package no.nav.helse.epj.helsepersonell

@JvmInline value class HelsepersonellHpr(val value: String)

data class Helsepersonell(val hpr: HelsepersonellHpr, val navn: String, val autorisasjon: String)

data class OpprettHelsepersonell(
    val hpr: HelsepersonellHpr,
    val navn: String,
    val autorisasjon: String,
)
