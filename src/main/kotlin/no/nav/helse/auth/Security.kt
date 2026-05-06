package no.nav.helse.auth

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import no.nav.helse.core.Environment

fun Application.configureSecurity() {
    val env: Environment by dependencies

    authentication {
        oauth("local-stub") {
            urlProvider = { env.oauth.callbackUrl }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "local-stub",
                    authorizeUrl = env.oauth.authorizeUrl,
                    accessTokenUrl = env.oauth.accessTokenUrl,
                    requestMethod = HttpMethod.Post,
                    clientId = env.oauth.clientId,
                    clientSecret = env.oauth.clientSecret,
                    defaultScopes = env.oauth.defaultScopes,
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
        call.respondRedirect("/login")
        return null
    }
    return userSession
}

@Serializable data class UserSession(val state: String, val accessToken: String)
