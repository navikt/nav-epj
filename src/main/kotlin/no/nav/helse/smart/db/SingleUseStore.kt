package no.nav.helse.smart.db

import java.util.concurrent.ConcurrentHashMap

/**
 * One-time-use handle store: [save] an id to value pair, [take] it back exactly once (later calls
 * return `null`). Backs the two non-replayable hand-offs in the launch flow: `/fhir/launch` to
 * `/oidc/authorize` (a [LaunchContext]) and `/oidc/authorize` to `/oidc/token` (an
 * [AuthCodeContext], enforcing single-use authorization codes).
 */
interface SingleUseStore<T> {
  suspend fun save(id: String, value: T)

  suspend fun take(id: String): T?
}

/**
 * In-memory [SingleUseStore]. Not shared across restarts or replicas; see the `TODO replace with
 * valkey` markers in [no.nav.helse.smart.configureSmartDependencies].
 */
class InMemorySingleUseStore<T> : SingleUseStore<T> {
  private val map = ConcurrentHashMap<String, T>()

  override suspend fun save(id: String, value: T) {
    map[id] = value
  }

  override suspend fun take(id: String): T? = map.remove(id)
}
