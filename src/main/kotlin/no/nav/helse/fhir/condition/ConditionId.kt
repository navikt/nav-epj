package no.nav.helse.fhir.condition

import java.util.UUID
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.tsm.diagnoser.Diagnose

/**
 * Condition.id is FHIR transport identity, not a database id. It is derived deterministically from
 * the owning consultation and diagnosis code so repeated lookups of the same diagnosis return the
 * same id without persisting one.
 */
fun conditionFhirId(konsultasjonId: KonsultasjonId, diagnose: Diagnose): String =
    UUID.nameUUIDFromBytes(
            "${konsultasjonId.value}|${diagnose.system}|${diagnose.code}".toByteArray()
        )
        .toString()
