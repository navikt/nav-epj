package no.nav.helse.epj.pasient

import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.LegekontorId

@JvmInline value class PasientId(val value: Uuid)

data class Pasient(
  val id: PasientId,
  val legekontorId: LegekontorId,
  val hprNumbers: List<HelsepersonellHpr>,
  val fornavn: String,
  val etternavn: String,
  val fnr: String,
)

data class OpprettPasientRequest(val fornavn: String, val etternavn: String, val fnr: String)
