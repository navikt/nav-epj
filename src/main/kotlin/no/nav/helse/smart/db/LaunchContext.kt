package no.nav.helse.smart.db

/**
 * Clinical context (patient plus optional active encounter) selected in this app's EPJ UI at EHR
 * launch. Saved under a `launch` id at `/fhir/launch`, resolved at `/oidc/authorize`, then surfaced
 * to the app as the token's `patient`/`encounter` claims (when a `launch` scope was granted).
 */
data class LaunchContext(
  /** FHIR `Patient.id` in context; null if no patient is selected. */
  val patientId: String?,
  /** FHIR `Encounter.id` in context; null if there is no active encounter. */
  val encounterId: String?,
)
