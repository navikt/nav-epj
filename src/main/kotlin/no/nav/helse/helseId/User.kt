package no.nav.helse.helseId

import io.ktor.server.auth.*
import io.ktor.server.routing.*

data class User(val name: String, val hpr: String)

data class HelseIdPrincipal(val user: User, val debug: DebugInfo)

data class DebugInfo(val accessToken: String, val idToken: String)

fun RoutingContext.loggedInUser(): User {
  val principal =
    requireNotNull(this.call.principal<HelseIdPrincipal>()) { "User not found in principal" }

  return principal.user
}
