package no.nav.helse.epj.legekontor

import kotlin.uuid.Uuid

@JvmInline value class LegekontorId(val value: Uuid)

data class Legekontor(
    val id: LegekontorId,
    val navn: String,
    val orgnummer: String?,
    val tlf: String?,
)
