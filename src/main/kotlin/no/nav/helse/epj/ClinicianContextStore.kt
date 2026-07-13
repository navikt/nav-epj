package no.nav.helse.epj

import java.util.concurrent.ConcurrentHashMap

data class ClinicianContext(val patientId: String)

/**
 * Holds the patient a clinician currently has open in the EHR. Written when a konsultasjon is
 * started and read during SMART launch, so the patient id never has to travel in a URL. Not
 * single-use: the context persists while the clinician works with the patient.
 */
interface ClinicianContextStore {
  suspend fun set(clinicianId: String, ctx: ClinicianContext)

  suspend fun get(clinicianId: String): ClinicianContext?
}

class InMemoryClinicianContextStore : ClinicianContextStore {
  private val map = ConcurrentHashMap<String, ClinicianContext>()

  override suspend fun set(clinicianId: String, ctx: ClinicianContext) {
    map[clinicianId] = ctx
  }

  override suspend fun get(clinicianId: String): ClinicianContext? = map[clinicianId]
}
