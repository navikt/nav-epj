package no.nav.helse.epj.sessionorg

import no.nav.helse.core.utils.logger
import no.nav.helse.smart.valkey.ValkeyService

sealed interface ChooseOrgResult {
    data class Chosen(val org: ChoosableOrg) : ChooseOrgResult

    data object UnknownOrg : ChooseOrgResult

    data object DerivedFromList : ChooseOrgResult
}

class SessionOrgService(
    private val resolver: SessionOrgResolver,
    private val choiceStore: SessionOrgChoiceStore,
    private val valkeyService: ValkeyService,
) {
    private val log = logger()

    suspend fun choose(hpr: String, orgnummer: String): ChooseOrgResult {
        val current = resolver.resolve(hpr)
        if (current is SessionOrgResolution.Resolved && current.derived) {
            return ChooseOrgResult.DerivedFromList
        }
        val chosen =
            resolver.choosableOrgs(hpr).firstOrNull { it.org.orgnummer == orgnummer }
                ?: return ChooseOrgResult.UnknownOrg

        choiceStore.set(hpr, orgnummer)
        valkeyService.clearActivePatient(hpr)
        log.atInfo()
            .setMessage("Session org chosen: hpr={} orgnr={}")
            .addArgument(hpr)
            .addArgument(orgnummer)
            .addKeyValue("hpr", hpr)
            .addKeyValue("orgnr", orgnummer)
            .log()
        return ChooseOrgResult.Chosen(chosen)
    }
}
