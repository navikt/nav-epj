package no.nav.helse.fhir

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Condition
import com.google.fhir.model.r4.Encounter
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.Organization
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Practitioner
import com.google.fhir.model.r4.UnsignedInt
import no.nav.helse.epj.db.HelsepersonellRepository
import no.nav.helse.epj.db.KonsultasjonRepository
import no.nav.helse.epj.db.PasientRepository

class FhirService(
  private val pasientRepository: PasientRepository,
  private val helsepersonellRepository: HelsepersonellRepository,
  private val konsultasjonRepository: KonsultasjonRepository,
) {

  suspend fun getPatient(id: String): Patient? {
    val pasient = pasientRepository.getPasient(id) ?: return null
    return pasient.toFhirPatient()
  }

  suspend fun getPractitioner(hprNummer: String): Practitioner? {
    val helsepersonell = helsepersonellRepository.getHelsepersonell(hprNummer) ?: return null
    return helsepersonell.toPractitioner()
  }

  suspend fun getEncounter(id: String, authorizedPatientId: String): Encounter? {
    val konsultasjon = konsultasjonRepository.getKonsultasjon(id) ?: return null
    if (konsultasjon.pasientId != authorizedPatientId) return null
    return konsultasjon.toEncounter()
  }

  suspend fun getConditions(konsultasjonId: String, patientId: String): List<Condition>? {
    val diagnoser = konsultasjonRepository.getDiagnoser(konsultasjonId) ?: return null
    return diagnoser.map { it.ToCondition(konsultasjonId, patientId) }
  }

  suspend fun getActiveEncounterForPatient(patientId: String): Encounter? =
    konsultasjonRepository.getAktivKonsultasjon(patientId)?.toEncounter()

  suspend fun searchEncounters(patientId: String): Bundle {
    val encounters = konsultasjonRepository.getKonsultasjoner(patientId).map { it.toEncounter() }
    return Bundle(
      type = Enumeration(value = Bundle.BundleType.Searchset),
      total = UnsignedInt(value = encounters.size),
      entry = encounters.map { Bundle.Entry(resource = it) },
    )
  }

  suspend fun getOrganization(): Organization? =
    helsepersonellRepository.getLegekontor()?.toOrganization()
}
