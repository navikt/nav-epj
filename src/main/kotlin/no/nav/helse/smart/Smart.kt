package no.nav.helse.smart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body for `GET /fhir/.well-known/smart-configuration` (SMART discovery document),
 * serialized to JSON. Field docs note REQUIRED/RECOMMENDED/OPTIONAL per the SMART IG. All values
 * are populated in [no.nav.helse.smart.api.configureSmartRouting].
 */
@Serializable
data class SmartDiscoveryDocument(
  /** REQUIRED. Issuer identifier; matches the `iss` claim on issued tokens. */
  val issuer: String,
  /** REQUIRED. Where to fetch this server's public signing key(s) (`GET /oidc/jwks`). */
  @SerialName("jwks_uri") val jwksUri: String,
  /** REQUIRED. The authorization endpoint (`GET /oidc/authorize`). */
  @SerialName("authorization_endpoint") val authorizationEndpoint: String,
  /** REQUIRED. The token endpoint (`POST /oidc/token`). */
  @SerialName("token_endpoint") val tokenEndpoint: String,
  /** RECOMMENDED. Client auth methods actually enforced at `/oidc/token`. */
  @SerialName("token_endpoint_auth_methods_supported")
  val tokenEndpointAuthMethodsSupported: List<String>,
  /** RECOMMENDED. Grant types the token endpoint accepts. */
  @SerialName("grant_types_supported") val grantTypesSupported: List<String>,
  /** OPTIONAL. Dynamic client registration endpoint. */
  @SerialName("registration_endpoint") val registrationEndpoint: String,
  /** RECOMMENDED. Scope values this server understands. */
  @SerialName("scopes_supported") val scopesSupported: List<String>,
  /** REQUIRED. Supported `response_type` values; only the authorization code flow. */
  @SerialName("response_types_supported") val responseTypesSupported: List<String>,
  /** OPTIONAL. User-access-consent management endpoint. */
  @SerialName("management_endpoint") val managementEndpoint: String,
  /** OPTIONAL. Token introspection endpoint. */
  @SerialName("introspection_endpoint") val introspectionEndpoint: String,
  /** OPTIONAL. Token revocation endpoint. */
  @SerialName("revocation_endpoint") val revocationEndpoint: String,
  /** REQUIRED (PKCE is mandatory). Only "S256" is accepted. */
  @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>,
  /** REQUIRED. SMART capability strings describing what this server supports. */
  @SerialName("capabilities") val capabilities: List<String>,
)

/**
 * JSON body returned by `POST /oidc/token` on success (RFC 6749 plus SMART launch-context
 * extensions). Every field is always serialized (empty string, not omitted) for a stable response
 * shape, at the cost of strictly following RFC 6749's "OPTIONAL fields may be omitted" wording.
 */
@Serializable
data class TokenResponse(
  /** REQUIRED. The FHIR access token, a signed JWT (see [SmartKeys]). */
  @SerialName("access_token") val accessToken: String,
  /** OIDC extension. Empty when `openid` scope wasn't granted. */
  @SerialName("id_token") val idToken: String,
  /** SMART launch context. The patient in context; empty if `launch` scope missing. */
  @SerialName("patient") val patient: String,
  /** SMART launch context. The encounter in context; empty if `launch` scope missing. */
  @SerialName("encounter") val encounter: String,
  /** OPTIONAL. Only issued when `offline_access` was granted. */
  @SerialName("refresh_token") val refreshToken: String,
  /** REQUIRED, case-insensitive. Always "Bearer". */
  @SerialName("token_type") val tokenType: String = "Bearer",
  /** RECOMMENDED. Matches the access token's 1-hour lifetime, in seconds. */
  @SerialName("expires_in") val expiresIn: Int = 3600,
  /**
   * OPTIONAL if identical to the requested scope. TODO remove default values. Available scopes are
   * shown in the discovery document.
   */
  @SerialName("scope")
  val scope: String = "openid profile launch fhirUser patient/*.* user/*.* offline_access",
  /** SMART `context-banner` extension. Tells the app whether to render its own patient banner */
  @SerialName("need_patient_banner") val needPatientBanner: Boolean = true,
)
