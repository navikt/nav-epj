@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package no.nav.helse.epj.pasient

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.utils.WithValkey
import org.junit.Before
import org.junit.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private const val HPR = "111"

class ActivePatientRoutesTest : WithValkey() {

    private val pasientService = mockk<PasientService>()
    private val mapper = jacksonObjectMapper()

    @Before
    fun clear() {
        runBlocking { glideClient.del(arrayOf("smart:active-patient:$HPR")).get() }
    }

    private fun testApp(block: suspend HttpClient.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            authentication {
                provider("wonderwall-helseid") {
                    authenticate { ctx ->
                        ctx.principal(
                            HelseIdPrincipal(User(name = "Test", hpr = HPR), DebugInfo("", ""))
                        )
                    }
                }
            }
            routing {
                authenticate("wonderwall-helseid") {
                    activePatientRoutes(
                        ActivePatientService(pasientService, valkeyService),
                        valkeyService,
                    )
                }
            }
        }
        client.block()
    }

    private fun pasient(hprs: List<String> = listOf(HPR)) =
        Pasient(
            id = PasientId(Uuid.generateV4()),
            legekontorId = LegekontorId(Legekontor.DEFAULT.id.value),
            hprNumbers = hprs.map { HelsepersonellHpr(it) },
            fornavn = "Ola",
            etternavn = "Nordmann",
            personident = "01019012345",
        )

    private suspend fun HttpClient.put(patientId: String) =
        put("/api/active-patient") {
            contentType(ContentType.Application.Json)
            setBody("""{"patientId":"$patientId"}""")
        }

    private fun body(text: String): Map<String, String> = mapper.readValue(text)

    @Test
    fun `GET without an active patient returns 204`() = testApp {
        val response = get("/api/active-patient")

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `GET returns the active patient with the remaining time to live`() = testApp {
        val id = Uuid.generateV4().toString()
        valkeyService.setActivePatient(HPR, id)
        val before = Instant.now()

        val response = get("/api/active-patient")

        assertEquals(HttpStatusCode.OK, response.status)
        val json = body(response.bodyAsText())
        assertEquals(id, json["patientId"])
        val expiresAt = Instant.parse(json.getValue("expiresAt"))
        assertTrue(expiresAt.isAfter(before.plusSeconds(8 * 60 * 60 - 60)), "$expiresAt")
        assertTrue(expiresAt.isBefore(Instant.now().plusSeconds(8 * 60 * 60 + 1)), "$expiresAt")
    }

    @Test
    fun `GET reflects a shorter remaining time to live`() = testApp {
        val id = Uuid.generateV4().toString()
        valkeyService.setActivePatient(HPR, id)
        glideClient.expire("smart:active-patient:$HPR", 120).get()

        val expiresAt =
            Instant.parse(body(get("/api/active-patient").bodyAsText()).getValue("expiresAt"))

        assertTrue(expiresAt.isBefore(Instant.now().plusSeconds(121)), "$expiresAt")
        assertTrue(expiresAt.isAfter(Instant.now().plusSeconds(60)), "$expiresAt")
    }

    @Test
    fun `PUT sets the active patient and returns it`() = testApp {
        val pasient = pasient()
        coEvery { pasientService.getPasientById(pasient.id) } returns pasient

        val response = put(pasient.id.value.toString())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(pasient.id.value.toString(), body(response.bodyAsText())["patientId"])
        assertEquals(pasient.id.value.toString(), valkeyService.getActivePatient(HPR))
        assertEquals(
            pasient.id.value.toString(),
            body(get("/api/active-patient").bodyAsText())["patientId"],
        )
    }

    @Test
    fun `PUT replaces the previous active patient`() = testApp {
        val first = pasient()
        val second = pasient()
        coEvery { pasientService.getPasientById(first.id) } returns first
        coEvery { pasientService.getPasientById(second.id) } returns second

        put(first.id.value.toString())
        put(second.id.value.toString())

        assertEquals(second.id.value.toString(), valkeyService.getActivePatient(HPR))
    }

    @Test
    fun `PUT for an unknown patient returns 404 and keeps the active patient`() = testApp {
        val known = pasient()
        coEvery { pasientService.getPasientById(known.id) } returns known
        put(known.id.value.toString())
        val unknown = PasientId(Uuid.generateV4())
        coEvery { pasientService.getPasientById(unknown) } returns null

        val response = put(unknown.value.toString())

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(known.id.value.toString(), valkeyService.getActivePatient(HPR))
    }

    @Test
    fun `PUT for a patient that is not linked to the clinician returns 404`() = testApp {
        val other = pasient(hprs = listOf("999"))
        coEvery { pasientService.getPasientById(other.id) } returns other

        val response = put(other.id.value.toString())

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertNull(valkeyService.getActivePatient(HPR))
    }

    @Test
    fun `PUT with a malformed patient id returns 404`() = testApp {
        val response = put("not-a-uuid")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertNull(valkeyService.getActivePatient(HPR))
    }

    @Test
    fun `PUT with a malformed body returns 400`() = testApp {
        val response =
            put("/api/active-patient") {
                contentType(ContentType.Application.Json)
                setBody("{not json")
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(valkeyService.getActivePatient(HPR))
    }
}
