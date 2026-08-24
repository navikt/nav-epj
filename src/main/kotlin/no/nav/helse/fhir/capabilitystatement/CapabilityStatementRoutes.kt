package no.nav.helse.fhir.capabilitystatement

import com.google.fhir.model.r4.CapabilityStatement
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.terminologies.FHIRVersion
import com.google.fhir.model.r4.terminologies.PublicationStatus
import com.google.fhir.model.r4.terminologies.ResourceType
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.capabilityStatementRoutes(fhirR4Json: FhirR4Json, fhirContentType: ContentType) {
  route("/fhir") {
    get("/metadata") {
      val capabilityStatement =
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
                resource =
                  listOf(
                    readAndSearchResource(ResourceType.Patient),
                    readAndSearchResource(ResourceType.Encounter),
                    searchOnlyResource(ResourceType.Condition),
                    readAndSearchResource(ResourceType.Practitioner),
                    searchOnlyResource(ResourceType.PractitionerRole),
                    readAndSearchResource(ResourceType.Organization),
                    readWriteResource(ResourceType.DocumentReference),
                  ),
              )
            ),
        )
      call.respondText(fhirR4Json.encodeToString(capabilityStatement), fhirContentType)
    }
  }
}

private fun interaction(
  code: CapabilityStatement.TypeRestfulInteraction
): CapabilityStatement.Rest.Resource.Interaction =
  CapabilityStatement.Rest.Resource.Interaction(code = Enumeration(value = code))

private fun readAndSearchResource(type: ResourceType) =
  CapabilityStatement.Rest.Resource(
    type = Enumeration(value = type),
    interaction =
      listOf(
        interaction(CapabilityStatement.TypeRestfulInteraction.Read),
        interaction(CapabilityStatement.TypeRestfulInteraction.Search_Type),
      ),
  )

private fun searchOnlyResource(type: ResourceType) =
  CapabilityStatement.Rest.Resource(
    type = Enumeration(value = type),
    interaction = listOf(interaction(CapabilityStatement.TypeRestfulInteraction.Search_Type)),
  )

private fun readWriteResource(type: ResourceType) =
  CapabilityStatement.Rest.Resource(
    type = Enumeration(value = type),
    interaction =
      listOf(
        interaction(CapabilityStatement.TypeRestfulInteraction.Read),
        interaction(CapabilityStatement.TypeRestfulInteraction.Search_Type),
        interaction(CapabilityStatement.TypeRestfulInteraction.Create),
        interaction(CapabilityStatement.TypeRestfulInteraction.Update),
      ),
  )
