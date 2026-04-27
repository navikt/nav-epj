package no.nav.helse.fhir.condition

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Uri
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.helse.fhir.condition.repository.StubConditionRepository
import no.nav.helse.fhir.fhirJson
import no.nav.helse.fhir.isAuthenticated
import no.nav.helse.fhir.respondFhir

fun Route.configureConditionRouting(){
  val conditionService = ConditionService(StubConditionRepository())
  get("/Condition/{id}") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val id = call.parameters["id"]
      ?: return@get call.respondText("Missing condition id", status = HttpStatusCode.BadRequest)
    val condition = conditionService.getCondition(id)
    if (condition != null) {
      call.respondFhir(condition)
    } else {
      call.respondText("Condition not found", status = HttpStatusCode.NotFound)
    }
  }

  get("/Condition") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val conditions = conditionService.getAllConditions()
    val bundle = Bundle(
      type = Enumeration(value = Bundle.BundleType.Searchset),
      entry = conditions.map { condition ->
        Bundle.Entry(
          fullUrl = Uri(value = "Condition/${condition.id}"),
          resource = condition
        )
      }
    )
    call.respondFhir(bundle)
  }

  get("/Condition/Patient/{id}") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val id = call.parameters["id"]
      ?: return@get call.respondText("Missing patient id", status = HttpStatusCode.BadRequest)
    val conditions = conditionService.getConditionsForPatient(id)
    val bundle = Bundle(
      type = Enumeration(value = Bundle.BundleType.Searchset),
      entry = conditions.map { condition ->
        Bundle.Entry(
          fullUrl = Uri(value = "Condition/Patient/${id}"),
          resource = condition
        )
      }
    )
    call.respondFhir(bundle)
  }

  get("/Condition/Encounter/{id}") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@get
    }
    val id = call.parameters["id"]
      ?: return@get call.respondText("Missing encounter id", status = HttpStatusCode.BadRequest)
    val conditions = conditionService.getConditionsForEncounter(id)
    val bundle = Bundle(
      type = Enumeration(value = Bundle.BundleType.Searchset),
      entry = conditions.map { condition ->
        Bundle.Entry(
          fullUrl = Uri(value = "Condition/Encounter/${id}"),
          resource = condition
        )
      }
    )
    call.respondFhir(bundle)
  }

  post("/Condition") {
    if (!call.isAuthenticated()) {
      call.respondRedirect("/login")
      return@post
    }
    val body = call.receiveText()
    val condition = fhirJson.decodeFromString(body) as Condition
    val created = conditionService.createCondition(condition)
    call.respondFhir(created)
  }
}
