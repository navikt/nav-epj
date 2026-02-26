package no.nav.tsm.frontend.wonderwall

import io.ktor.server.auth.jwt.JWTPrincipal

data class User(
    val hpr: String,
)

data class HelseIdPrincipal(val user: User, val accessToken: JWTPrincipal)
