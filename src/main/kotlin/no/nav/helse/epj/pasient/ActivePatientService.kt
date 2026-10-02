package no.nav.helse.epj.pasient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.smart.valkey.ActivePatient
import no.nav.helse.smart.valkey.ValkeyService

@OptIn(ExperimentalUuidApi::class)
class ActivePatientService(
    private val pasientService: PasientService,
    private val valkeyService: ValkeyService,
) {
    suspend fun claimActivePatient(hpr: String, patientId: Uuid): ActivePatient? {
        val pasient = pasientService.getPasientById(PasientId(patientId)) ?: return null
        if (HelsepersonellHpr(hpr) !in pasient.hprNumbers) return null
        valkeyService.setActivePatient(hpr, patientId.toString())
        return checkNotNull(valkeyService.getActivePatientWithExpiry(hpr)) {
            "Active patient missing right after it was set"
        }
    }
}
