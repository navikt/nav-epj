package no.nav.helse.fhir.condition

import no.nav.tsm.diagnoser.Diagnose

data class DiagnoseWithOid(val diagnose: Diagnose, val oid: String)
