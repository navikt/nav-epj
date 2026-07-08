package no.nav.helse.fhir

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String
import com.google.fhir.model.r4.Uri
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.Pasient

fun Konsultasjon.toEncounter(): Encounter {
  val status =
    when (this.status) {
      "pågående" -> Encounter.EncounterStatus.In_Progress
      "fullført" -> Encounter.EncounterStatus.Finished
      else -> Encounter.EncounterStatus.In_Progress
    }

  return Encounter(
    id = this.id,
    subject = Reference(reference = String(value = "Patient/${this.pasientId}")),
    participant =
      this.hpr.map {
        Encounter.Participant(id = String(value = "Practitioner/${it}").toString())
      },
    status = Enumeration(value = status),
    `class` =
      Coding(
        code = Code(value = "AMB"),
        system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
      ),
  )
}

fun Pasient.toFhirPatient(): Patient {
  return Patient(
    meta =
      Meta(
        profile =
          listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"))
      ),
    id = this.id,
    identifier =
      listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.1"),
          value = String(value = "fødselsnummer"),
        )
      ),
    name =
      listOf(
        HumanName(family = String(value = this.navn), given = listOf(String(value = this.navn)))
      ),
  )
}

fun Helsepersonell.toPractitioner(): Practitioner {
  return Practitioner(
    id = this.id,
    meta =
      Meta(
        profile =
          listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"))
      ),
    identifier =
      listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
          value = String(value = this.hpr),
        ),
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.2"),
          value = String(value = this.herId),
        ),
      ),
  )
}
