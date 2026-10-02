package no.nav.helse.smart

import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Patient
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.helse.core.Environment
import no.nav.helse.core.EpjConfig
import no.nav.helse.core.SmartConfig
import no.nav.helse.core.ValkeyConfig
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.helseId.DebugInfo
import no.nav.helse.helseId.HelseIdPrincipal
import no.nav.helse.helseId.User
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.smart.api.configureSmartRouting
import no.nav.helse.smart.security.ClientAssertionVerifier
import no.nav.helse.smart.security.LaunchMode
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.SmartKeys
import no.nav.helse.smart.security.TokenEndpointAuthMethod
import no.nav.helse.smart.security.parseRegisteredScopes
import no.nav.helse.smart.valkey.LaunchContext
import no.nav.helse.smart.valkey.ValkeyService
import no.nav.helse.utils.WithValkey
import no.nav.helse.utils.simpleTestEnvironment
import org.junit.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private const val HPR = "111"
private const val SECRET = "super-secret-client-value"
private const val ISS = "http://test/fhir"
private const val ISS_ENCODED = "http%3A%2F%2Ftest%2Ffhir"

private val clients =
    listOf(
        SmartClient(
            clientId = "syk-inn",
            redirectUris = listOf("https://sykmelding.test/callback"),
            launchUris =
                listOf("https://sykmelding.test/fhir/launch", "https://other.test/fhir/launch"),
            tokenEndpointAuthMethod = TokenEndpointAuthMethod.CLIENT_SECRET_BASIC,
            clientSecret = SECRET,
            allowedScopes = parseRegisteredScopes(listOf("openid", "launch", "patient/Patient.rs")),
            displayName = "Sykmelding",
            beskrivelse = "Skriv sykmelding og send den til Nav.",
            ikon = "sykmelding",
            launchMode = LaunchMode.IFRAME,
        ),
        SmartClient(
            clientId = "validator",
            redirectUris = listOf("https://validator.test"),
            launchUris = listOf("https://validator.test/launch"),
            tokenEndpointAuthMethod = TokenEndpointAuthMethod.PRIVATE_KEY_JWT,
            jwksUri = "https://validator.test/jwks.json",
            allowedScopes = parseRegisteredScopes(listOf("openid", "launch")),
            displayName = "Validator",
            launchMode = LaunchMode.ASK,
        ),
    )

class AppRoutesTest : WithValkey() {

    private val patientService = mockk<PatientService>()
    private val encounterService = mockk<EncounterService>()
    private val mapper = jacksonObjectMapper()

    private fun testApp(block: suspend HttpClient.() -> Unit) = testApplication {
        val environment =
            Environment(
                postgres = simpleTestEnvironment.postgres,
                smart =
                    SmartConfig(
                        issuerBaseUrl = "http://test/oidc",
                        fhirServerUrl = ISS,
                        clients = clients,
                        privateKeyJwk = simpleTestEnvironment.smart.privateKeyJwk,
                    ),
                valkey = ValkeyConfig("valkey", 8080, false, null, null),
                epj = EpjConfig(baseUrl = "testurl"),
            )
        application {
            configureSerialization()
            dependencies {
                provide<Environment> { environment }
                provide<ValkeyService> { valkeyService }
                provide<EncounterService> { encounterService }
                provide<PatientService> { patientService }
                provide<ClientAssertionVerifier> { mockk(relaxed = true) }
                provide<SmartKeys> { SmartKeys(environment.smart.privateKeyJwk) }
            }
            authentication {
                provider("wonderwall-helseid") {
                    authenticate { ctx ->
                        ctx.principal(
                            HelseIdPrincipal(User(name = "Test", hpr = HPR), DebugInfo("", ""))
                        )
                    }
                }
            }
            configureSmartRouting()
        }
        redirectClient = createClient { followRedirects = false }
        client.block()
    }

    private lateinit var redirectClient: HttpClient

    private fun noRedirects() = redirectClient

    private var currentPatient = UUID.randomUUID().toString()

    private suspend fun HttpClient.launch(appId: String, patientId: String = currentPatient) =
        post("/api/launch") {
            contentType(ContentType.Application.Json)
            setBody("""{"appId":"$appId","patientId":"$patientId"}""")
        }

    private fun patientWithId(patientId: String) = mockk<Patient> { every { id } returns patientId }

    private fun encounterWithId(encounterId: String) =
        mockk<Encounter> { every { id } returns encounterId }

    private fun tree(text: String): Map<String, String> = mapper.readValue(text)

    private fun activePatient(id: String = UUID.randomUUID().toString()): String {
        runBlocking { valkeyService.setActivePatient(HPR, id) }
        currentPatient = id
        return id
    }

