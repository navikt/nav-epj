package no.nav.helse.fhir.organization

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.LegekontorNotfoundException
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorId
import no.nav.helse.epj.legekontor.LegekontorService
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import no.nav.helse.smart.security.parseScopes
import org.junit.Test

class OrganizationRoutesTest {

    private val legekontorService = mockk<LegekontorService>()
    private val organizationService = OrganizationService(legekontorService)
    private val fhirJson = FhirR4Json()
    private val fhirContentType = ContentType("application", "fhir+json")

    private fun testApp(
        scopes: Set<SmartScope> = emptySet(),
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
                                patient = null,
                                encounter = null,
                            )
                        )
                    }
                }
            }
            routing {
                authenticate("smart-access-token") {
                    organizationRoutes(organizationService, fhirJson, fhirContentType)
                }
            }
        }
        client.block()
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Organization returns 200 for a token without any Organization scope`() {
        val kontor =
            Legekontor(
                id = LegekontorId(Uuid.generateV4()),
                navn = "Testlegekontor",
                orgnummer = "123456789",
                tlf = "12345678",
            )
        coEvery { legekontorService.getLegekontor(kontor.id) } returns kontor

        testApp {
            val response = get("/fhir/Organization/${kontor.id.value}")

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Organization returns 404 for an unknown id`() {
        val id = LegekontorId(Uuid.generateV4())
        coEvery { legekontorService.getLegekontor(id) } throws LegekontorNotfoundException()

        testApp {
            val response = get("/fhir/Organization/${id.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Organization is forbidden for a system token without an Organization scope`() {
        val id = LegekontorId(Uuid.generateV4())

        testApp(parseScopes("system/Patient.rs")) {
            val response = get("/fhir/Organization/${id.value}")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `GET Organization returns 200 for a system token with system Organization r`() {
        val kontor =
            Legekontor(
                id = LegekontorId(Uuid.generateV4()),
                navn = "Testlegekontor",
                orgnummer = "123456789",
                tlf = "12345678",
            )
        coEvery { legekontorService.getLegekontor(kontor.id) } returns kontor

        testApp(parseScopes("system/Organization.r")) {
            val response = get("/fhir/Organization/${kontor.id.value}")

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
