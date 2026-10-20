package no.nav.helse.core.db

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.select

fun konsultasjonAtOrg(orgnummer: String?): Op<Boolean> =
    if (orgnummer == null) {
        Op.TRUE
    } else {
        KonsultasjonTable.legekontorId inSubQuery
            LegekontorTable.select(LegekontorTable.id).where {
                LegekontorTable.orgnummer eq orgnummer
            }
    }

fun konsultasjonIdsAtOrg(orgnummer: String?) =
    KonsultasjonTable.select(KonsultasjonTable.id).where { konsultasjonAtOrg(orgnummer) }
