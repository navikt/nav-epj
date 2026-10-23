package no.nav.helse.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.db.LegekontorTable
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.jdbc.insertIgnore

const val DEFAULT_ORGNUMMER = "864425208"
const val OTHER_ORGNUMMER = "311111124"

@OptIn(ExperimentalUuidApi::class)
suspend fun ensureOrg(orgnummer: String) = dbQuery {
    LegekontorTable.insertIgnore {
        it[id] = Uuid.generateV4()
        it[navn] = "Org $orgnummer"
        it[tlf] = "22000000"
        it[LegekontorTable.orgnummer] = orgnummer
    }
}
