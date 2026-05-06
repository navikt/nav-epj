package no.nav.helse.auth

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

fun Application.configureSecurity() {
    val config = environment.config.config("oauth")

    authentication {
        oauth("local-stub") {
            urlProvider = { config.property("callbackUrl").getString() }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "local-stub",
                    authorizeUrl = config.property("authorizeUrl").getString(),
                    accessTokenUrl = config.property("accessTokenUrl").getString(),
                    requestMethod = HttpMethod.Post,
                    clientId = config.property("clientId").getString(),
                    clientSecret = config.property("clientSecret").getString(),
                    defaultScopes = config.property("defaultScopes").getString().split(","),
                )
            }
            fallback = { cause ->
                if (cause is OAuth2RedirectError) {
                    respondRedirect("/login-after-fallback")
                } else {
                    respond(HttpStatusCode.Forbidden, cause.message)
                }
            }
            client = HttpClient(CIO)
        }
    }
}

suspend fun getSession(call: ApplicationCall): UserSession? {
    val userSession: UserSession? = call.sessions.get()
    if (userSession == null) {
        val redirectUrl =
            URLBuilder("http://localhost:8080/login").run {
                parameters.append("redirectUrl", call.request.uri)
                build()
            }
        call.respondRedirect(redirectUrl)
        return null
    }
    return userSession
}

@Serializable data class UserSession(val state: String, val accessToken: String)
