package no.nav.helse.smart.api

import org.junit.Test

class SmartRoutingTest {

  @Test
  fun `should reject authorize request when launch token is missing or invalid`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect to redirect_uri with code and state on successful authorize`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return conflict from ehr launch when clinician has no active patient context`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return not found from ehr launch when patient does not exist`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect to app url with iss and launch id on successful ehr launch`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should expose public jwk at oidc jwks endpoint`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token request when code parameter is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token request when code is unknown or already used`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should issue access token with patient and encounter claims`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should only include id token when scope contains openid`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should set id token aud claim to the client_id, not the client's redirect_uris`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should only include refresh token when scope contains offline_access`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should expose smart configuration discovery document`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject fhir patient request without patient scope in token`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return not found when requested patient id does not match token patient context`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return patient resource for authorized patient id`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject encounter search without patient search parameter`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject encounter search outside authorized patient context`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return encounter bundle for authorized patient`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return not found for unknown encounter id`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should return encounter resource by id for authorized patient`() {
    TODO("not yet implemented")
  }

  // --- /fhir/launch: launch url allow-list (this session's fix #2) ---

  @Test
  fun `should reject ehr launch when url query parameter is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject ehr launch when url is not registered as a launch uri for any client`() {
    TODO("not yet implemented")
  }

  // --- /oidc/authorize: missing-parameter handling (this session's cleanup #5) ---
  // client_id/redirect_uri failures must reject directly (no redirect) per RFC 6749 §4.1.2.1.

  @Test
  fun `should reject authorize request directly without redirect when client_id is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject authorize request directly without redirect when redirect_uri is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject authorize request directly when client_id is not a registered client`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject authorize request directly when redirect_uri is not registered for the client`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject authorize request directly without redirect when state is missing`() {
    TODO("not yet implemented")
  }

  // Once redirect_uri/state are trusted, remaining errors are reported via redirect with state.

  @Test
  fun `should redirect with invalid_request error and state when response_type is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect with invalid_request error and state when launch is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect with invalid_request error and state when scope is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect with invalid_request error and state when aud is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect with invalid_request error and state when code_challenge is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect with invalid_request error and state when code_challenge_method is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect with unsupported_response_type error when response_type is not code`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect with invalid_request error when aud does not match the fhir server url`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should redirect with invalid_request error when code_challenge_method is not S256`() {
    TODO("not yet implemented")
  }

  // --- /oidc/token: PKCE verification (this session's fix #1, the critical one) ---

  @Test
  fun `should reject token request when code_verifier is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token request with invalid_grant when code_verifier does not match the stored code_challenge`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should issue tokens when code_verifier matches the stored code_challenge`() {
    TODO("not yet implemented")
  }

  // --- /oidc/token: redirect_uri match (this session's fix #3) ---

  @Test
  fun `should reject token request when redirect_uri parameter is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token request with invalid_grant when redirect_uri does not match the one used at authorize`() {
    TODO("not yet implemented")
  }

  // --- /oidc/token: client authentication (this session's fix #4) ---

  @Test
  fun `should reject token request when grant_type parameter is missing`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token request with unsupported_grant_type when grant_type is not authorization_code`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject token request when the client from the authorization code is no longer registered`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should not require client authentication for a public client with no configured secret`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject confidential client token request missing the Authorization header`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should reject confidential client token request with the wrong client secret`() {
    TODO("not yet implemented")
  }

  @Test
  fun `should issue tokens for a confidential client presenting valid client_secret_basic credentials`() {
    TODO("not yet implemented")
  }
}
