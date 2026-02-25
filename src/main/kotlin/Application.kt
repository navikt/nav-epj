package no.nav.tsm

import io.ktor.server.application.*
import no.nav.tsm.plugins.configureFrameworks
import no.nav.tsm.plugins.configureHTTP
import no.nav.tsm.plugins.configureMonitoring
import no.nav.tsm.plugins.configureSerialization

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureFrameworks()
    configureMonitoring()
    configureHTTP()
    configureRouting()
}

