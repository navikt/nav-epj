package no.nav.helse

import io.ktor.server.application.*
import no.nav.helse.auth.configureSecurity
import no.nav.helse.auth.stub.configureOidcStub
import no.nav.helse.core.Runtime
import no.nav.helse.core.initializeEnvironment
import no.nav.helse.fhir.configureFhirRouting
import no.nav.helse.plugins.configureDependencies
import no.nav.helse.plugins.configureSerialization
import no.nav.helse.plugins.configureSession

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configureDependencies()
    configureSerialization()
    configureSession()
    configureSecurity()
    configureRouting()
    configureFhirRouting()

    if (initializeEnvironment(environment.config).runtime == Runtime.LOCAL) {
        configureOidcStub()
    }
}
