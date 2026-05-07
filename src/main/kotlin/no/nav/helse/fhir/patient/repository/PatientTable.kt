package no.nav.helse.fhir.patient.repository

import com.google.fhir.model.r4.Patient
import no.nav.helse.fhir.fhirResource
import org.jetbrains.exposed.v1.core.Table

object PatientTable : Table("patient") {
  val id = text("id")
  val data = fhirResource<Patient>("data")
}
