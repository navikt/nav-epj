package no.nav.helse.epj.pasient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.SessionOrgRequiredException
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
        val orgNumber = resolveOrgNumber(hpr) ?: return null
        return valkeyService.getActivePatientWithExpiry(hpr, orgNumber)
    }

    suspend fun claimActivePatient(hpr: String, patientId: Uuid): ActivePatient? {
        val orgNumber = resolveOrgNumber(hpr) ?: throw SessionOrgRequiredException()
        if (!pasientService.isListedAt(PasientId(patientId), orgNumber)) return null
        valkeyService.setActivePatient(hpr, orgNumber, patientId.toString())
        return checkNotNull(valkeyService.getActivePatientWithExpiry(hpr, orgNumber)) {
            "Active patient missing right after it was set"
        }
    }

    private suspend fun resolveOrgNumber(hpr: String): String? =
        (sessionOrgResolver.resolve(hpr) as? SessionOrgResolution.Resolved)?.org?.orgNumber
}
