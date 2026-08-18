package no.nav.helse.epj.pasient

import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.LegekontorId

@JvmInline value class PatientId(val value: Uuid)

data class Pasient(
  val id: PatientId,
  val legekontorId: LegekontorId,
  val hprNumbers: List<HelsepersonellHpr>,
  val navn: String,
  val fnr: String,
)

data class OpprettPasientRequest(val navn: String, val fnr: String)
