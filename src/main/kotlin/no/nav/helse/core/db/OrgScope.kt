package no.nav.helse.core.db

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.select

fun konsultasjonAtOrg(orgNumber: String?): Op<Boolean> =
    if (orgNumber == null) {
        Op.TRUE
    } else {
        KonsultasjonTable.clinicId inSubQuery
            ClinicTable.select(ClinicTable.id).where { ClinicTable.orgNumber eq orgNumber }
    }

fun konsultasjonIdsAtOrg(orgNumber: String?) =
    KonsultasjonTable.select(KonsultasjonTable.id).where { konsultasjonAtOrg(orgNumber) }
