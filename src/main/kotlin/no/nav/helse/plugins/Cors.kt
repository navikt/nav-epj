package no.nav.helse.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.di.*
import java.net.URI
import no.nav.helse.core.Environment
import no.nav.helse.smart.security.SmartClient

internal fun corsOrigins(clients: List<SmartClient>): List<String> =
    clients.flatMap { it.launchUris + it.redirectUris }.map(::originOf).distinct().sorted()

fun Application.configureCors() {
    val env: Environment by dependencies
    install(CORS) {
        allowHost("localhost:5173")
        allowHost("localhost:5174")
        allowHost("localhost:3000")
        allowHost("epj.ekstern.dev.nav.no")
        corsOrigins(env.smart.clients).forEach { origin ->
            val uri = URI(origin)
            allowHost(uri.authority, schemes = listOf(uri.scheme))
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Wonderwall-Id-Token")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
}
