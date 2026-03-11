package no.nav.tsm.frontend

import frontend.user.HelseIdPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.Pebble
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.pebbletemplates.pebble.loader.ClasspathLoader
import frontend.user.loggedInUser
import io.ktor.server.auth.principal
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.application
import no.nav.tsm.frontend.apps.App
import no.nav.tsm.frontend.apps.AppsRepo
import java.util.UUID

fun Application.epjFrontendModule() {
    dependencies {
        provide(AppsRepo::class)
    }

    configurePepple()

    routing {
        staticResources("/static", "static")
        authenticate("wonderwall-helseid") {
            landingPage()
        }
    }
}

fun Route.landingPage() {
    val appsRepo: AppsRepo by application.dependencies

    get("/") {
        val user = loggedInUser()
        val apps: List<App> = appsRepo.getAllApps()

        call.respond(
            PebbleContent("main.html", mapOf("user" to user, "apps" to apps))
        )
    }

    get("/app/{id}") {
        val id = UUID.fromString(call.parameters["id"])
        val app = appsRepo.getAppById(id)
            ?: return@get call.respond(HttpStatusCode.NotFound)

        val launchUrl = "${app.launchUrl}/launch?iss=http://localhost:8080&launch=ABCD"

        call.respond(
            PebbleContent("parts/app-frame.html", mapOf("launchUrl" to launchUrl, "name" to app.name))
        )
    }

    get("/debug-user") {
        val principal = this.call.principal<HelseIdPrincipal>()

        call.respond<HelseIdPrincipal>(principal ?: error("User not found in principal"))
    }
}

fun Application.configurePepple() {
    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates"
        })
    }
}
