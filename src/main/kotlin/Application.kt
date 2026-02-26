package no.nav.tsm

import io.ktor.server.application.*
import no.nav.tsm.auth.fhirAuthModule
import no.nav.tsm.frontend.epjFrontendModule
import no.nav.tsm.plugins.configureFrameworks
import no.nav.tsm.plugins.configureOpenAPI
import no.nav.tsm.plugins.configureMonitoring
import no.nav.tsm.plugins.configureSecurity
import no.nav.tsm.plugins.configureSerialization

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Global Ktor configuration
    configureSerialization()
    configureSecurity()
    configureFrameworks()
    configureMonitoring()
    configureOpenAPI()

    // Different application modules
    epjFrontendModule()
    fhirAuthModule()
}
