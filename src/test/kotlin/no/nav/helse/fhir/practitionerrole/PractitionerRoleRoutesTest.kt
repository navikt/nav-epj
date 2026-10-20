package no.nav.helse.fhir.practitionerrole

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.parseScopes

class PractitionerRoleRoutesTest {
    private val service = mockk<PractitionerRoleService>()
    private val bundle = Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

    private fun testApp(block: suspend io.ktor.client.HttpClient.() -> Unit) = testApplication {
        application {
            configureStatusPages()
            authentication {
                provider("smart-access-token") {
                    authenticate { ctx ->
                        ctx.principal(
                            SmartPrincipal(
                                subject = "111",
                                scopes = parseScopes("user/PractitionerRole.rs"),
                                patient = null,
                                encounter = null,
                                orgnummer = "311111116",
                            )
                        )
                    }
                }
            }
            routing {
                authenticate("smart-access-token") {
                    practitionerRoleRoutes(
                        service,
                        FhirR4Json(),
                        ContentType("application", "fhir+json"),
                    )
                }
            }
        }
        client.block()
    }

    @Test
    fun `session org is passed for the caller's own practitioner`() = testApp {
        coEvery { service.getPractitionerRolesByPractitioner("111", "311111116") } returns bundle
        get("/fhir/PractitionerRole?practitioner=Practitioner/111")
        coVerify { service.getPractitionerRolesByPractitioner("111", "311111116") }
    }

    @Test
    fun `session org is not passed for another practitioner`() = testApp {
        coEvery { service.getPractitionerRolesByPractitioner("222", null) } returns bundle
        get("/fhir/PractitionerRole?practitioner=Practitioner/222")
        coVerify { service.getPractitionerRolesByPractitioner("222", null) }
    }
}
