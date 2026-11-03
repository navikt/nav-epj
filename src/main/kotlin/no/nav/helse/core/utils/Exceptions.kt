package no.nav.helse.core.utils

import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr

class PractitionerNotFoundException(hpr: PractitionerHpr) :
    RuntimeException("Fant ikke helsepersonell med HPR=${hpr.value}")

class PractitionerForPatientNotFoundException(patient: PatientId) :
    RuntimeException("Fant ikke helsepersonell på pasient med id=${patient.value}")

class PatientCreationException : RuntimeException("Pasient ble ikke opprettet")

class KonsultasjonNotFoundException(konsultasjonId: KonsultasjonId) :
    RuntimeException("Fant ikke konsultasjon med id=${konsultasjonId.value}")

class AktivKonsultasjonNotFoundException(patientId: PatientId) :
    RuntimeException("Fant ingen aktiv konsultasjon for pasient med id=${patientId.value}")

class SessionOrgRequiredException : RuntimeException("Legekontor er ikke valgt for denne økten")

class IngenFastlegelisteException :
    RuntimeException("Legen har ingen fastlegeliste hos dette legekontoret")

class ClinicNotFoundException(orgNumber: String? = null) :
    RuntimeException(
        orgNumber?.let { "Fant ikke Legekontor med orgnummer $it" } ?: "Fant ikke Legekontor"
    )

class KonsultasjonNotFoundForPatientException(patientId: PatientId) :
    RuntimeException("Fant ikke konsultasjon for pasientId=${patientId.value}")

class UgyldigDiagnoseException(kode: String, system: String) :
    RuntimeException("Fant ikke diagnosekode=$kode i kodeverk=$system")

class UgyldigPersonidentException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class DuplicatePatientException : RuntimeException("Pasienten finnes allerede")

class DuplikatMaalingException : RuntimeException("Målingen finnes allerede")

class DuplikatJournalnotatException : RuntimeException("Journalnotatet finnes allerede")

class ConsultationBelongsToOtherPatientException(
    konsultasjonId: KonsultasjonId,
    patientId: PatientId,
) :
    RuntimeException(
        "Konsultasjon med id=${konsultasjonId.value} tilhører ikke pasient med id=${patientId.value}"
    )
