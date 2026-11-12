package no.nav.helse.fhir

import com.google.fhir.model.r4.Resource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.fhir.security.requireScope
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.SmartPrincipal

fun Route.fhirResourceRoutes(
    handler: FhirResourceHandler,
    codec: FhirCodec,
    fhirContentType: ContentType,
) {
    suspend fun ApplicationCall.respondFhir(result: FhirResult) {
        result.location?.let { response.header(HttpHeaders.Location, it) }
        val resource = result.resource ?: return respond(result.status)
        respondText(codec.encode(resource), fhirContentType, result.status)
    }

    suspend fun ApplicationCall.receiveResource(): Resource = codec.decode(receiveBoundedText())

    route("/fhir/${handler.resourceType}") {
        get("/{id}") {
            call.respondFhir(handler.read(call.smartPrincipal(), call.uuidParameter("id")))
        }
        get { call.respondFhir(handler.search(call.smartPrincipal(), call.parameters)) }
        post {
            val principal =
                call.smartPrincipal().requireScope(handler.resourceType, Interaction.CREATE)
            call.respondFhir(handler.create(principal, call.receiveResource()))
        }
        put("/{id}") {
            val principal =
                call.smartPrincipal().also { it.requireAnyWriteScope(handler.resourceType) }
            val id = call.uuidParameter("id")
            call.respondFhir(handler.update(principal, id, call.receiveResource()))
        }
        delete("/{id}") {
            call.respondFhir(handler.delete(call.smartPrincipal(), call.uuidParameter("id")))
        }
    }
}

private fun ApplicationCall.smartPrincipal(): SmartPrincipal = principal<SmartPrincipal>()!!
