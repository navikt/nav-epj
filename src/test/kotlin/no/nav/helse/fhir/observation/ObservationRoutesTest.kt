@file:OptIn(ExperimentalUuidApi::class)

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
    private val fhirServerUrl = "https://fhir.example.test/fhir"

    private fun observationScope(
        interactions: Set<Interaction>,
        context: ScopeContext = ScopeContext.PATIENT,
    ) =
        SmartScope.Fhir(
            context = context,
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
                    observationRoutes(observationService, fhirJson, fhirContentType, fhirServerUrl)
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

    @Test
    fun `GET Observation search accepts a system-level scope without launch context`() {
        val patientId = Uuid.generateV4()
        coEvery {
            observationService.searchObservations(PatientInputId(patientId), null, null)
        } returns Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(
            scopes = setOf(observationScope(setOf(Interaction.SEARCH), ScopeContext.SYSTEM)),
            boundPatient = null,
        ) {
            val response = get("/fhir/Observation?patient=Patient/$patientId")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(true, response.bodyAsText().contains("\"searchset\""))
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

    private fun createScope() = setOf(observationScope(setOf(Interaction.CREATE)))

    private fun validCreateBody(
        patientId: Uuid = Uuid.generateV4(),
        encounterId: Uuid = Uuid.generateV4(),
        clientSuppliedId: String? = null,
    ): String {
        val observation =
            Observation(
                id = clientSuppliedId,
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
                encounter = Reference(reference = FhirString(value = "Encounter/$encounterId")),
                effective =
                    Observation.Effective.DateTime(
                        com.google.fhir.model.r4.DateTime(
                            value =
                                com.google.fhir.model.r4.FhirDateTime.DateTime(
                                    kotlinx.datetime.LocalDateTime(2025, 1, 15, 10, 30),
                                    kotlinx.datetime.UtcOffset.ZERO,
                                )
                        )
                    ),
                value =
                    Observation.Value.Quantity(
                        com.google.fhir.model.r4.Quantity(
                            value =
                                com.google.fhir.model.r4.Decimal(
                                    value =
                                        com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString(
                                            "37.2"
                                        )
                                ),
                            unit = FhirString(value = "degree Celsius"),
                            system = Uri(value = "http://unitsofmeasure.org"),
                            code = Code(value = "Cel"),
                        )
                    ),
            )
        return fhirJson.encodeToString(observation)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST Observation creates the resource with a server-generated id and returns 201 with Location`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        val generatedId = ObservationId(Uuid.generateV4())
        val persisted = sampleObservation(generatedId.value.toString(), patientId.toString())
        coEvery { observationService.createObservation(any()) } returns persisted

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/Observation") {
                    contentType(fhirContentType)
                    setBody(
                        validCreateBody(
                            patientId = patientId,
                            encounterId = encounterId,
                            clientSuppliedId = "client-chosen-id",
                        )
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(
                "$fhirServerUrl/Observation/${generatedId.value}",
                response.headers[HttpHeaders.Location],
            )
            val body = response.bodyAsText()
            assertEquals(true, body.contains(generatedId.value.toString()))
            assertEquals(false, body.contains("client-chosen-id"))
        }
        coVerify(exactly = 1) { observationService.createObservation(any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST Observation rejects a token missing the Observation create scope`() {
        val patientId = Uuid.generateV4()

        testApp(scopes = emptySet(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/Observation") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
        coVerify(exactly = 0) { observationService.createObservation(any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST Observation rejects a subject that does not match the launched patient and writes nothing`() {
        val patientId = Uuid.generateV4()
        val otherPatientId = Uuid.generateV4()

        testApp(scopes = createScope(), boundPatient = otherPatientId.toString()) {
            val response =
                post("/fhir/Observation") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
        coVerify(exactly = 0) { observationService.createObservation(any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST Observation rejects an encounter belonging to another patient and writes nothing`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        coEvery { observationService.createObservation(any()) } throws
            no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException(
                no.nav.helse.epj.konsultasjon.KonsultasjonId(encounterId),
                no.nav.helse.epj.pasient.PasientId(patientId),
            )

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/Observation") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId, encounterId = encounterId))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertEquals(true, body.contains("\"OperationOutcome\""))
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST Observation rejects an unknown encounter and writes nothing`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        coEvery { observationService.createObservation(any()) } throws
            no.nav.helse.core.utils.KonsultasjonNotFoundException(
                no.nav.helse.epj.konsultasjon.KonsultasjonId(encounterId)
            )

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/Observation") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId, encounterId = encounterId))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST Observation surfaces a duplicate id collision explicitly instead of a fallback success`() {
        val patientId = Uuid.generateV4()
        coEvery { observationService.createObservation(any()) } throws
            no.nav.helse.core.utils.DuplikatMaalingException()

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/Observation") {
                    contentType(fhirContentType)
                    setBody(validCreateBody(patientId = patientId))
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST Observation rejects invalid content with an OperationOutcome and writes nothing`() {
        val patientId = Uuid.generateV4()
        val invalidObservation =
            Observation(
                status = Enumeration(value = Observation.ObservationStatus.Final),
                code = CodeableConcept(coding = emptyList()),
                subject = Reference(reference = FhirString(value = "Patient/$patientId")),
            )

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/Observation") {
                    contentType(fhirContentType)
                    setBody(fhirJson.encodeToString(invalidObservation))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertEquals(true, body.contains("\"OperationOutcome\""))
            assertEquals(true, body.contains("\"issue\""))
        }
        coVerify(exactly = 0) { observationService.createObservation(any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST Observation rejects malformed JSON with a structural OperationOutcome`() {
        val patientId = Uuid.generateV4()

        testApp(scopes = createScope(), boundPatient = patientId.toString()) {
            val response =
                post("/fhir/Observation") {
                    contentType(fhirContentType)
                    setBody("{ this is not valid json")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertEquals(true, body.contains("\"OperationOutcome\""))
        }
        coVerify(exactly = 0) { observationService.createObservation(any()) }
    }
}
