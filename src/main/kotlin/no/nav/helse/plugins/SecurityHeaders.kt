package no.nav.helse.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import java.net.URI
import no.nav.helse.core.Environment
import no.nav.helse.smart.security.LaunchMode
import no.nav.helse.smart.security.SmartClient

private const val PERMISSIONS_POLICY =
    "camera=(), microphone=(), geolocation=(), payment=(), usb=(), " +
        "bluetooth=(), serial=(), display-capture=()"

internal class SecurityHeadersConfig {
    var contentSecurityPolicy: String = ""
}

private val SecurityHeaders =
    createApplicationPlugin("SecurityHeaders", ::SecurityHeadersConfig) {
        val csp = pluginConfig.contentSecurityPolicy
        onCall { call ->
            call.response.headers.apply {
                append("Content-Security-Policy", csp)
                append("X-Content-Type-Options", "nosniff")
                append("Referrer-Policy", "no-referrer")
                append("Permissions-Policy", PERMISSIONS_POLICY)
            }
        }
    }

internal fun frameSources(clients: List<SmartClient>): List<String> =
    clients
        .filter { it.launchMode != LaunchMode.TAB }
        .flatMap { it.launchUris + it.redirectUris }
        .map(::originOf)
        .distinct()
        .sorted()

internal fun contentSecurityPolicy(frameSources: List<String>): String =
    "frame-src ${(listOf("'self'") + frameSources).joinToString(" ")}; frame-ancestors 'self'"

private fun originOf(uri: String): String =
    URI(uri).let {
        val host = requireNotNull(it.host) { "No host in SMART client uri $uri" }
        "${it.scheme}://$host${if (it.port == -1) "" else ":${it.port}"}"
    }

fun Application.configureSecurityHeaders() {
    val env: Environment by dependencies
    install(SecurityHeaders) {
        contentSecurityPolicy = contentSecurityPolicy(frameSources(env.smart.clients))
    }
}
