package no.nav.helse.fhir.observation

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Observation
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import org.junit.Test

class ObservationRoutesTest {

    private val observationService = mockk<ObservationService>()
    private val fhirJson = FhirR4Json()
    private val fhirContentType = ContentType("application", "fhir+json")

    private fun observationScope(interactions: Set<Interaction>) =
        SmartScope.Fhir(
            context = ScopeContext.PATIENT,
            resourceType = "Observation",
            interactions = interactions,
        )

    private fun sampleObservation(id: String, patientId: String): Observation =
        Observation(
            id = id,
            status = Enumeration(value = Observation.ObservationStatus.Final),
            code =
                CodeableConcept(
                    coding =
                        listOf(
                            Coding(
                                system = Uri(value = "http://loinc.org"),
                                code = Code(value = "8310-5"),
                                display = FhirString(value = "Body temperature"),
                            )
                        )
                ),
            subject = Reference(reference = FhirString(value = "Patient/$patientId")),
        )

    private fun testApp(
        scopes: Set<SmartScope> =
            setOf(observationScope(setOf(Interaction.READ, Interaction.SEARCH))),
        boundPatient: String? = null,
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
                                scopes = scopes,
                                patient = boundPatient,
                                encounter = null,
                            )
                        )
                    }
                }
            }
            routing {
                authenticate("smart-access-token") {
                    observationRoutes(observationService, fhirJson, fhirContentType)
                }
            }
        }
        client.block()
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation by id returns the mapped resource for the launched patient`() {
        val patientId = Uuid.generateV4().toString()
        val observationId = ObservationId(Uuid.generateV4())
        coEvery { observationService.getObservationById(observationId) } returns
            sampleObservation(observationId.value.toString(), patientId)

        testApp(boundPatient = patientId) {
            val response = get("/fhir/Observation/${observationId.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "application/fhir+json",
                response.contentType()?.withoutParameters().toString(),
            )
            assertEquals(true, response.bodyAsText().contains("\"8310-5\""))
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation by id returns 404 for an unknown observation`() {
        val observationId = ObservationId(Uuid.generateV4())
        coEvery { observationService.getObservationById(observationId) } returns null

        testApp(boundPatient = Uuid.generateV4().toString()) {
            val response = get("/fhir/Observation/${observationId.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation by id rejects a token missing the Observation scope`() {
        val patientId = Uuid.generateV4().toString()
        val observationId = ObservationId(Uuid.generateV4())
        coEvery { observationService.getObservationById(observationId) } returns
            sampleObservation(observationId.value.toString(), patientId)

        testApp(scopes = emptySet(), boundPatient = patientId) {
            val response = get("/fhir/Observation/${observationId.value}")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation by id returns 404 for a token bound to a different patient`() {
        val observationId = ObservationId(Uuid.generateV4())
        coEvery { observationService.getObservationById(observationId) } returns
            sampleObservation(observationId.value.toString(), Uuid.generateV4().toString())

        testApp(boundPatient = Uuid.generateV4().toString()) {
            val response = get("/fhir/Observation/${observationId.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search requires a subject or patient parameter`() {
        testApp(boundPatient = Uuid.generateV4().toString()) {
            val response = get("/fhir/Observation")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search rejects conflicting subject and patient parameters`() {
        val patientId = Uuid.generateV4().toString()
        val otherPatientId = Uuid.generateV4().toString()

        testApp(boundPatient = patientId) {
            val response =
                get("/fhir/Observation?subject=Patient/$patientId&patient=Patient/$otherPatientId")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search accepts the patient alias parameter`() {
        val patientId = Uuid.generateV4()
        coEvery {
            observationService.searchObservations(PatientInputId(patientId), null, null)
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(boundPatient = patientId.toString()) {
            val response = get("/fhir/Observation?patient=Patient/$patientId")

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search passes the encounter filter through`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        coEvery {
            observationService.searchObservations(
                PatientInputId(patientId),
                EncounterId(encounterId),
                null,
            )
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(boundPatient = patientId.toString()) {
            val response =
                get("/fhir/Observation?subject=Patient/$patientId&encounter=Encounter/$encounterId")

            assertEquals(HttpStatusCode.OK, response.status)
        }
        coVerify(exactly = 1) {
            observationService.searchObservations(
                PatientInputId(patientId),
                EncounterId(encounterId),
                null,
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search passes a bare LOINC code filter through`() {
        val patientId = Uuid.generateV4()
        coEvery {
            observationService.searchObservations(PatientInputId(patientId), null, "8310-5")
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(boundPatient = patientId.toString()) {
            val response = get("/fhir/Observation?subject=Patient/$patientId&code=8310-5")

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search accepts a system-qualified LOINC code`() {
        val patientId = Uuid.generateV4()
        coEvery {
            observationService.searchObservations(PatientInputId(patientId), null, "8310-5")
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(boundPatient = patientId.toString()) {
            val response =
                get("/fhir/Observation?subject=Patient/$patientId&code=http://loinc.org|8310-5")

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search rejects an unsupported code system`() {
        val patientId = Uuid.generateV4()

        testApp(boundPatient = patientId.toString()) {
            val response =
                get("/fhir/Observation?subject=Patient/$patientId&code=http://snomed.info/sct|1234")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search returns an empty searchset bundle`() {
        val patientId = Uuid.generateV4()
        coEvery {
            observationService.searchObservations(PatientInputId(patientId), null, null)
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(boundPatient = patientId.toString()) {
            val response = get("/fhir/Observation?subject=Patient/$patientId")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertEquals(true, body.contains("\"searchset\""))
            assertEquals(false, body.contains("\"entry\""))
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search rejects a token missing the Observation scope`() {
        val patientId = Uuid.generateV4()

        testApp(scopes = emptySet(), boundPatient = patientId.toString()) {
            val response = get("/fhir/Observation?subject=Patient/$patientId")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Observation search rejects a token bound to a different patient`() {
        val patientId = Uuid.generateV4()
        val otherPatientId = Uuid.generateV4().toString()

        testApp(boundPatient = otherPatientId) {
            val response = get("/fhir/Observation?subject=Patient/$patientId")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }
}
