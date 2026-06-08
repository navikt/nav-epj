package no.nav.helse.smartAuth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SmartDiscoveryDocument(
  val issuer: String,
  @SerialName("jwks_uri") val jwksUri: String,
  @SerialName("authorization_endpoint") val authorizationEndpoint: String,
  @SerialName("token_endpoint") val tokenEndpoint: String,
  @SerialName("token_endpoint_auth_methods_supported")
  val tokenEndpointAuthMethodsSupported: List<String>,
  @SerialName("grant_types_supported") val grantTypesSupported: List<String>,
  @SerialName("registration_endpoint") val registrationEndpoint: String,
  @SerialName("scopes_supported") val scopesSupported: List<String>,
  @SerialName("response_types_supported") val responseTypesSupported: List<String>,
  @SerialName("management_endpoint") val managementEndpoint: String,
  @SerialName("introspection_endpoint") val introspectionEndpoint: String,
  @SerialName("revocation_endpoint") val revocationEndpoint: String,
  @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>,
  @SerialName("capabilities") val capabilities: List<String>,
)

@Serializable
data class TokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("id_token") val id_token: String,
  @SerialName("patient") val patient: String,
  @SerialName("encounter") val encounter: String,
  @SerialName("refresh_token") val refresh_token: String,
  @SerialName("token_type") val token_type: String = "Bearer",
  @SerialName("expires_in") val expiresIn: Int = 3600,
  @SerialName("scope")
  val scope: String = "openid profile launch fhirUser patient/*.* user/*.* offline_access",
  @SerialName("need_patient_banner") val need_patient_banner: Boolean = true,
)
