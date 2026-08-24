package no.nav.helse.smart.security

enum class TokenEndpointAuthMethod(val value: String) {
  NONE("none"),
  CLIENT_SECRET_BASIC("client_secret_basic"),
  PRIVATE_KEY_JWT("private_key_jwt");

  companion object {
    fun from(value: String): TokenEndpointAuthMethod =
      entries.find { it.value == value }
        ?: throw IllegalArgumentException("Invalid token endpoint: $value")
  }
}

data class SmartClient(
  val clientId: String,
  val redirectUris: List<String>,
  val launchUris: List<String>,
  val tokenEndpointAuthMethod: TokenEndpointAuthMethod,
  val clientSecret: String? = null,
  val jwksUri: String? = null,
  val allowedScopes: Set<SmartScope>,
)

data class SmartPrincipal(
  val subject: String,
  val scopes: Set<SmartScope>,
  val patient: String?,
  val encounter: String?,
)
