package no.nav.helse.epj.pasient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.SessionOrgRequiredException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.sessionorg.SessionOrgResolution
import no.nav.helse.epj.sessionorg.SessionOrgResolver
import no.nav.helse.smart.valkey.ActivePatient
import no.nav.helse.smart.valkey.ValkeyService

@OptIn(ExperimentalUuidApi::class)
class ActivePatientService(
    private val pasientService: PasientService,
    private val valkeyService: ValkeyService,
    private val sessionOrgResolver: SessionOrgResolver,
) {
    suspend fun getActivePatient(hpr: String): ActivePatient? {
        val orgnummer = resolveOrgnummer(hpr) ?: return null
        return valkeyService.getActivePatientWithExpiry(hpr, orgnummer)
    }

    suspend fun claimActivePatient(hpr: String, patientId: Uuid): ActivePatient? {
        val orgnummer = resolveOrgnummer(hpr) ?: throw SessionOrgRequiredException()
        val pasient = pasientService.getPasientById(PasientId(patientId)) ?: return null
        if (HelsepersonellHpr(hpr) !in pasient.hprNumbers) return null
        valkeyService.setActivePatient(hpr, orgnummer, patientId.toString())
        return checkNotNull(valkeyService.getActivePatientWithExpiry(hpr, orgnummer)) {
            "Active patient missing right after it was set"
        }
    }

    private suspend fun resolveOrgnummer(hpr: String): String? =
        (sessionOrgResolver.resolve(hpr) as? SessionOrgResolution.Resolved)?.org?.orgnummer
}
