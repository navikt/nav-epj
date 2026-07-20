package no.nav.helse.smart.security

data class SmartClient(
  val clientId: String,
  val redirectUris: List<String>,
  val launchUris: List<String>,
  val clientSecret: String? = null,
)

data class SmartPrincipal(
  val subject: String,
  val scope: String,
  val patient: String?,
  val encounter: String?,
)
