package no.nav.helse.smart.db

/**
 * A statically registered SMART app client. Loaded from `application*.yaml` via
 * `no.nav.helse.core.Environment`.
 *
 * TODO ideally we should have dynamic registration (as advertised by registration_endpoint in TODO
 * .well-known/smart-configuration)
 */
data class SmartClient(
  /** Value the app sends as `client_id` at `/oidc/authorize`. */
  val clientId: String,
  /**
   * Registered redirect URI(s). `/oidc/authorize` rejects any `redirect_uri` not listed here, and
   * `/oidc/token` requires an exact match to the one used at `/authorize`.
   */
  val redirectUris: List<String>,
  /**
   * Registered EHR-launch URL(s). `/fhir/launch` rejects any `url` not listed here; without this
   * check it would be an open redirect.
   */
  val launchUris: List<String>,
  /**
   * If set, a confidential client: `/oidc/token` requires `client_secret_basic` with this secret.
   * If null, a public client relying on PKCE alone (e.g. an in-browser SPA).
   */
  val clientSecret: String? = null,
)
