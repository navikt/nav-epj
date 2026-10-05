package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Patient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.pasient.AdministrativeGender
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientService
import no.nav.helse.epj.pasient.PersonidentType
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import org.junit.Test

class PatientRoutesTest {

    private val epjPatientService = mockk<PasientService>()
    private val patientService = PatientService(epjPatientService)
    private val fhirJson = FhirR4Json()
    private val fhirContentType = ContentType("application", "fhir+json")

    @OptIn(ExperimentalUuidApi::class)
    private fun pasient(
        id: PasientId = PasientId(Uuid.generateV4()),
        personidentType: PersonidentType? = PersonidentType.FNR,
        birthDate: LocalDate? = LocalDate.of(1985, 6, 15),
        gender: AdministrativeGender? = AdministrativeGender.FEMALE,
    ) =
        Pasient(
            id = id,
            legekontorId = Legekontor.DEFAULT.id,
            hprNumbers = listOf(HelsepersonellHpr("111")),
            fornavn = "Kari",
            etternavn = "Nordmann",
            personident = "15068500017",
            personidentType = personidentType,
            birthDate = birthDate,
            gender = gender,
        )

    private fun scope(context: ScopeContext, vararg interactions: Interaction) =
        SmartScope.Fhir(
            context = context,
            resourceType = "Patient",
            interactions = interactions.toSet(),
        )

