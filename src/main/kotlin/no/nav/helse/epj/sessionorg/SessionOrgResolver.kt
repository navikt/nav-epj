package no.nav.helse.epj.sessionorg

import no.nav.helse.core.utils.SessionOrgRequiredException
import no.nav.helse.epj.clinic.Clinic
import no.nav.helse.epj.clinic.ClinicRepository
import no.nav.helse.smart.valkey.ValkeyService

sealed interface SessionOrgResolution {
    data class Resolved(val org: Clinic, val derived: Boolean) : SessionOrgResolution

    data object NeedsChoice : SessionOrgResolution
}

data class ChoosableOrg(val org: Clinic, val hasOwnList: Boolean)

interface SessionOrgChoiceStore {
    suspend fun get(hpr: String): String?

    suspend fun set(hpr: String, orgNumber: String)

    suspend fun extend(hpr: String)
}

class ValkeySessionOrgChoiceStore(private val valkeyService: ValkeyService) :
    SessionOrgChoiceStore {
    override suspend fun get(hpr: String): String? = valkeyService.getSessionOrg(hpr)

    override suspend fun set(hpr: String, orgNumber: String) =
        valkeyService.setSessionOrg(hpr, orgNumber)

    override suspend fun extend(hpr: String) = valkeyService.refreshSessionOrg(hpr)
}

suspend fun SessionOrgResolver.requireOrgNumber(hpr: String): String =
    when (val resolution = resolve(hpr)) {
        is SessionOrgResolution.Resolved ->
            resolution.org.orgNumber ?: throw SessionOrgRequiredException()
        SessionOrgResolution.NeedsChoice -> throw SessionOrgRequiredException()
    }

class SessionOrgResolver(
    private val fastlegelisteRepository: FastlegelisteRepository,
    private val clinicRepository: ClinicRepository,
    private val choiceStore: SessionOrgChoiceStore,
) {
    suspend fun resolve(hpr: String): SessionOrgResolution {
        val listed = fastlegelisteRepository.findClinicIdsByHpr(hpr)
        listed.singleOrNull()?.let { listedId ->
            clinicRepository.findByClinicId(listedId.value)?.let {
                return SessionOrgResolution.Resolved(it, derived = true)
            }
        }
        val chosen =
            choiceStore.get(hpr)?.let { orgNumber ->
                clinicRepository.listChoosable().firstOrNull { it.orgNumber == orgNumber }
            }
        if (chosen == null) return SessionOrgResolution.NeedsChoice
        choiceStore.extend(hpr)
        return SessionOrgResolution.Resolved(chosen, derived = false)
    }

    suspend fun choosableOrgs(hpr: String): List<ChoosableOrg> {
        val listed = fastlegelisteRepository.findClinicIdsByHpr(hpr).toSet()
        return clinicRepository.listChoosable().map { ChoosableOrg(it, it.id in listed) }
    }
}