    private fun launchKeys(): Set<String> = runBlocking {
        val keys = glideClient.customCommand(arrayOf("KEYS", "smart:launch:*")).get()
        (keys as Array<*>).map { it.toString() }.toSet()
    }

    private fun clearActivePatient() {
        runBlocking { glideClient.del(arrayOf("smart:active-patient:$HPR")).get() }
    }

    @Test
    fun `GET api apps returns the registered clients in config order`() = testApp {
        val response = get("/api/apps")

        assertEquals(HttpStatusCode.OK, response.status)
        val apps = mapper.readValue<List<Map<String, Any?>>>(response.bodyAsText())
        assertEquals(listOf("syk-inn", "validator"), apps.map { it["clientId"] })

        assertEquals(
            mapOf(
                "clientId" to "syk-inn",
                "navn" to "Sykmelding",
                "beskrivelse" to "Skriv sykmelding og send den til Nav.",
                "ikon" to "sykmelding",
                "launchMode" to "iframe",
                "launchUri" to "https://sykmelding.test/fhir/launch",
                "tokenEndpointAuthMethod" to "client_secret_basic",
                "jwksUri" to null,
                "redirectUris" to listOf("https://sykmelding.test/callback"),
                "scopes" to listOf("openid", "launch", "patient/Patient.rs"),
            ),
            apps[0],
        )
        assertEquals(
            mapOf(
                "clientId" to "validator",
                "navn" to "Validator",
                "beskrivelse" to "",
                "ikon" to "vindu",
                "launchMode" to "ask",
                "launchUri" to "https://validator.test/launch",
                "tokenEndpointAuthMethod" to "private_key_jwt",
                "jwksUri" to "https://validator.test/jwks.json",
                "redirectUris" to listOf("https://validator.test"),
                "scopes" to listOf("openid", "launch"),
            ),
            apps[1],
        )
    }

    @Test
    fun `GET api apps never exposes the client secret`() = testApp {
        val body = get("/api/apps").bodyAsText()

        assertFalse(SECRET in body)
        assertFalse("clientSecret" in body, "clientSecret must not be a field in the response")
        assertFalse("inlineJwkSet" in body)
    }

    @Test
    fun `POST api launch creates a single-use launch context and returns the launch url`() =
        testApp {
            activePatient()
            coEvery { patientService.getPatient(any<PatientInputId>()) } returns
                patientWithId("patient-1")
            coEvery { encounterService.getActiveEncounterByPatient(any<PatientInputId>()) } returns
                encounterWithId("encounter-1")

            val response = launch("syk-inn")

            assertEquals(HttpStatusCode.OK, response.status)
            val launchUrl = tree(response.bodyAsText()).getValue("launchUrl")
            val prefix = "https://sykmelding.test/fhir/launch/?iss=$ISS_ENCODED&launch="
            assertTrue(launchUrl.startsWith(prefix), launchUrl)
            val launchId = launchUrl.removePrefix(prefix)
            assertEquals(
                LaunchContext("patient-1", "encounter-1", HPR),
                valkeyService.getAndDeleteLaunchContext(launchId),
            )
            assertNull(valkeyService.getAndDeleteLaunchContext(launchId))
        }

    @Test
    fun `POST api launch creates a new launch id on every call`() = testApp {
        activePatient()
        coEvery { patientService.getPatient(any<PatientInputId>()) } returns
            patientWithId("patient-1")
        coEvery { encounterService.getActiveEncounterByPatient(any<PatientInputId>()) } returns
            encounterWithId("encounter-1")

        val first = tree(launch("validator").bodyAsText()).getValue("launchUrl")
        val second = tree(launch("validator").bodyAsText()).getValue("launchUrl")

        assertTrue(first != second)
    }

    @Test
    fun `POST api launch without an active patient returns 409 NO_ACTIVE_PATIENT`() = testApp {
        clearActivePatient()

        val response = launch("syk-inn")

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = tree(response.bodyAsText())
        assertEquals("NO_ACTIVE_PATIENT", body["code"])
        assertEquals("syk-inn", body["appId"])
        assertTrue(body.getValue("message").isNotBlank())
    }

    @Test
    fun `POST api launch for another patient than the active one returns 409 PATIENT_MISMATCH`() =
        testApp {
            activePatient()
            coEvery { patientService.getPatient(any<PatientInputId>()) } returns
                patientWithId("patient-1")
            coEvery { encounterService.getActiveEncounterByPatient(any<PatientInputId>()) } returns
                encounterWithId("encounter-1")

            val response = launch("syk-inn", UUID.randomUUID().toString())

            assertEquals(HttpStatusCode.Conflict, response.status)
            val body = tree(response.bodyAsText())
            assertEquals("PATIENT_MISMATCH", body["code"])
            assertEquals("syk-inn", body["appId"])
        }

