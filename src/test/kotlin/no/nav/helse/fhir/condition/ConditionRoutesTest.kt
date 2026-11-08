@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.FhirR4Json
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.clinic.ClinicId
import no.nav.helse.ehr.consultation.Consultation
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import org.junit.Test

class ConditionRoutesTest {

    private val consultationService = mockk<ConsultationService>()
    private val conditionService = ConditionService(consultationService)
    private val fhirJson = FhirR4Json()
    private val fhirContentType = ContentType("application", "fhir+json")

    private val patientId = PatientId(Uuid.generateV4())
    private val encounterA = ConsultationId(Uuid.generateV4())
    private val encounterB = ConsultationId(Uuid.generateV4())

    private fun consultation(id: ConsultationId, patientId: PatientId, code: String) =
        Consultation(
            id = id,
            patientId = patientId,
            clinicId = ClinicId(Uuid.generateV4()),
            hpr = emptyList(),
            clinicalNotes = emptyList(),
            diagnoses =
                listOf(Diagnose(system = DiagnoseType.ICPC2, code = code, text = "Diagnose $code")),
            startedAt = LocalDateTime.now().minusHours(1),
            endedAt = null,
            status = ConsultationStatus.IN_PROGRESS,
            reasonForVisit = null,
        )

    private fun givenPatientHasTwoEncounters() {
        coEvery { consultationService.getConsultations(patientId, DEFAULT_ORG_NUMBER) } returns
            listOf(
                consultation(encounterA, patientId, "A01"),
                consultation(encounterB, patientId, "B02"),
            )
    }

    private fun testApp(
        boundPatient: String? = patientId.value.toString(),
        scopeContext: ScopeContext = ScopeContext.PATIENT,
        block: suspend io.ktor.client.HttpClient.() -> Unit,
    ) = testApplication {
        application {
            configureStatusPages()
            authentication {
                provider("smart-access-token") {
                    authenticate { ctx ->
                        ctx.principal(
                            SmartPrincipal(
                                subject = "test-client",
                                scopes =
                                    setOf(
                                        SmartScope.Fhir(
                                            context = scopeContext,
                                            resourceType = "Condition",
                                            interactions = setOf(Interaction.SEARCH),
                                        )
                                    ),
                                patient = boundPatient,
                                encounter = null,
                                orgNumber = DEFAULT_ORG_NUMBER,
                            )
                        )
                    }
                }
            }
            routing {
                authenticate("smart-access-token") {
                    conditionRoutes(conditionService, fhirJson, fhirContentType)
                }
            }
        }
        client.block()
    }

    private fun HttpResponse.conditions(body: String): List<Condition> =
        (fhirJson.decodeFromString(body) as Bundle).entry.map { it.resource as Condition }

    @Test
    fun `GET Condition with subject and encounter returns only that encounter's conditions`() {
        givenPatientHasTwoEncounters()

        testApp {
            val response =
                get(
                    "/fhir/Condition?subject=Patient/${patientId.value}&encounter=Encounter/${encounterA.value}"
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val conditions = response.conditions(response.bodyAsText())
            assertEquals(1, conditions.size)
            assertEquals(
                "Encounter/${encounterA.value}",
                conditions.single().encounter?.reference?.value,
            )
            assertEquals("A01", conditions.single().code?.coding?.single()?.code?.value)
            assertEquals(
                "encounter-diagnosis",
                conditions.single().category.single().coding.single().code?.value,
            )
        }
    }

    @Test
    fun `GET Condition with subject and an encounter of another patient returns an empty bundle`() {
        val foreignEncounter = ConsultationId(Uuid.generateV4())
        givenPatientHasTwoEncounters()

        testApp {
            val response =
                get(
                    "/fhir/Condition?subject=Patient/${patientId.value}&encounter=Encounter/${foreignEncounter.value}"
                )

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.conditions(response.bodyAsText()).isEmpty())
        }
    }

    @Test
    fun `GET Condition with subject and an unknown encounter returns an empty bundle`() {
        val unknownEncounter = ConsultationId(Uuid.generateV4())
        givenPatientHasTwoEncounters()

        testApp {
            val response =
                get(
                    "/fhir/Condition?subject=Patient/${patientId.value}&encounter=Encounter/${unknownEncounter.value}"
                )

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.conditions(response.bodyAsText()).isEmpty())
        }
    }

    @Test
    fun `GET Condition with subject and encounter rejects a token bound to a different patient`() {
        givenPatientHasTwoEncounters()

        testApp(boundPatient = Uuid.generateV4().toString()) {
            val response =
                get(
                    "/fhir/Condition?subject=Patient/${patientId.value}&encounter=Encounter/${encounterA.value}"
                )

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `GET Condition with subject only returns the conditions of all the patient's encounters`() {
        givenPatientHasTwoEncounters()

        testApp {
            val response = get("/fhir/Condition?subject=Patient/${patientId.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            val codes =
                response.conditions(response.bodyAsText()).map {
                    it.code?.coding?.single()?.code?.value
                }
            assertEquals(listOf("A01", "B02"), codes)
        }
    }

    @Test
    fun `GET Condition with subject returns the patient's conditions for a system-level scope without launch context`() {
        givenPatientHasTwoEncounters()

        testApp(boundPatient = null, scopeContext = ScopeContext.SYSTEM) {
            val response = get("/fhir/Condition?subject=Patient/${patientId.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            val codes =
                response.conditions(response.bodyAsText()).map {
                    it.code?.coding?.single()?.code?.value
                }
            assertEquals(listOf("A01", "B02"), codes)
        }
    }

    @Test
    fun `GET Condition with encounter only returns that encounter's conditions`() {
        coEvery { consultationService.getConsultation(encounterA, DEFAULT_ORG_NUMBER) } returns
            consultation(encounterA, patientId, "A01")

        testApp {
            val response = get("/fhir/Condition?encounter=Encounter/${encounterA.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, response.conditions(response.bodyAsText()).size)
        }
    }

    @Test
    fun `GET Condition with encounter only rejects an encounter of another patient`() {
        coEvery { consultationService.getConsultation(encounterA, DEFAULT_ORG_NUMBER) } returns
            consultation(encounterA, PatientId(Uuid.generateV4()), "A01")

        testApp {
            val response = get("/fhir/Condition?encounter=Encounter/${encounterA.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `GET Condition without subject or encounter returns 400`() {
        testApp {
            val response = get("/fhir/Condition")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }
}
