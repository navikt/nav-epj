package no.nav.helse.ehr.patient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.SessionOrgRequiredException
import no.nav.helse.ehr.sessionorg.SessionOrgResolution
import no.nav.helse.ehr.sessionorg.SessionOrgResolver
import no.nav.helse.smart.valkey.ActivePatient
import no.nav.helse.smart.valkey.ValkeyService

@OptIn(ExperimentalUuidApi::class)
class ActivePatientService(
    private val patientService: PatientService,
    private val valkeyService: ValkeyService,
    private val sessionOrgResolver: SessionOrgResolver,
) {
    suspend fun getActivePatient(hpr: String): ActivePatient? {
        val orgNumber = resolveOrgNumber(hpr) ?: return null
        return valkeyService.getActivePatientWithExpiry(hpr, orgNumber)
    }

    suspend fun claimActivePatient(hpr: String, patientId: Uuid): ActivePatient? {
        val orgNumber = resolveOrgNumber(hpr) ?: throw SessionOrgRequiredException()
        if (!patientService.isListedAt(PatientId(patientId), orgNumber)) return null
        valkeyService.setActivePatient(hpr, orgNumber, patientId.toString())
        return checkNotNull(valkeyService.getActivePatientWithExpiry(hpr, orgNumber)) {
            "Active patient missing right after it was set"
        }
    }

    private suspend fun resolveOrgNumber(hpr: String): String? =
        (sessionOrgResolver.resolve(hpr) as? SessionOrgResolution.Resolved)?.org?.orgNumber
}
