package no.nav.helse.smart.valkey

import glide.api.GlideClient
import glide.api.models.commands.SetOptions
import kotlinx.coroutines.future.await
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class ValkeyService(private val glideClient: GlideClient) {
  private val mapper = jacksonObjectMapper()

  suspend fun saveLaunchContext(key: String, context: LaunchContext) {
    glideClient.set(key, mapper.writeValueAsString(context)).await()
  }

  suspend fun getLaunchContext(key: String): LaunchContext? =
    glideClient.get(key).await()?.let { mapper.readValue<LaunchContext>(it) }

  suspend fun set(key: String, value: String) {
    glideClient.set(key, value).await()
  }

  suspend fun get(key: String): String? = glideClient.get(key).await()

  suspend fun saveAuthCode(key: String, authCode: AuthCodeContext) {
    glideClient.set(key, mapper.writeValueAsString(authCode)).await()
  }

  suspend fun getAuthCode(key: String): AuthCodeContext? =
    glideClient.get(key).await()?.let { mapper.readValue<AuthCodeContext>(it) }

  suspend fun getAndDeleteAuthCode(key: String): AuthCodeContext? {
    val authCode = getAuthCode(key)
    if (authCode != null) {
      glideClient.del(arrayOf(key)).await()
    }
    return authCode
  }

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
