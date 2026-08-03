package no.nav.helse.epj.helsepersonell

import kotlinx.serialization.Serializable
import no.nav.helse.epj.legekontor.LegekontorId

@JvmInline @Serializable value class HelsepersonellHpr(val value: String)

@Serializable
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
