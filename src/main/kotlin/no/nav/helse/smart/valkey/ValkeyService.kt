package no.nav.helse.smart.valkey

import glide.api.GlideClient
import glide.api.models.commands.SetOptions
import kotlinx.coroutines.future.await
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private const val AUTH_CODE_TTL_SECONDS = 60L
private const val LAUNCH_CONTEXT_TTL_SECONDS = 300L
private const val ACTIVE_PATIENT_TTL_SECONDS = 8 * 60 * 60L

class ValkeyService(private val glideClient: GlideClient) {
  private val mapper = jacksonObjectMapper()

  suspend fun saveLaunchContext(launchId: String, context: LaunchContext) {
    glideClient
      .set(
        launchKey(launchId),
        mapper.writeValueAsString(context),
        SetOptions.builder().expiry(SetOptions.Expiry.Seconds(LAUNCH_CONTEXT_TTL_SECONDS)).build(),
      )
      .await()
  }

  suspend fun getAndDeleteLaunchContext(launchId: String): LaunchContext? =
    glideClient.getdel(launchKey(launchId)).await()?.let { mapper.readValue<LaunchContext>(it) }

  suspend fun setActivePatient(hpr: String, patientId: String) {
    glideClient
      .set(
        activePatientKey(hpr),
        patientId,
        SetOptions.builder().expiry(SetOptions.Expiry.Seconds(ACTIVE_PATIENT_TTL_SECONDS)).build(),
      )
      .await()
  }

  suspend fun getActivePatient(hpr: String): String? =
    glideClient.get(activePatientKey(hpr)).await()

  suspend fun saveAuthCode(code: String, authCode: AuthCodeContext) {
    glideClient
      .set(
        authCodeKey(code),
        mapper.writeValueAsString(authCode),
        SetOptions.builder().expiry(SetOptions.Expiry.Seconds(AUTH_CODE_TTL_SECONDS)).build(),
      )
      .await()
  }

  suspend fun getAndDeleteAuthCode(code: String): AuthCodeContext? =
    glideClient.getdel(authCodeKey(code)).await()?.let { mapper.readValue<AuthCodeContext>(it) }

  suspend fun get(key: String): String? = glideClient.get(key).await()

  suspend fun setIfAbsent(key: String, value: String, ttlSeconds: Long): Boolean =
    glideClient
      .set(
        key,
        value,
        SetOptions.builder()
          .conditionalSet(SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST)
          .expiry(SetOptions.Expiry.Seconds(ttlSeconds))
          .build(),
      )
      .await() != null
}

private fun authCodeKey(code: String) = "smart:code:$code"

private fun launchKey(launchId: String) = "smart:launch:$launchId"

private fun activePatientKey(hpr: String) = "smart:active-patient:$hpr"
