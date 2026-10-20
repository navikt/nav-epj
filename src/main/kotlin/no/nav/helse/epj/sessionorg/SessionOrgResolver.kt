package no.nav.helse.epj.sessionorg

import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorRepository
import no.nav.helse.smart.valkey.ValkeyService

sealed interface SessionOrgResolution {
    data class Resolved(val org: Legekontor, val derived: Boolean) : SessionOrgResolution

    data object NeedsChoice : SessionOrgResolution
}

data class ChoosableOrg(val org: Legekontor, val hasOwnList: Boolean)

/** Where the org of a session comes from when the HPR's lists do not decide it. */
interface SessionOrgChoiceStore {
    suspend fun get(hpr: String): String?

    suspend fun set(hpr: String, orgnummer: String)
}

class ValkeySessionOrgChoiceStore(private val valkeyService: ValkeyService) :
    SessionOrgChoiceStore {
    override suspend fun get(hpr: String): String? = valkeyService.getSessionOrg(hpr)

    override suspend fun set(hpr: String, orgnummer: String) =
        valkeyService.setSessionOrg(hpr, orgnummer)
}

class SessionOrgResolver(
    private val fastlegelisteRepository: FastlegelisteRepository,
    private val legekontorRepository: LegekontorRepository,
    private val choiceStore: SessionOrgChoiceStore,
) {
    suspend fun resolve(hpr: String): SessionOrgResolution {
        val listed = fastlegelisteRepository.findLegekontorIdsByHpr(hpr)
        listed.singleOrNull()?.let { listedId ->
            legekontorRepository.findByLegekontorId(listedId.value)?.let {
                return SessionOrgResolution.Resolved(it, derived = true)
            }
        }
        val chosen =
            choiceStore.get(hpr)?.let { orgnummer ->
                legekontorRepository.listChoosable().firstOrNull { it.orgnummer == orgnummer }
            }
        return if (chosen != null) SessionOrgResolution.Resolved(chosen, derived = false)
        else SessionOrgResolution.NeedsChoice
    }

    suspend fun choosableOrgs(hpr: String): List<ChoosableOrg> {
        val listed = fastlegelisteRepository.findLegekontorIdsByHpr(hpr).toSet()
        return legekontorRepository.listChoosable().map { ChoosableOrg(it, it.id in listed) }
    }
}
