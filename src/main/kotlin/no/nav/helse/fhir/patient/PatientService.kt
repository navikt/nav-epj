package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Uri
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientService

class PatientService(val epjPatientService: PasientService) {

  val log = logger()

  @WithSpan
  suspend fun getPatient(patientInputId: PatientInputId): Patient? {
    val span = Span.current()
    span.setAttribute("patientId", patientInputId.value.toString())
    val epjPatient = epjPatientService.getPasientById(PasientId(patientInputId.value))
    return epjPatient?.toPatient()
  }

  fun Pasient.toPatient(): Patient {
    return Patient(
      meta =
        Meta(
          profile =
            listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
        ),
      id = this.id.value.toString(),
      identifier =
        listOf(
          Identifier(
            system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
            value = com.google.fhir.model.r4.String(value = this.fnr),
          )
        ),
      name =
        listOf(
          HumanName(
            family = com.google.fhir.model.r4.String(value = this.etternavn),
            given = listOf(com.google.fhir.model.r4.String(value = this.fornavn)),
          )
        ),
    )
  }
}
