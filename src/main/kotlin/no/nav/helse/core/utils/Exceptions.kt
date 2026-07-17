package no.nav.helse.core.utils

class HelsepersonellNotFoundException(hpr: String) :
  RuntimeException("Fant ikke helsepersonell med HPR=$hpr")

class PasientCreationException : RuntimeException("Pasient ble ikke opprettet")

class KonsultasjonNotFoundException(konsultasjonId: String, pasientId: String) :
  RuntimeException("Fant ikke konsultasjon med id=$konsultasjonId for pasientId=$pasientId")

class UgyldigDiagnoseException(kode: String, system: String) :
  RuntimeException("Fant ikke diagnosekode=$kode i kodeverk=$system")
