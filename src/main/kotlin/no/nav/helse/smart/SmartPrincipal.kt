package no.nav.helse.smart

/**
 * Authenticated principal for a request carrying a valid SMART access token. Produced by
 * [configureSmartSecurity]'s `validate` block and read via `call.principal<SmartPrincipal>()`.
 */
data class SmartPrincipal(
  /** `sub` claim: the clinician's HPR number. */
  val subject: String,
  /** `scope` claim, verbatim; used by resource routes to authorize FHIR access. */
  val scope: String,
  /** `patient` claim, if the launch granted patient context. */
  val patient: String?,
  /** `encounter` claim, if the launch granted encounter context. */
  val encounter: String?,
)
