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
        id = LegekontorId(Uuid.parse("aed5c75c-3b12-4652-83d7-223bdd69062d")),
        navn = "Legekontoret",
        orgnummer = null,
        tlf = null,
      )
  }
}
