package no.nav.helse.fhir

import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.ContactPoint
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import no.nav.helse.epj.api.Diagnose
import no.nav.helse.epj.api.Helsepersonell
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.KonsultasjonStatus
import no.nav.helse.epj.api.Legekontor
import no.nav.helse.epj.api.Pasient

fun Konsultasjon.toEncounter(): Encounter {
  val status =
    when (this.status) {
      KonsultasjonStatus.PLANLAGT -> Encounter.EncounterStatus.Planned
      KonsultasjonStatus.PÅGÅENDE -> Encounter.EncounterStatus.In_Progress
      KonsultasjonStatus.FULLFØRT -> Encounter.EncounterStatus.Finished
      KonsultasjonStatus.AVLYST -> Encounter.EncounterStatus.Cancelled
    }

  return Encounter(
    id = this.id,
    subject =
      Reference(reference = com.google.fhir.model.r4.String(value = "Patient/${this.pasientId}")),
    participant =
      listOf(
        Encounter.Participant(
          individual =
            Reference(reference = com.google.fhir.model.r4.String(value = "Practitioner/$hpr"))
        )
      ),
    diagnosis =
      listOf(
        Encounter.Diagnosis(
          condition =
            Reference(reference = com.google.fhir.model.r4.String(value = "Condition/${this.id}"))
        )
      ),
    serviceProvider =
      Reference(reference = com.google.fhir.model.r4.String(value = "Organization/")),
    status = Enumeration(value = status),
    type =
      listOf(
        CodeableConcept(
          coding =
            listOf(
              Coding(system = Uri("urn:oid:2.16.578.1.12.4.1.1.8432"), code = Code("kontakttype"))
            )
        )
      ),
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
          value = com.google.fhir.model.r4.String(value = this.fnr),
        )
      ),
    name =
      listOf(
        HumanName(
          family = com.google.fhir.model.r4.String(value = this.navn),
          given = listOf(com.google.fhir.model.r4.String(value = this.navn)),
        )
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
          value = com.google.fhir.model.r4.String(value = this.hpr),
        )
      ),
  )
}

// TODO: sender med konsultasjonId som unik Condition ident, dette er jeg usikker på
fun Diagnose.ToCondition(konsultasjonId: String, patientId: String): Condition {
  return Condition(
    id = konsultasjonId,
    subject =
      Reference(reference = com.google.fhir.model.r4.String(value = "Patient/${patientId}")),
    code =
      CodeableConcept(
        coding =
          listOf(
            Coding(
              system = Uri(value = this.system),
              code = Code(value = this.kode),
              display = com.google.fhir.model.r4.String(value = this.beskrivelse),
            )
          )
      ),
  )
}

fun Legekontor.toOrganization(): Organization {
  return Organization(
    id = this.id,
    meta =
      Meta(
        profile =
          listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Organization"))
      ),
    identifier =
      listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.101"),
          value = com.google.fhir.model.r4.String(this.id),
        ),
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.2"),
          value = com.google.fhir.model.r4.String("organisasjonsnummer / HER"),
        ),
      ),
    telecom =
      listOf(
        ContactPoint(
          system = Enumeration(value = ContactPoint.ContactPointSystem.Phone),
          value =
            com.google.fhir.model.r4.String(
              value = this.tlf ?: "+47 tulletlf"
            ), // TODO: tlf til legekontoret er nå null
        )
      ),
  )
}
