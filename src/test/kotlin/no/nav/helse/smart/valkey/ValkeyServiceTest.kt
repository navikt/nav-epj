package no.nav.helse.smart.valkey

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import no.nav.helse.utils.WithValkey
import org.junit.Test

class ValkeyServiceTest : WithValkey() {

  @Test
  fun `lagrer og henter launch context`() = runTest {
    val key = "launch-1"
    val context = LaunchContext(patientId = "patient-1", encounterId = "encounter-1")

    valkeyService.saveLaunchContext(key, context)
    val result = valkeyService.getLaunchContext(key)

    assertEquals(context, result)
  }

  @Test
  fun `henter launch context som ikke finnes gir null`() = runTest {
    val result = valkeyService.getLaunchContext("finnes-ikke")
    assertNull(result)
  }

  @Test
  fun `lagrer og henter auth code`() = runTest {
    val key = "auth-code-1"
    val authCode =
      AuthCodeContext(
        username = "Test",
        redirectUrl = "http://test",
        launch = LaunchContext(patientId = "patient-1", encounterId = "encounter-1"),
        subject = "111",
        scope = "openid launch",
        clientId = "test-client-id",
        codeChallenge = "challenge",
      )

    valkeyService.saveAuthCode(key, authCode)
    val result = valkeyService.getAuthCode(key)

    assertEquals(authCode, result)
  }

  @Test
  fun `henter auth code som ikke finnes gir null`() = runTest {
    val result = valkeyService.getAuthCode("finnes-ikke")
    assertNull(result)
  }

  @Test
  fun `getAndDeleteAuthCode returnerer og sletter koden`() = runTest {
    val key = "auth-code-2"
    val authCode =
      AuthCodeContext(
        username = "Test",
        redirectUrl = "http://test",
        launch = LaunchContext(patientId = "patient-2", encounterId = "encounter-2"),
        subject = "222",
        scope = "openid",
        clientId = "test-client-id",
        codeChallenge = "challenge",
      )

    valkeyService.saveAuthCode(key, authCode)
    val first = valkeyService.getAndDeleteAuthCode(key)
    val second = valkeyService.getAndDeleteAuthCode(key)

    assertEquals(authCode, first)
    assertNull(second)
  }

  @Test
  fun `set og get lagrer rene strenger`() = runTest {
    val key = "string-key"
    valkeyService.set(key, "hello")

    assertEquals("hello", valkeyService.get(key))
  }

  @Test
  fun `get på ukjent nøkkel gir null`() = runTest { assertNull(valkeyService.get("ukjent-nokkel")) }

  @Test
  fun `setIfAbsent lagrer verdi når nøkkel ikke finnes`() = runTest {
    val result = valkeyService.setIfAbsent("jti-1", "", ttlSeconds = 60)
    assertEquals(true, result)
    assertEquals("", valkeyService.get("jti-1"))
  }

  @Test
  fun `setIfAbsent gir false når nøkkel allerede finnes`() = runTest {
    valkeyService.setIfAbsent("jti-2", "", ttlSeconds = 60)
    val result = valkeyService.setIfAbsent("jti-2", "", ttlSeconds = 60)
    assertEquals(false, result)
  }
}
