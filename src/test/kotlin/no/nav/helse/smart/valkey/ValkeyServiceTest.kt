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
    val context = LaunchContext(patientId = "patient-1", encounterId = "encounter-1", hpr = "hpr-1")

    valkeyService.saveLaunchContext(key, context)
    val result = valkeyService.getAndDeleteLaunchContext(key)

    assertEquals(context, result)
  }

  @Test
  fun `launch context kan kun hentes en gang`() = runTest {
    val key = "launch-once"
    val context = LaunchContext(patientId = "patient-1", encounterId = "encounter-1", hpr = "hpr-1")

    valkeyService.saveLaunchContext(key, context)
    valkeyService.getAndDeleteLaunchContext(key)
    val result = valkeyService.getAndDeleteLaunchContext(key)

    assertNull(result)
  }

  @Test
  fun `henter launch context som ikke finnes gir null`() = runTest {
    val result = valkeyService.getAndDeleteLaunchContext("finnes-ikke")
    assertNull(result)
  }

  @Test
  fun `lagrer og henter auth code`() = runTest {
    val key = "auth-code-1"
    val authCode =
      AuthCodeContext(
        username = "Test",
        redirectUrl = "http://test",
        launch = LaunchContext(patientId = "patient-1", encounterId = "encounter-1", hpr = "hpr-1"),
        subject = "111",
        scope = "openid launch",
        clientId = "test-client-id",
        codeChallenge = "challenge",
      )

    valkeyService.saveAuthCode(key, authCode)
    val result = valkeyService.getAndDeleteAuthCode(key)

    assertEquals(authCode, result)
  }

  @Test
  fun `henter auth code som ikke finnes gir null`() = runTest {
    val result = valkeyService.getAndDeleteAuthCode("finnes-ikke")
    assertNull(result)
  }

  @Test
  fun `getAndDeleteAuthCode returnerer og sletter koden`() = runTest {
    val key = "auth-code-2"
    val authCode =
      AuthCodeContext(
        username = "Test",
        redirectUrl = "http://test",
        launch = LaunchContext(patientId = "patient-2", encounterId = "encounter-2", hpr = "hpr-2"),
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
  fun `lagrer og henter active patient for hpr`() = runTest {
    val hpr = "hpr-1"
    valkeyService.setActivePatient(hpr, "patient-1")

    assertEquals("patient-1", valkeyService.getActivePatient(hpr))
  }

  @Test
  fun `active patient for ukjent hpr gir null`() = runTest {
    assertNull(valkeyService.getActivePatient("ukjent-hpr"))
  }

  @Test
  fun `auth code and launch context with the same id do not collide`() = runTest {
    val sameId = "shared-id"
    val authCode =
      AuthCodeContext(
        username = "Test",
        redirectUrl = "http://test",
        launch = LaunchContext(patientId = "patient-3", encounterId = "encounter-3", hpr = "hpr-3"),
        subject = "333",
        scope = "openid",
        clientId = "test-client-id",
        codeChallenge = "challenge",
      )
    val launchContext =
      LaunchContext(patientId = "patient-4", encounterId = "encounter-4", hpr = "hpr-4")

    valkeyService.saveAuthCode(sameId, authCode)
    valkeyService.saveLaunchContext(sameId, launchContext)

    assertEquals(launchContext, valkeyService.getAndDeleteLaunchContext(sameId))
    assertEquals(authCode, valkeyService.getAndDeleteAuthCode(sameId))
  }

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
