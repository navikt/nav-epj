package no.nav.helse.smart.db

import java.util.concurrent.ConcurrentHashMap

data class LaunchContext(val patientId: String?, val encounterId: String?)

interface LaunchStore {
  suspend fun save(id: String, ctx: LaunchContext)

  // Single use, therefore not take() instead of get()
  suspend fun take(id: String): LaunchContext?
}

class InMemoryLaunchStore : LaunchStore {
  private val map = ConcurrentHashMap<String, LaunchContext>()

  override suspend fun save(id: String, ctx: LaunchContext) {
    map[id] = ctx
  }

  override suspend fun take(id: String): LaunchContext? = map.remove(id)
}
