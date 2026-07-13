package no.nav.helse.smart.db

data class SmartClient(
  val clientId: String,
  val redirectUris: List<String>,
  val launchUris: List<String>,
  val clientSecret: String? = null,
)
