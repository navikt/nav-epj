package no.nav.helse.smart

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

data class SmartDiscoveryDocument(
  val issuer: String,
  @get:JsonProperty("jwks_uri") val jwksUri: String,
  @get:JsonProperty("authorization_endpoint") val authorizationEndpoint: String,
  @get:JsonProperty("token_endpoint") val tokenEndpoint: String,
  @get:JsonProperty("token_endpoint_auth_methods_supported")
  val tokenEndpointAuthMethodsSupported: List<String>,
  @get:JsonProperty("grant_types_supported") val grantTypesSupported: List<String>,
  @get:JsonProperty("registration_endpoint") val registrationEndpoint: String,
  @get:JsonProperty("scopes_supported") val scopesSupported: List<String>,
  @get:JsonProperty("response_types_supported") val responseTypesSupported: List<String>,
  @get:JsonProperty("management_endpoint") val managementEndpoint: String,
  @get:JsonProperty("introspection_endpoint") val introspectionEndpoint: String,
  @get:JsonProperty("revocation_endpoint") val revocationEndpoint: String,
  @get:JsonProperty("code_challenge_methods_supported")
  val codeChallengeMethodsSupported: List<String>,
  @get:JsonProperty("capabilities") val capabilities: List<String>,
  @get:JsonProperty("token_endpoint_auth_signing_alg_values_supported")
  val tokenEndpointAuthSigningAlgValuesSupported: List<String>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TokenResponse(
  @get:JsonProperty("access_token") val accessToken: String,
  @get:JsonProperty("id_token") val idToken: String? = null,
  @get:JsonProperty("patient") val patient: String? = null,
  @get:JsonProperty("encounter") val encounter: String? = null,
  @get:JsonProperty("refresh_token") val refreshToken: String? = null,
  @get:JsonProperty("token_type") val tokenType: String = "Bearer",
  @get:JsonProperty("expires_in") val expiresIn: Int = 3600,
  @get:JsonProperty("scope") val scope: String,
  @get:JsonProperty("need_patient_banner") val needPatientBanner: Boolean,
)
