package no.nav.helse.fhir.capabilitystatement

import com.google.fhir.model.r4.CapabilityStatement
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.terminologies.FHIRVersion
import com.google.fhir.model.r4.terminologies.PublicationStatus
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.capabilityStatementRoutes(fhirR4Json: FhirR4Json, fhirContentType: ContentType) {
    val body = fhirR4Json.encodeToString(capabilityStatement(declaredFhirCapabilities))
    route("/fhir") { get("/metadata") { call.respondText(body, fhirContentType) } }
}

private fun capabilityStatement(resources: List<DeclaredResource>) =
    CapabilityStatement(
        status = Enumeration(value = PublicationStatus.Active),
        date = DateTime(value = FhirDateTime.fromString("2025-01-01T00:00:00Z")),
        kind = Enumeration(value = CapabilityStatement.CapabilityStatementKind.Instance),
        fhirVersion = Enumeration(value = FHIRVersion._4_0_1),
        format = listOf(Code(value = "json")),
        rest =
            listOf(
                CapabilityStatement.Rest(
                    mode = Enumeration(value = CapabilityStatement.RestfulCapabilityMode.Server),
                    resource = resources.map { it.toCapabilityResource() },
                )
            ),
    )

private fun DeclaredResource.toCapabilityResource() =
    CapabilityStatement.Rest.Resource(
        type = Enumeration(value = type),
        interaction =
            interactions.map {
                CapabilityStatement.Rest.Resource.Interaction(code = Enumeration(value = it))
            },
        searchParam =
            searchParams.map {
                CapabilityStatement.Rest.Resource.SearchParam(
                    name = FhirString(value = it.name),
                    type = Enumeration(value = it.type),
                )
            },
    )
