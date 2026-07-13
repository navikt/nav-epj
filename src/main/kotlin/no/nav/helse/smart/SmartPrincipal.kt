package no.nav.helse.smart

data class SmartPrincipal(
  val subject: String,
  val scope: String,
  val patient: String?,
  val encounter: String?,
)
