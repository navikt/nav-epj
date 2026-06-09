package no.nav.helse.epj

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import no.nav.helse.core.db.Repository

fun Application.configureEpjDependencies() {
  dependencies {
    // TODO: dette må være feil måte å injecte disse på???
    provide(EpjService::class)
    provide(Repository::class)
  }
}
