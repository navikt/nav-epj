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
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.DuplikatPasientException
import no.nav.helse.core.utils.UgyldigPersonidentException
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureStatusPages
import org.junit.Test

class PasientRoutesTest {

    private val pasientService = mockk<PasientService>()

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
            routing { authenticate("wonderwall-helseid") { pasientRoutes(pasientService) } }
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
                    legekontorId = Legekontor.DEFAULT.id,
                    hprNumbers = emptyList(),
                    fornavn = "Kari",
                    etternavn = "Nordmann",
                    personident = personident,
                    personidentType = PersonidentType.FNR,
                    birthDate = birthDate,
                    gender = AdministrativeGender.FEMALE,
                )
            coEvery { pasientService.createPasient(any(), "111") } returns opprettetPasient

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
        coEvery { pasientService.createPasient(any(), any()) } throws
            UgyldigPersonidentException("Personident har ugyldig kontrollsiffer")

        val response =
            post("/api/patient") {
                contentType(ContentType.Application.Json)
                setBody(opprettPasientJson("00000000000", "1985-06-15"))
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST patient with a duplicate personident returns 409`() = testApp {
        coEvery { pasientService.createPasient(any(), any()) } throws DuplikatPasientException()

        val response =
            post("/api/patient") {
                contentType(ContentType.Application.Json)
                setBody(opprettPasientJson("15068500017", "1985-06-15"))
            }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }
}
