@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.encounter

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
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
import no.nav.helse.fhir.patient.PatientInputId
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import org.junit.Test

class EncounterRoutesTest {

    private val encounterService = mockk<EncounterService>()
    private val fhirJson = FhirR4Json()
    private val fhirContentType = ContentType("application", "fhir+json")

    private fun testApp(
        scopes: Set<SmartScope>,
        boundPatient: String?,
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
                    encounterRoutes(encounterService, fhirJson, fhirContentType)
                }
            }
        }
        client.block()
    }

    private fun encounterScope(context: ScopeContext) =
        SmartScope.Fhir(
            context = context,
            resourceType = "Encounter",
            interactions = setOf(Interaction.SEARCH),
        )

    @Test
    fun `GET Encounter search accepts a system-level scope without launch context`() {
        val patientId = Uuid.generateV4()
        coEvery { encounterService.getEncountersByPatient(PatientInputId(patientId)) } returns
            Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

        testApp(scopes = setOf(encounterScope(ScopeContext.SYSTEM)), boundPatient = null) {
            val response = get("/fhir/Encounter?patient=Patient/$patientId")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(true, response.bodyAsText().contains("\"searchset\""))
        }
        coVerify(exactly = 1) { encounterService.getEncountersByPatient(PatientInputId(patientId)) }
    }

    @Test
    fun `GET Encounter search without subject or patient returns 400`() {
        testApp(scopes = setOf(encounterScope(ScopeContext.SYSTEM)), boundPatient = null) {
            val response = get("/fhir/Encounter")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }
}