    private fun testApp(
        patientId: String?,
        scopes: Set<SmartScope> = setOf(scope(ScopeContext.PATIENT, Interaction.READ)),
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
                                patient = patientId,
                                encounter = null,
                            )
                        )
                    }
                }
            }
            routing {
                authenticate("smart-access-token") {
                    patientRoutes(patientService, fhirJson, fhirContentType)
                }
            }
        }
        client.block()
    }

    @Test
    fun `GET Patient returns the fnr identifier system and demographics`() {
        val subject = pasient(personidentType = PersonidentType.FNR)
        coEvery { epjPatientService.getPasientById(subject.id) } returns subject

        testApp(patientId = subject.id.value.toString()) {
            val response = get("/fhir/Patient/${subject.id.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "application/fhir+json",
                response.contentType()?.withoutParameters().toString(),
            )
            val body = response.bodyAsText()
            assertTrue(body.contains("\"urn:oid:2.16.578.1.12.4.1.4.1\""))
            assertTrue(body.contains("\"gender\": \"female\""))
            assertTrue(body.contains("\"birthDate\": \"1985-06-15\""))
        }
    }

    @Test
    fun `GET Patient returns the dnr identifier system`() {
        val subject = pasient(personidentType = PersonidentType.DNR)
        coEvery { epjPatientService.getPasientById(subject.id) } returns subject

        testApp(patientId = subject.id.value.toString()) {
            val response = get("/fhir/Patient/${subject.id.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("\"urn:oid:2.16.578.1.12.4.1.4.2\""))
        }
    }

    @Test
    fun `GET Patient omits gender and birthDate for a legacy patient without demographics`() {
        val subject = pasient(personidentType = null, birthDate = null, gender = null)
        coEvery { epjPatientService.getPasientById(subject.id) } returns subject

        testApp(patientId = subject.id.value.toString()) {
            val response = get("/fhir/Patient/${subject.id.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"urn:oid:2.16.578.1.12.4.1.4.1\""))
            assertFalse(body.contains("\"gender\""))
            assertFalse(body.contains("\"birthDate\""))
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Patient returns 404 for an unknown patient`() {
        val id = PasientId(Uuid.generateV4())
        coEvery { epjPatientService.getPasientById(id) } returns null

        testApp(patientId = id.value.toString()) {
            val response = get("/fhir/Patient/${id.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    private fun searchScopes() =
        setOf(scope(ScopeContext.PATIENT, Interaction.READ, Interaction.SEARCH))

    private suspend fun HttpResponse.entries(): List<Patient> =
        (fhirJson.decodeFromString(bodyAsText()) as Bundle).entry.map { it.resource as Patient }

    @Test
    fun `GET Patient by identifier returns the launched patient in a searchset`() {
        val subject = pasient(personidentType = PersonidentType.FNR)
        coEvery { epjPatientService.getPasientByPersonident(subject.personident) } returns subject

        testApp(patientId = subject.id.value.toString(), scopes = searchScopes()) {
            val response =
                get("/fhir/Patient") {
                    parameter("identifier", "urn:oid:2.16.578.1.12.4.1.4.1|${subject.personident}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val bundle = fhirJson.decodeFromString(response.bodyAsText()) as Bundle
            assertEquals(Bundle.BundleType.Searchset, bundle.type?.value)
            assertEquals(listOf(subject.id.value.toString()), bundle.entry.map { it.resource?.id })
        }
    }

    @Test
    fun `GET Patient by identifier value without system matches`() {
        val subject = pasient(personidentType = PersonidentType.DNR)
        coEvery { epjPatientService.getPasientByPersonident(subject.personident) } returns subject

        testApp(patientId = subject.id.value.toString(), scopes = searchScopes()) {
            val response = get("/fhir/Patient") { parameter("identifier", subject.personident) }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf(subject.id.value.toString()), response.entries().map { it.id })
        }
    }

    @Test
    fun `GET Patient by identifier with the other national system returns no match`() {
        val subject = pasient(personidentType = PersonidentType.FNR)
        coEvery { epjPatientService.getPasientByPersonident(subject.personident) } returns subject

        testApp(patientId = subject.id.value.toString(), scopes = searchScopes()) {
            val response =
                get("/fhir/Patient") {
                    parameter("identifier", "urn:oid:2.16.578.1.12.4.1.4.2|${subject.personident}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.entries().isEmpty())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Patient by identifier hides another patient from a patient-bound token`() {
        val other = pasient()
        coEvery { epjPatientService.getPasientByPersonident(other.personident) } returns other

        testApp(patientId = Uuid.generateV4().toString(), scopes = searchScopes()) {
            val response = get("/fhir/Patient") { parameter("identifier", other.personident) }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.entries().isEmpty())
        }
    }

    @Test
    fun `GET Patient by identifier finds any patient with a user-level scope`() {
        val other = pasient()
        coEvery { epjPatientService.getPasientByPersonident(other.personident) } returns other

        testApp(
            patientId = null,
            scopes = setOf(scope(ScopeContext.USER, Interaction.READ, Interaction.SEARCH)),
        ) {
            val response = get("/fhir/Patient") { parameter("identifier", other.personident) }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf(other.id.value.toString()), response.entries().map { it.id })
        }
    }

    @Test
    fun `GET Patient by identifier finds a patient with a system-level scope and no launch context`() {
        val other = pasient()
        coEvery { epjPatientService.getPasientByPersonident(other.personident) } returns other

        testApp(patientId = null, scopes = setOf(scope(ScopeContext.SYSTEM, Interaction.SEARCH))) {
            val response = get("/fhir/Patient") { parameter("identifier", other.personident) }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf(other.id.value.toString()), response.entries().map { it.id })
        }
    }

    @Test
    fun `GET Patient by identifier returns an empty searchset for an unknown identifier`() {
        coEvery { epjPatientService.getPasientByPersonident("15068500017") } returns null

        testApp(patientId = null, scopes = searchScopes()) {
            val response = get("/fhir/Patient") { parameter("identifier", "15068500017") }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.entries().isEmpty())
        }
    }

    @Test
    fun `GET Patient by identifier rejects an unknown identifier system`() {
        testApp(patientId = null, scopes = searchScopes()) {
            val response =
                get("/fhir/Patient") { parameter("identifier", "urn:oid:1.2.3|15068500017") }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `GET Patient without identifier is rejected`() {
        testApp(patientId = null, scopes = searchScopes()) {
            val response = get("/fhir/Patient")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `GET Patient by identifier requires a Patient search scope`() {
        testApp(patientId = null) {
            val response = get("/fhir/Patient") { parameter("identifier", "15068500017") }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }
}
