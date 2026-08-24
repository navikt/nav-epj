package no.nav.helse.fhir.security

import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope

class InsufficientScopeException(val resourceType: String, val interaction: Interaction) :
  RuntimeException("No granted scope covers $resourceType.${interaction.code}")

class PatientMismatchException(val resourceType: String) :
  RuntimeException("Requested $resourceType does not belong to the launched patient")

private fun SmartPrincipal.matchingScopes(
  resourceType: String,
  interaction: Interaction,
): List<SmartScope.Fhir> =
  scopes.filterIsInstance<SmartScope.Fhir>().filter {
    (it.resourceType == resourceType || it.resourceType == "*") && interaction in it.interactions
  }

fun ApplicationCall.requireFhirScope(
  resourceType: String,
  interaction: Interaction,
): SmartPrincipal {
  val principal = principal<SmartPrincipal>()!!
  if (principal.matchingScopes(resourceType, interaction).isEmpty()) {
    throw InsufficientScopeException(resourceType, interaction)
  }
  return principal
}

fun SmartPrincipal.requirePatientMatch(
  resourceType: String,
  interaction: Interaction,
  patientId: String?,
) {
  val matches = matchingScopes(resourceType, interaction)
  val patientBound = matches.isNotEmpty() && matches.all { it.context == ScopeContext.PATIENT }
  if (patientBound && patientId != patient) {
    throw PatientMismatchException(resourceType)
  }
}
