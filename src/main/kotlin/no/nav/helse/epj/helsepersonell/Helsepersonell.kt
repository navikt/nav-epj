package no.nav.helse.epj.helsepersonell

import no.nav.helse.epj.legekontor.LegekontorId

@JvmInline value class HelsepersonellHpr(val value: String)

data class Helsepersonell(
  val hpr: HelsepersonellHpr,
  val legekontorId: LegekontorId,
  val navn: String,
  val autorisasjon: String,
)

data class OpprettHelsepersonell(
  val hpr: HelsepersonellHpr,
  val legekontorId: LegekontorId,
  val navn: String,
  val autorisasjon: String,
)
