package no.nav.helse.fhir.api

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Uri
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.helse.fhir.FhirService
import no.nav.helse.smart.SmartPrincipal

fun Application.configureFhirRouting() {

  routing {
    authenticate("smart-access-token") {
      val fhirJson = FhirR4Json()
      val fhirContentType = ContentType("application", "fhir+json")
      val fhirService: FhirService by dependencies

      route("/fhir") {
        get("/Patient/{id}") {
          val principal = call.principal<SmartPrincipal>()!!
          val authorizedPatient =
            principal.patient
              ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no patient context")

          val id = call.parameters["id"]!!
          if (id != authorizedPatient) {
            return@get call.respond(HttpStatusCode.NotFound)
          }

          val patient =
            fhirService.getPatient(id) ?: return@get call.respond(HttpStatusCode.NotFound)
          call.respondText(fhirJson.encodeToString(patient), fhirContentType)
        }
        get("/Practitioner/{id}") {
          val hprNummer = call.parameters["id"]!!
          val practitioner =
            fhirService.getPractitioner(hprNummer)
              ?: return@get call.respond(HttpStatusCode.NotFound)
          call.respondText(fhirJson.encodeToString(practitioner), fhirContentType)
        }

        get("/Encounter") {
          val principal = call.principal<SmartPrincipal>()!!
          val authorizedPatient =
            principal.patient
              ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no patient context")

          val patientParam =
            call.request.queryParameters["patient"]
              ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                "Missing 'patient' search parameter",
              )

          if (patientParam != authorizedPatient) {
            return@get call.respond(
              HttpStatusCode.Forbidden,
              "Not permitted to search outside the patient context",
            )
          }

          val bundle = fhirService.searchEncounters(authorizedPatient)
          call.respondText(fhirJson.encodeToString(bundle), fhirContentType)
        }

        get("/Encounter/{id}") {
          val id =
            call.parameters["id"]
              ?: return@get call.respondText(
                "Missing condition id",
                status = HttpStatusCode.BadRequest,
              )
          val principal = call.principal<SmartPrincipal>()!!
          val authorizedPatient =
            principal.patient
              ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no patient context")

          val encounter =
            fhirService.getEncounter(id, authorizedPatient)
              ?: return@get call.respond(HttpStatusCode.NotFound)
          call.respondText(fhirJson.encodeToString(encounter), fhirContentType)
        }

        // TODO: for øyeblikket kun en organization
        get("/Organization") {
          val organization = fhirService.getOrganization()
          val bundle =
            Bundle(
              type = Enumeration(value = Bundle.BundleType.Searchset),
              entry =
                listOf(Bundle.Entry(fullUrl = Uri(value = "Organization"), resource = organization)),
            )
          call.respondText(fhirJson.encodeToString(bundle), fhirContentType)
        }

        get("/Condition/{id}") {
          val id =
            call.parameters["id"]
              ?: return@get call.respondText(
                "Missing condition id",
                status = HttpStatusCode.BadRequest,
              )
          val principal = call.principal<SmartPrincipal>()!!
          val authorizedPatient =
            principal.patient
              ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no patient context")
          val conditions =
            fhirService.getConditions(id, authorizedPatient)
              ?: return@get call.respond(HttpStatusCode.NotFound)
          val bundle =
            Bundle(
              type = Enumeration(value = Bundle.BundleType.Searchset),
              entry =
                conditions.map { condition ->
                  Bundle.Entry(
                    fullUrl = Uri(value = "Condition/${condition.id}"),
                    resource = condition,
                  )
                },
            )
          call.respondText(fhirJson.encodeToString(bundle), fhirContentType)
        }

        get("/Condition") {
          val principal = call.principal<SmartPrincipal>()!!
          val encounter =
            principal.encounter
              ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no encounter context")

          val patient =
            principal.patient
              ?: return@get call.respond(HttpStatusCode.Forbidden, "Token has no patient context")
          val conditions =
            fhirService.getConditions(encounter, patient)
              ?: return@get call.respond(HttpStatusCode.NotFound)
          val bundle =
            Bundle(
              type = Enumeration(value = Bundle.BundleType.Searchset),
              entry =
                conditions.map { condition ->
                  Bundle.Entry(
                    fullUrl = Uri(value = "Condition/${condition.id}"),
                    resource = condition,
                  )
                },
            )
          call.respondText(fhirJson.encodeToString(bundle), fhirContentType)
        }
      }
    }
  }
}
