package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.FhirR4Json
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

    private fun testApp(patientId: String, block: suspend io.ktor.client.HttpClient.() -> Unit) =
        testApplication {
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
                                                context = ScopeContext.PATIENT,
                                                resourceType = "Patient",
                                                interactions = setOf(Interaction.READ),
                                            )
                                        ),
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
}
