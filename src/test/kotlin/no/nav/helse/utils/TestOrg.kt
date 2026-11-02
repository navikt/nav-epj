package no.nav.helse.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.ClinicTable
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.jdbc.insertIgnore

const val DEFAULT_ORG_NUMBER = "864425208"
const val OTHER_ORG_NUMBER = "311111124"

@OptIn(ExperimentalUuidApi::class)
suspend fun ensureOrg(orgNumber: String) = dbQuery {
    ClinicTable.insertIgnore {
        it[id] = Uuid.generateV4()
        it[name] = "Org $orgNumber"
        it[phone] = "22000000"
        it[ClinicTable.orgNumber] = orgNumber
    }
}
