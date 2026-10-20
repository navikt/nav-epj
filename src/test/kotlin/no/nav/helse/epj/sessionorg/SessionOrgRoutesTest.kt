@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package no.nav.helse.epj.sessionorg

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.legekontor.LegekontorRepository
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

class SessionOrgRoutesTest : WithValkey() {

    private val fastlegelisteRepository = mockk<FastlegelisteRepository>()
    private val legekontorRepository = mockk<LegekontorRepository>()
    private val mapper = jacksonObjectMapper()

    private val fjordvik = org("Fjordvik Legekontor", "311111116")
    private val lyngdal = org("Lyngdal Legesenter", "311111124")

    private fun org(navn: String, orgnummer: String) =
        Legekontor(LegekontorId(Uuid.generateV4()), navn, orgnummer, "22000000")

    @Before
    fun setUp() {
        runBlocking {
            glideClient.del(arrayOf("epj:session-org:$HPR", "smart:active-patient:$HPR")).get()
        }
        coEvery { legekontorRepository.listChoosable() } returns listOf(fjordvik, lyngdal)
        coEvery { legekontorRepository.findByLegekontorId(fjordvik.id.value) } returns fjordvik
        coEvery { fastlegelisteRepository.findLegekontorIdsByHpr(HPR) } returns emptyList()
    }

    private fun testApp(block: suspend HttpClient.() -> Unit) = testApplication {
        val resolver =
            SessionOrgResolver(
                fastlegelisteRepository,
                legekontorRepository,
                ValkeySessionOrgChoiceStore(valkeyService),
            )
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
                    sessionOrgRoutes(
                        resolver,
                        SessionOrgService(
                            resolver,
                            ValkeySessionOrgChoiceStore(valkeyService),
                            valkeyService,
                        ),
                    )
                }
            }
        }
        client.block()
    }

    private suspend fun HttpClient.choose(orgnummer: String) =
        put("/api/session-org") {
            contentType(ContentType.Application.Json)
            setBody("""{"orgnummer":"$orgnummer"}""")
        }

    private fun json(text: String): SessionOrgResponse = mapper.readValue(text)

    @Test
    fun `GET for an unknown hpr asks for a choice and lists all orgs`() = testApp {
        val body = json(get("/api/session-org").bodyAsText())

        assertEquals(true, body.needsChoice)
        assertEquals(true, body.canChoose)
        assertNull(body.org)
        assertEquals(listOf("311111116", "311111124"), body.choosableOrgs.map { it.org.orgnummer })
    }

    @Test
    fun `GET for a doctor with one list returns the org and no choice`() = testApp {
        coEvery { fastlegelisteRepository.findLegekontorIdsByHpr(HPR) } returns listOf(fjordvik.id)

        val body = json(get("/api/session-org").bodyAsText())

        assertEquals(false, body.needsChoice)
        assertEquals(false, body.canChoose)
        assertEquals("311111116", body.org?.orgnummer)
        assertEquals(listOf(true, false), body.choosableOrgs.map { it.hasOwnList })
    }

    @Test
    fun `PUT stores the choice and GET returns the chosen org`() = testApp {
        val response = choose("311111124")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("311111124", valkeyService.getSessionOrg(HPR))
        val body = json(get("/api/session-org").bodyAsText())
        assertEquals("311111124", body.org?.orgnummer)
        assertEquals(false, body.needsChoice)
    }

    @Test
    fun `PUT with an unknown orgnr returns 400 and stores nothing`() = testApp {
        val response = choose("999999999")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(valkeyService.getSessionOrg(HPR))
    }

    @Test
    fun `PUT is rejected when the org is derived from a single list`() = testApp {
        coEvery { fastlegelisteRepository.findLegekontorIdsByHpr(HPR) } returns listOf(fjordvik.id)

        val response = choose("311111124")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertNull(valkeyService.getSessionOrg(HPR))
    }

    @Test
    fun `changing org clears the active patient`() = testApp {
        valkeyService.setActivePatient(HPR, Uuid.generateV4().toString())

        choose("311111116")

        assertNull(valkeyService.getActivePatient(HPR))
    }

    @Test
    fun `a rejected change keeps the active patient`() = testApp {
        val patient = Uuid.generateV4().toString()
        valkeyService.setActivePatient(HPR, patient)

        choose("999999999")

        assertEquals(patient, valkeyService.getActivePatient(HPR))
    }
}
