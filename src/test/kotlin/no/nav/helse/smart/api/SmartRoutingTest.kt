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
}
