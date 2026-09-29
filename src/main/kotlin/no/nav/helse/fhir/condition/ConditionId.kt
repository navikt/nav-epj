package no.nav.helse.fhir.condition

import java.util.UUID
import kotlin.text.Charsets.UTF_8
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.tsm.diagnoser.Diagnose

fun conditionFhirId(konsultasjonId: KonsultasjonId, diagnose: Diagnose): String =
    UUID.nameUUIDFromBytes(
            "${konsultasjonId.value}|${diagnose.system}|${diagnose.code}".toByteArray(UTF_8)
        )
        .toString()