    @Test
    fun `POST api launch follows a switch of the active patient`() = testApp {
        val first = activePatient()
        coEvery { patientService.getPatient(any<PatientInputId>()) } returns
            patientWithId("patient-1")
        coEvery { encounterService.getActiveEncounterByPatient(any<PatientInputId>()) } returns
            encounterWithId("encounter-1")
        assertEquals(HttpStatusCode.OK, launch("syk-inn", first).status)

        val second = activePatient()

        assertEquals(HttpStatusCode.Conflict, launch("syk-inn", first).status)
        assertEquals(HttpStatusCode.OK, launch("syk-inn", second).status)
    }

    @Test
    fun `POST api launch with a malformed patient id returns 409 PATIENT_MISMATCH`() = testApp {
        activePatient()

        val response = launch("syk-inn", "not-a-uuid")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("PATIENT_MISMATCH", tree(response.bodyAsText())["code"])
    }

    @Test
    fun `POST api launch does not store a launch context on a mismatch`() = testApp {
        val active = activePatient()
        coEvery { patientService.getPatient(any<PatientInputId>()) } returns
            patientWithId("patient-1")
        coEvery { encounterService.getActiveEncounterByPatient(any<PatientInputId>()) } returns
            encounterWithId("encounter-1")
        val before = launchKeys()

        launch("syk-inn", UUID.randomUUID().toString())

        assertEquals(before, launchKeys())
        assertEquals(active, valkeyService.getActivePatient(HPR))
    }

    @Test
    fun `POST api launch with a stored patient that no longer exists returns 409 NO_ACTIVE_PATIENT`() =
        testApp {
            activePatient()
            coEvery { patientService.getPatient(any<PatientInputId>()) } returns null

            val response = launch("syk-inn")

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertEquals("NO_ACTIVE_PATIENT", tree(response.bodyAsText())["code"])
        }

    @Test
    fun `POST api launch without an ongoing konsultasjon returns 409 NO_ACTIVE_ENCOUNTER`() =
        testApp {
            activePatient()
            coEvery { patientService.getPatient(any<PatientInputId>()) } returns
                patientWithId("patient-1")
            coEvery { encounterService.getActiveEncounterByPatient(any<PatientInputId>()) } returns
                null

            val response = launch("validator")

            assertEquals(HttpStatusCode.Conflict, response.status)
            val body = tree(response.bodyAsText())
            assertEquals("NO_ACTIVE_ENCOUNTER", body["code"])
            assertEquals("validator", body["appId"])
        }

    @Test
    fun `POST api launch for an unregistered app returns 404 UNKNOWN_APP`() = testApp {
        activePatient()

        val response = launch("finnes-ikke")

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = tree(response.bodyAsText())
        assertEquals("UNKNOWN_APP", body["code"])
        assertEquals("finnes-ikke", body["appId"])
    }

    @Test
    fun `GET fhir launch keeps redirecting to the app with iss and launch`() = testApp {
        activePatient()
        coEvery { patientService.getPatient(any<PatientInputId>()) } returns
            patientWithId("patient-1")
        coEvery { encounterService.getActiveEncounterByPatient(any<PatientInputId>()) } returns
            encounterWithId("encounter-1")

        val response =
            noRedirects().get("/fhir/launch") {
                parameter("url", "https://sykmelding.test/fhir/launch")
            }

        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location].orEmpty()
        assertTrue(
            location.startsWith("https://sykmelding.test/fhir/launch/?iss=$ISS_ENCODED&launch="),
            location,
        )
    }

    @Test
    fun `GET fhir launch keeps its plain text errors`() = testApp {
        clearActivePatient()
        val noPatient =
            noRedirects().get("/fhir/launch") {
                parameter("url", "https://sykmelding.test/fhir/launch")
            }
        assertEquals(HttpStatusCode.Conflict, noPatient.status)
        assertEquals("No active patient context for clinician", noPatient.bodyAsText())

        activePatient()
        coEvery { patientService.getPatient(any<PatientInputId>()) } returns null
        val unknownPatient =
            noRedirects().get("/fhir/launch") {
                parameter("url", "https://sykmelding.test/fhir/launch")
            }
        assertEquals(HttpStatusCode.BadRequest, unknownPatient.status)
        assertEquals("Unknown patient", unknownPatient.bodyAsText())

        coEvery { patientService.getPatient(any<PatientInputId>()) } returns
            patientWithId("patient-1")
        coEvery { encounterService.getActiveEncounterByPatient(any<PatientInputId>()) } returns null
        val noEncounter =
            noRedirects().get("/fhir/launch") {
                parameter("url", "https://sykmelding.test/fhir/launch")
            }
        assertEquals(HttpStatusCode.BadRequest, noEncounter.status)
        assertEquals("Found no active encounter for patient", noEncounter.bodyAsText())
    }
}
