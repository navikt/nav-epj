package no.nav.helse.core.utils

import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.pasient.PatientId

class HelsepersonellNotFoundException(hpr: HelsepersonellHpr) :
  RuntimeException("Fant ikke helsepersonell med HPR=${hpr.value}")

class PasientCreationException : RuntimeException("Pasient ble ikke opprettet")

class KonsultasjonNotFoundException(konsultasjonId: KonsultasjonId) :
  RuntimeException("Fant ikke konsultasjon med id=${konsultasjonId.value}")

class AktivKonsultasjonNotFoundException(pasientId: PatientId) :
  RuntimeException("Fant ingen aktiv konsultasjon for pasient med id=${pasientId.value}")

class LegekontorNotfoundException : RuntimeException("Fant ikke Legekontor")

class KonsultasjonNotFoundForPatientException(pasientId: PatientId) :
  RuntimeException("Fant ikke konsultasjon for pasientId=${pasientId.value}")

class UgyldigDiagnoseException(kode: String, system: String) :
  RuntimeException("Fant ikke diagnosekode=$kode i kodeverk=$system")
