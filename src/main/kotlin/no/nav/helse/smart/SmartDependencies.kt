package no.nav.helse.smart

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.helse.fhir.FhirService
import no.nav.helse.smart.db.AuthCodeContext
import no.nav.helse.smart.db.InMemorySingleUseStore
import no.nav.helse.smart.db.LaunchContext
import no.nav.helse.smart.db.SingleUseStore

/**
 * Registers the stateful dependencies [no.nav.helse.smart.api.configureSmartRouting] needs: the
 * [FhirService] client and the two [SingleUseStore]s that pass one-time state between routes
 * (`/fhir/launch` to `/oidc/authorize`; `/oidc/authorize` to `/oidc/token`).
 */
fun Application.configureSmartDependencies() {
  dependencies {
    provide(FhirService::class)
    // Both stores are in-memory: a restart (or the wrong pod, if replicated) loses in-flight
    // launches/codes. Fine for single-instance/local dev; move to Valkey before running replicas.
    provide<SingleUseStore<LaunchContext>> { InMemorySingleUseStore() } // TODO replace with valkey
    provide<SingleUseStore<AuthCodeContext>> {
      InMemorySingleUseStore()
    } // TODO replace with valkey
  }
}
