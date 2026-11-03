package no.nav.helse.core.utils

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.tsm.diagnoser.DiagnoseType
import no.nav.tsm.diagnoser.ICD10
import no.nav.tsm.diagnoser.ICPC2
import no.nav.tsm.diagnoser.ICPC2B

fun DiagnoseType.oid(): String =
    if (this == DiagnoseType.ICPC2) {
        ICPC2.OID
    } else if (this == DiagnoseType.ICPC2B) {
        ICPC2B.OID
    } else {
        ICD10.OID
    }

enum class ConsultationStatus(@get:JsonValue val label: String) {
    PLANNED("PLANNED"),
    IN_PROGRESS("IN_PROGRESS"),
    FINISHED("FINISHED"),
    CANCELLED("CANCELLED");

    companion object {
        fun fromLabel(label: String): ConsultationStatus = entries.first { it.label == label }
    }
}
