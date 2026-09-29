package no.nav.helse.fhir.capabilitystatement

import com.google.fhir.model.r4.CapabilityStatement.TypeRestfulInteraction
import com.google.fhir.model.r4.terminologies.ResourceType
import com.google.fhir.model.r4.terminologies.SearchParamType

data class DeclaredSearchParam(val name: String, val type: SearchParamType)

data class DeclaredResource(
    val type: ResourceType,
    val interactions: List<TypeRestfulInteraction>,
    val searchParams: List<DeclaredSearchParam> = emptyList(),
)

private fun reference(name: String) = DeclaredSearchParam(name, SearchParamType.Reference)

private fun token(name: String) = DeclaredSearchParam(name, SearchParamType.Token)

val declaredFhirCapabilities: List<DeclaredResource> =
    listOf(
        DeclaredResource(ResourceType.Patient, listOf(TypeRestfulInteraction.Read)),
        DeclaredResource(
            ResourceType.Encounter,
            listOf(TypeRestfulInteraction.Read, TypeRestfulInteraction.Search_Type),
            listOf(reference("patient"), reference("subject")),
        ),
        DeclaredResource(
            ResourceType.Condition,
            listOf(TypeRestfulInteraction.Search_Type),
            listOf(reference("subject"), reference("encounter")),
        ),
        DeclaredResource(
            ResourceType.Observation,
            listOf(
                TypeRestfulInteraction.Read,
                TypeRestfulInteraction.Search_Type,
                TypeRestfulInteraction.Create,
            ),
            listOf(
                reference("patient"),
                reference("subject"),
                reference("encounter"),
                token("code"),
            ),
        ),
        DeclaredResource(ResourceType.Practitioner, listOf(TypeRestfulInteraction.Read)),
        DeclaredResource(
            ResourceType.PractitionerRole,
            listOf(TypeRestfulInteraction.Search_Type),
            listOf(reference("practitioner")),
        ),
        DeclaredResource(ResourceType.Organization, listOf(TypeRestfulInteraction.Read)),
        DeclaredResource(
            ResourceType.DocumentReference,
            listOf(
                TypeRestfulInteraction.Read,
                TypeRestfulInteraction.Search_Type,
                TypeRestfulInteraction.Create,
            ),
            listOf(reference("patient"), reference("subject"), reference("encounter")),
        ),
    )
