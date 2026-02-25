package no.nav.tsm.plugins

import dev.hayden.KHealth
import io.ktor.server.application.*
import io.ktor.server.engine.ShutDownUrl

fun Application.configureMonitoring() {
    install(KHealth) {
        healthCheckPath = "/internal/health/alive"
        readyCheckPath = "/internal/health/ready"
    }
    install(ShutDownUrl.ApplicationCallPlugin) {
        shutDownUrl = "/internal/shutdown"
        exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
    }
}
