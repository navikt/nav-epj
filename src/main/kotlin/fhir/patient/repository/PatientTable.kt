package no.nav.helse.fhir.patient.repository

import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.jsonb

object PatientTable : Table("patient") {
  val id = text("id")
  val meta = jsonb<Meta>("meta", jsonConfig)
  val identifier = jsonb<Array<Identifier>>("identifier", jsonConfig)
  val active = bool("active")
  val name = jsonb<Array<HumanName>>("name", jsonConfig)
  val gender = jsonb<AdministrativeGender>("gender", jsonConfig)
  val birthDate = jsonb<Date>("birth_date", jsonConfig)
}

private val jsonConfig = Json { prettyPrint = true }
