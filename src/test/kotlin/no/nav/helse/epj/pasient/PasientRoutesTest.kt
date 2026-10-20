package no.nav.helse.epj.pasient

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
import no.nav.helse.core.utils.DuplikatPasientException
import no.nav.helse.core.utils.IngenFastlegelisteException
import no.nav.helse.core.utils.UgyldigPersonidentException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.sessionorg.SessionOrgResolution
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureStatusPages
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class PasientRoutesTest {

    private val pasientService = mockk<PasientService>()
    private val sessionOrgResolver =
        mockk<SessionOrgResolver> {
            coEvery { resolve(any()) } returns
                SessionOrgResolution.Resolved(
                    Legekontor(LegekontorId(Uuid.generateV4()), "Fjordvik", "311111116", null),
                    derived = true,
                )
        }

    private fun testApp(block: suspend io.ktor.client.HttpClient.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            authentication {
                provider("wonderwall-helseid") {
                    authenticate { ctx ->
                        ctx.principal(
                            HelseIdPrincipal(User(name = "Test", hpr = "111"), DebugInfo("", ""))
                        )
                    }
                }
            }
            routing {
                authenticate("wonderwall-helseid") {
                    pasientRoutes(pasientService, sessionOrgResolver)
                }
            }
        }
        client.block()
    }

    private fun opprettPasientJson(personident: String, birthDate: String) =
        """
        {
          "fornavn": "Kari",
          "etternavn": "Nordmann",
          "personident": "$personident",
          "personidentType": "FNR",
          "birthDate": "$birthDate",
          "gender": "FEMALE"
        }
        """
            .trimIndent()

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `POST patient accepts ISO birthDate and serializes it back as the same ISO string`() =
        testApp {
            val birthDate = LocalDate.of(1985, 6, 15)
            val personident = "15068500017"
            val opprettetPasient =
                Pasient(
                    id = PasientId(Uuid.generateV4()),
                    hprNumbers = emptyList(),
                    fornavn = "Kari",
                    etternavn = "Nordmann",
                    personident = personident,
                    personidentType = PersonidentType.FNR,
                    birthDate = birthDate,
                    gender = AdministrativeGender.FEMALE,
                )
            coEvery { pasientService.createPasient(any(), "111", "311111116") } returns
                opprettetPasient

            val response =
                post("/api/patient") {
                    contentType(ContentType.Application.Json)
                    setBody(opprettPasientJson(personident, "1985-06-15"))
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = response.bodyAsText()
            assertTrue(Regex("\"birthDate\"\\s*:\\s*\"1985-06-15\"").containsMatchIn(body))
        }

    @Test
    fun `POST patient with invalid personident returns 400`() = testApp {
        val personident = "00000000000"
        coEvery { pasientService.createPasient(any(), any(), any()) } throws
            UgyldigPersonidentException("Personident $personident har ugyldig kontrollsiffer")

        val response =
            post("/api/patient") {
                contentType(ContentType.Application.Json)
                setBody(opprettPasientJson(personident, "1985-06-15"))
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertEquals("Ugyldig personident", body)
        assertFalse(body.contains(personident))
        assertFalse(body.contains("kontrollsiffer"))
    }

    @Test
    fun `POST patient with a duplicate personident returns 409`() = testApp {
        val personident = "15068500017"
        coEvery { pasientService.createPasient(any(), any(), any()) } throws
            DuplikatPasientException()

        val response =
            post("/api/patient") {
                contentType(ContentType.Application.Json)
                setBody(opprettPasientJson(personident, "1985-06-15"))
            }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.bodyAsText()
        assertEquals("Pasienten finnes allerede", body)
        assertFalse(body.contains(personident))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET patient lists the patients at the session org`() = testApp {
        val pasient =
            Pasient(
                id = PasientId(Uuid.generateV4()),
                hprNumbers = emptyList(),
                fornavn = "Kari",
                etternavn = "Nordmann",
                personident = "15068500017",
            )
        coEvery { pasientService.listAtLegekontor("311111116", HelsepersonellHpr("111")) } returns
            listOf(pasient)

        val response = get("/api/patient")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Nordmann"))
    }

    @Test
    fun `GET patient without a session org returns 409`() = testApp {
        coEvery { sessionOrgResolver.resolve("111") } returns SessionOrgResolution.NeedsChoice

        assertEquals(HttpStatusCode.Conflict, get("/api/patient").status)
    }

    @Test
    fun `POST patient without an own list at the org returns 403`() = testApp {
        coEvery { pasientService.createPasient(any(), any(), any()) } throws
            IngenFastlegelisteException()

        val response =
            post("/api/patient") {
                contentType(ContentType.Application.Json)
                setBody(opprettPasientJson("15068500017", "1985-06-15"))
            }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `GET patient by id returns a patient listed at the session org`() = testApp {
        val id = PasientId(Uuid.generateV4())
        coEvery { pasientService.getPasientById(id) } returns
            Pasient(id, emptyList(), "Kari", "Nordmann", "15068500017")
        coEvery { pasientService.isListedAt(id, "311111116") } returns true

        assertEquals(HttpStatusCode.OK, get("/api/patient/${id.value}").status)
    }

    @Test
    fun `GET patient by id returns 404 for a patient not listed at the session org`() = testApp {
        val id = PasientId(Uuid.generateV4())
        coEvery { pasientService.getPasientById(id) } returns
            Pasient(id, emptyList(), "Kari", "Nordmann", "15068500017")
        coEvery { pasientService.isListedAt(id, "311111116") } returns false

        assertEquals(HttpStatusCode.NotFound, get("/api/patient/${id.value}").status)
    }
}
