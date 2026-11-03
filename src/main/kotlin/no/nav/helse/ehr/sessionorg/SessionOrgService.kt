package no.nav.helse.ehr.sessionorg

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

    suspend fun choose(hpr: String, orgNumber: String): ChooseOrgResult {
        val current = resolver.resolve(hpr)
        if (current is SessionOrgResolution.Resolved && current.derived) {
            return ChooseOrgResult.DerivedFromList
        }
        val chosen =
            resolver.choosableOrgs(hpr).firstOrNull { it.org.orgNumber == orgNumber }
                ?: return ChooseOrgResult.UnknownOrg

        choiceStore.set(hpr, orgNumber)
        (current as? SessionOrgResolution.Resolved)?.org?.orgNumber?.let {
            valkeyService.clearActivePatient(hpr, it)
        }
        valkeyService.clearActivePatient(hpr, orgNumber)
        log.atInfo()
            .setMessage("Session org chosen: hpr={} orgnr={}")
            .addArgument(hpr)
            .addArgument(orgNumber)
            .addKeyValue("hpr", hpr)
            .addKeyValue("orgnr", orgNumber)
            .log()
        return ChooseOrgResult.Chosen(chosen)
    }
}
