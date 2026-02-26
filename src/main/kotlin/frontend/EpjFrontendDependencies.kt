package no.nav.tsm.frontend

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import no.nav.tsm.frontend.wonderwall.CloudWonderwall
import no.nav.tsm.frontend.wonderwall.LocalWonderwall
import no.nav.tsm.frontend.wonderwall.Wonderwall

fun Application.configureDependencies() {
    if (developmentMode) {
        dependencies {
            provide<Wonderwall>() { LocalWonderwall() }
        }
    } else {
        dependencies {
            provide<Wonderwall>() { CloudWonderwall() }
        }
    }
}
