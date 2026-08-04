package no.nav.helse.epj.pasient

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.LegekontorId

@JvmInline @Serializable value class PatientId(val value: Uuid)

@Serializable
data class Pasient(
  val id: PatientId,
  val legekontorId: LegekontorId,
  val hpr: HelsepersonellHpr,
  val navn: String,
  val fnr: String,
)

@Serializable data class OpprettPasientRequest(val navn: String, val fnr: String)
