@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.capabilitystatement

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.CapabilityStatement
import com.google.fhir.model.r4.CapabilityStatement.TypeRestfulInteraction
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.terminologies.ResourceType
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.Environment
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.documentreference.DocumentReferenceService
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.fhirRoutes
import no.nav.helse.fhir.observation.ObservationService
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitionerrole.PractitionerRoleService
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import no.nav.helse.utils.simpleTestEnvironment
import org.junit.Test

class CapabilityStatementContractTest {

    private data class Endpoint(val method: HttpMethod, val path: String)

    private val conditionService = mockk<ConditionService>()
    private val encounterService = mockk<EncounterService>()
    private val observationService = mockk<ObservationService>()
    private val organizationService = mockk<OrganizationService>()
    private val patientService = mockk<PatientService>()
    private val practitionerService = mockk<PractitionerService>()
    private val practitionerRoleService = mockk<PractitionerRoleService>()
    private val documentReferenceService = mockk<DocumentReferenceService>()

    private val emptySearchset = Bundle(type = Enumeration(value = Bundle.BundleType.Searchset))

    private val unadvertisedRoutes =
        setOf(
            Endpoint(HttpMethod.Get, "/fhir/metadata"),
            Endpoint(HttpMethod.Put, "/fhir/DocumentReference/{}"),
            Endpoint(HttpMethod.Put, "/fhir/QuestionnaireResponse/{}"),
        )

    private val patientId = Uuid.generateV4()

    private val sampleSearchValues =
        mapOf(
            "patient" to "Patient/$patientId",
            "subject" to "Patient/$patientId",
            "encounter" to "Encounter/${Uuid.generateV4()}",
            "practitioner" to "Practitioner/123456",
            "code" to "8310-5",
        )

    private val patientCompartmentParams = setOf("patient", "subject")

    private val resourcesRequiringPatientCompartment =
        setOf(ResourceType.Observation, ResourceType.DocumentReference)

    private val expectedMetadata =
        mapOf(
            "Patient" to (listOf("read") to emptyList()),
            "Encounter" to
                (listOf("read", "search-type") to listOf("patient:reference", "subject:reference")),
            "Condition" to
                (listOf("search-type") to listOf("subject:reference", "encounter:reference")),
            "Observation" to
                (listOf("read", "search-type", "create") to
                    listOf(
                        "patient:reference",
                        "subject:reference",
                        "encounter:reference",
                        "code:token",
                    )),
            "Practitioner" to (listOf("read") to emptyList()),
            "PractitionerRole" to (listOf("search-type") to listOf("practitioner:reference")),
            "Organization" to (listOf("read") to emptyList()),
            "DocumentReference" to
                (listOf("read", "search-type", "create") to
                    listOf("patient:reference", "subject:reference", "encounter:reference")),
        )

    private fun testApp(block: suspend HttpClient.(RoutingRoot) -> Unit) = testApplication {
        lateinit var root: RoutingRoot
        application {
            configureStatusPages()
            dependencies {
                provide<ConditionService> { conditionService }
                provide<EncounterService> { encounterService }
                provide<ObservationService> { observationService }
                provide<OrganizationService> { organizationService }
                provide<PatientService> { patientService }
                provide<PractitionerService> { practitionerService }
                provide<PractitionerRoleService> { practitionerRoleService }
                provide<DocumentReferenceService> { documentReferenceService }
                provide<Environment> { simpleTestEnvironment }
            }
            authentication {
                provider("smart-access-token") {
                    authenticate { ctx ->
                        ctx.principal(
                            SmartPrincipal(
                                subject = "test-client",
                                scopes =
                                    setOf(
                                        SmartScope.Fhir(
                                            context = ScopeContext.USER,
                                            resourceType = "*",
                                            interactions = Interaction.entries.toSet(),
                                        )
                                    ),
                                patient = null,
                                encounter = null,
                            )
                        )
                    }
                }
            }
            root = routing { fhirRoutes() }
        }
        startApplication()
        client.block(root)
    }

