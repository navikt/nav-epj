package no.nav.helse.fhir.condition

import java.util.UUID
import kotlin.text.Charsets.UTF_8
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.tsm.diagnoser.Diagnose

fun conditionFhirId(consultationId: ConsultationId, diagnosis: Diagnose): String =
    UUID.nameUUIDFromBytes(
            "${consultationId.value}|${diagnosis.system}|${diagnosis.code}".toByteArray(UTF_8)
        )
        .toString()
