package no.nav.helse.smart

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class TokenResponseTest {

  private val mapper = jacksonObjectMapper()

  @Test
  fun `fields that are not assigned are omitted from JSON, not empty string`() {
    val response =
      TokenResponse(
        accessToken = "access-token",
        idToken = null,
        patient = null,
        encounter = null,
        refreshToken = null,
        scope = "openid fhirUser",
        needPatientBanner = false,
      )

    val json = mapper.writeValueAsString(response).replace('"', '\'')

    assertFalse("'id_token'" in json)
    assertFalse("'patient'" in json)
    assertFalse("'encounter'" in json)
    assertFalse("'refresh_token'" in json)
    assertTrue("'access_token'" in json)
    assertTrue("'scope'" in json)
  }

  @Test
  fun `assigned fields are included in JSON`() {
    val response =
      TokenResponse(
        accessToken = "access-token",
        idToken = "id-token",
        patient = "patient-1",
        encounter = "encounter-1",
        refreshToken = "refresh-token",
        scope = "openid launch patient/*.cruds offline_access",
        needPatientBanner = true,
      )

    val json = mapper.writeValueAsString(response).replace('"', '\'')

    assertTrue("'id_token':'id-token'" in json)
    assertTrue("'patient':'patient-1'" in json)
    assertTrue("'encounter':'encounter-1'" in json)
    assertTrue("'refresh_token':'refresh-token'" in json)
    assertTrue("'need_patient_banner':true" in json)
  }
}