    private fun RoutingNode.endpoint(): Endpoint? {
        var method: HttpMethod? = null
        val segments = ArrayDeque<String>()
        var node: RoutingNode? = this
        while (node != null) {
            when (val selector = node.selector) {
                is HttpMethodRouteSelector -> method = selector.method
                is PathSegmentConstantRouteSelector -> segments.addFirst(selector.value)
                is PathSegmentParameterRouteSelector -> segments.addFirst("{}")
                is AuthenticationRouteSelector,
                is RootRouteSelector -> Unit
                else -> error("Unexpected route selector $selector in $this")
            }
            node = node.parent
        }
        return method?.let { Endpoint(it, segments.joinToString("/", prefix = "/")) }
    }

    private fun declaredEndpoints(): Set<Endpoint> =
        declaredFhirCapabilities
            .flatMap { resource ->
                val type = resource.type.getCode()
                resource.interactions.map { interaction ->
                    when (interaction) {
                        TypeRestfulInteraction.Read -> Endpoint(HttpMethod.Get, "/fhir/$type/{}")
                        TypeRestfulInteraction.Search_Type ->
                            Endpoint(HttpMethod.Get, "/fhir/$type")
                        TypeRestfulInteraction.Create -> Endpoint(HttpMethod.Post, "/fhir/$type")
                        else -> error("No route mapping for $interaction on $type")
                    }
                }
            }
            .toSet()

    @Test
    fun `registered FHIR routes are exactly the advertised interactions plus the allowlist`() {
        testApp { root ->
            val registered = root.getAllRoutes().mapNotNull { it.endpoint() }.toSet()

            assertEquals(declaredEndpoints() + unadvertisedRoutes, registered)
        }
    }

    @Test
    fun `every advertised search parameter is accepted by its route`() {
        coEvery { encounterService.getEncountersByPatient(any()) } returns emptySearchset
        coEvery { conditionService.getConditionsByPatientId(any()) } returns emptySearchset
        coEvery { conditionService.getConditionsByEncounterId(any()) } returns emptySearchset
        coEvery { observationService.searchObservations(any(), any(), any()) } returns
            emptySearchset
        coEvery { practitionerRoleService.getPractitionerRolesByPractitioner(any()) } returns
            emptySearchset
        coEvery { documentReferenceService.searchDocumentReferences(any(), any()) } returns
            emptySearchset

        testApp {
            declaredFhirCapabilities.forEach { resource ->
                resource.searchParams.forEach { param ->
                    val response =
                        get("/fhir/${resource.type.getCode()}") {
                            parameter(param.name, sampleSearchValues.getValue(param.name))
                            if (
                                resource.type in resourcesRequiringPatientCompartment &&
                                    param.name !in patientCompartmentParams
                            ) {
                                parameter("patient", sampleSearchValues.getValue("patient"))
                            }
                        }

                    assertEquals(
                        HttpStatusCode.OK,
                        response.status,
                        "${resource.type.getCode()}?${param.name}",
                    )
                }
            }
        }
    }

    @Test
    fun `resources without advertised search do not answer a type search`() {
        testApp {
            declaredFhirCapabilities
                .filter { TypeRestfulInteraction.Search_Type !in it.interactions }
                .forEach { resource ->
                    val response = get("/fhir/${resource.type.getCode()}?_id=${Uuid.generateV4()}")

                    assertNotEquals(HttpStatusCode.OK, response.status, resource.type.getCode())
                }
        }
    }

    @Test
    fun `metadata advertises exactly the declared resources, interactions and search params`() {
        testApp {
            val response = get("/fhir/metadata")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "application/fhir+json",
                response.contentType()?.withoutParameters().toString(),
            )

            val statement =
                FhirR4Json().decodeFromString(response.bodyAsText()) as CapabilityStatement
            assertEquals("4.0.1", statement.fhirVersion.value?.getCode())
            assertEquals("active", statement.status.value?.getCode())
            assertEquals("instance", statement.kind.value?.getCode())
            assertEquals(listOf("json"), statement.format.map { it.value })

            val rest = statement.rest.single()
            assertEquals("server", rest.mode.value?.getCode())
            assertNull(rest.security)

            val advertised =
                rest.resource.associate { resource ->
                    resource.type.value!!.getCode() to
                        (resource.interaction.map { it.code.value!!.getCode() } to
                            resource.searchParam.map {
                                "${it.name.value}:${it.type.value!!.getCode()}"
                            })
                }
            assertEquals(expectedMetadata, advertised)
        }
    }
}
