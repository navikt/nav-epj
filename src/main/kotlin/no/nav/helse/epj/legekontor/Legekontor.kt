package no.nav.helse.epj.legekontor

import kotlin.uuid.Uuid

@JvmInline value class LegekontorId(val value: Uuid)

data class Legekontor(
  val id: LegekontorId,
  val navn: String,
  val orgnummer: String?,
  val tlf: String?,
) {
  companion object {
    val DEFAULT =
      Legekontor(
        id = LegekontorId(Uuid.parse("a1000000-0000-0000-0000-000000000001")),
        navn = "Legekontoret",
        orgnummer = null,
        tlf = null,
      )
  }
}
