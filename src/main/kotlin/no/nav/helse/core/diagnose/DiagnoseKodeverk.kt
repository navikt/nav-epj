package no.nav.helse.core.diagnose

import no.nav.helse.epj.api.DiagnoseSystem
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType

fun DiagnoseSystem.toDiagnoseType(): DiagnoseType =
  when (this) {
    DiagnoseSystem.ICPC2 -> DiagnoseType.ICPC2
    DiagnoseSystem.ICD10 -> DiagnoseType.ICD10
  }

fun lookupDiagnose(system: DiagnoseSystem, kode: String): Diagnose? =
  Diagnose.from(system.toDiagnoseType(), kode)
