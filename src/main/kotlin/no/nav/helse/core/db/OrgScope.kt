package no.nav.helse.core.db

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.select

fun consultationAtOrg(orgNumber: String?): Op<Boolean> =
    if (orgNumber == null) {
        Op.TRUE
    } else {
        ConsultationTable.clinicId inSubQuery
            ClinicTable.select(ClinicTable.id).where { ClinicTable.orgNumber eq orgNumber }
    }

fun consultationIdsAtOrg(orgNumber: String?) =
    ConsultationTable.select(ConsultationTable.id).where { consultationAtOrg(orgNumber) }
