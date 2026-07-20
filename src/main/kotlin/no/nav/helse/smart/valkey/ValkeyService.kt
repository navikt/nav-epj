package no.nav.helse.smart.valkey

import glide.api.GlideClient
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json

class ValkeyService(private val glideClient: GlideClient) {
  suspend fun saveLaunchContext(key: String, context: LaunchContext) {
    glideClient.set(key, Json.encodeToString(context)).await()
  }

  suspend fun getLaunchContext(key: String): LaunchContext? =
    glideClient.get(key).await()?.let { Json.decodeFromString<LaunchContext>(it) }

  suspend fun set(key: String, value: String) {
    glideClient.set(key, value).await()
  }

  suspend fun get(key: String): String? = glideClient.get(key).await()

  suspend fun saveAuthCode(key: String, authCode: AuthCodeContext) {
    glideClient.set(key, Json.encodeToString(authCode)).await()
  }

  suspend fun getAuthCode(key: String): AuthCodeContext? =
    glideClient.get(key).await()?.let { Json.decodeFromString<AuthCodeContext>(it) }

  suspend fun getAndDeleteAuthCode(key: String): AuthCodeContext? {
    val authCode = getAuthCode(key)
    if (authCode != null) {
      glideClient.del(arrayOf(key)).await()
    }
    return authCode
  }
}
