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
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.konsultasjon.Konsultasjon
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import org.junit.Test

class ConditionRoutesTest {

    private val konsultasjonService = mockk<KonsultasjonService>()
    private val conditionService = ConditionService(konsultasjonService)
    private val fhirJson = FhirR4Json()
    private val fhirContentType = ContentType("application", "fhir+json")

    private val pasientId = PasientId(Uuid.generateV4())
    private val encounterA = KonsultasjonId(Uuid.generateV4())
    private val encounterB = KonsultasjonId(Uuid.generateV4())

    private fun konsultasjon(id: KonsultasjonId, pasientId: PasientId, code: String) =
        Konsultasjon(
            id = id,
            pasientId = pasientId,
            legekontorId = LegekontorId(Uuid.generateV4()),
            hpr = emptyList(),
            journalnotat = emptyList(),
            diagnoser =
                listOf(Diagnose(system = DiagnoseType.ICPC2, code = code, text = "Diagnose $code")),
            startetTidspunkt = LocalDateTime.now().minusHours(1),
            avsluttetTidspunkt = null,
            status = KonsultasjonStatus.PAAGAAENDE,
            problemstilling = null,
        )

    private fun givenPatientHasTwoEncounters() {
        coEvery { konsultasjonService.getKonsultasjoner(pasientId) } returns
            listOf(
                konsultasjon(encounterA, pasientId, "A01"),
                konsultasjon(encounterB, pasientId, "B02"),
            )
    }

    private fun testApp(
        boundPatient: String? = pasientId.value.toString(),
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
                    "/fhir/Condition?subject=Patient/${pasientId.value}&encounter=Encounter/${encounterA.value}"
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val conditions = response.conditions(response.bodyAsText())
            assertEquals(1, conditions.size)
            assertEquals(
                "Encounter/${encounterA.value}",
                conditions.single().encounter?.reference?.value,
            )
            assertEquals("A01", conditions.single().code?.coding?.single()?.code?.value)
        }
    }

    @Test
    fun `GET Condition with subject and an encounter of another patient returns an empty bundle`() {
        val foreignEncounter = KonsultasjonId(Uuid.generateV4())
        givenPatientHasTwoEncounters()

        testApp {
            val response =
                get(
                    "/fhir/Condition?subject=Patient/${pasientId.value}&encounter=Encounter/${foreignEncounter.value}"
                )

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.conditions(response.bodyAsText()).isEmpty())
        }
    }

    @Test
    fun `GET Condition with subject and an unknown encounter returns an empty bundle`() {
        val unknownEncounter = KonsultasjonId(Uuid.generateV4())
        givenPatientHasTwoEncounters()

        testApp {
            val response =
                get(
                    "/fhir/Condition?subject=Patient/${pasientId.value}&encounter=Encounter/${unknownEncounter.value}"
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
                    "/fhir/Condition?subject=Patient/${pasientId.value}&encounter=Encounter/${encounterA.value}"
                )

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `GET Condition with subject only returns the conditions of all the patient's encounters`() {
        givenPatientHasTwoEncounters()

        testApp {
            val response = get("/fhir/Condition?subject=Patient/${pasientId.value}")

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
            val response = get("/fhir/Condition?subject=Patient/${pasientId.value}")

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
        coEvery { konsultasjonService.getKonsultasjon(encounterA) } returns
            konsultasjon(encounterA, pasientId, "A01")

        testApp {
            val response = get("/fhir/Condition?encounter=Encounter/${encounterA.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, response.conditions(response.bodyAsText()).size)
        }
    }

    @Test
    fun `GET Condition with encounter only rejects an encounter of another patient`() {
        coEvery { konsultasjonService.getKonsultasjon(encounterA) } returns
            konsultasjon(encounterA, PasientId(Uuid.generateV4()), "A01")

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
