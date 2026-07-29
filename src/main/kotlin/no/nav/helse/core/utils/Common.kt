package no.nav.helse.core.utils

import no.nav.tsm.diagnoser.DiagnoseType
import no.nav.tsm.diagnoser.ICD10
import no.nav.tsm.diagnoser.ICPC2
import no.nav.tsm.diagnoser.ICPC2B

fun DiagnoseType.oid(): String =
  if (this == DiagnoseType.ICPC2) {
    ICPC2.OID
  } else if (this == DiagnoseType.ICPC2B) {
    ICPC2B.OID
  } else {
    ICD10.OID
  }

enum class KonsultasjonStatus {
  PLANLAGT,
  PÅGÅENDE,
  FULLFØRT,
  AVLYST,
}
