package no.nav.helse.smart.db

/**
 * Everything `/oidc/token` needs to redeem an authorization code, captured at `/oidc/authorize` and
 * stored under the code in a [SingleUseStore]. The code itself is a random opaque string; all state
 * lives here.
 */
data class AuthCodeContext(
  /** Clinician display name, for logging. */
  val username: String,
  /** `redirect_uri` from `/oidc/authorize`; `/oidc/token` requires an identical value. */
  val redirectUrl: String,
  /** Patient/encounter launch context resolved at `/fhir/launch`. */
  val launch: LaunchContext,
  /** Clinician's HPR number; becomes the token's `sub` claim. */
  val hpr: String?,
  /** `scope` requested at `/oidc/authorize`, copied verbatim onto the access token. */
  val scope: String,
  /** Requesting app's `client_id`, used to re-look-up the [SmartClient] at `/oidc/token`. */
  val clientId: String,
  /** PKCE `code_challenge`, verified at `/oidc/token` via [codeChallengeS256]. */
  val codeChallenge: String,
)
