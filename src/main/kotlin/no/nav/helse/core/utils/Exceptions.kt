package no.nav.helse.core.utils

import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr

class PractitionerNotFoundException(hpr: PractitionerHpr) :
    RuntimeException("Fant ikke helsepersonell med HPR=${hpr.value}")

class PractitionerForPatientNotFoundException(patient: PatientId) :
    RuntimeException("Fant ikke helsepersonell på pasient med id=${patient.value}")

class PatientCreationException : RuntimeException("Pasient ble ikke opprettet")

class ConsultationNotFoundException(consultationId: ConsultationId) :
    RuntimeException("Fant ikke konsultasjon med id=${consultationId.value}")

class ActiveConsultationNotFoundException(patientId: PatientId) :
    RuntimeException("Fant ingen aktiv konsultasjon for pasient med id=${patientId.value}")

class SessionOrgRequiredException : RuntimeException("Legekontor er ikke valgt for denne økten")

class NoFastlegelisteException :
    RuntimeException("Legen har ingen fastlegeliste hos dette legekontoret")

class ClinicNotFoundException(orgNumber: String? = null) :
    RuntimeException(
        orgNumber?.let { "Fant ikke Legekontor med orgnummer $it" } ?: "Fant ikke Legekontor"
    )

class ConsultationNotFoundForPatientException(patientId: PatientId) :
    RuntimeException("Fant ikke konsultasjon for pasientId=${patientId.value}")

class InvalidDiagnosisException(code: String, system: String) :
    RuntimeException("Fant ikke diagnosekode=$code i kodeverk=$system")

class DuplicateDiagnosisException(code: String, system: String) :
    RuntimeException("Diagnosekode=$code i kodeverk=$system er oppgitt flere ganger")

class InvalidPersonidentException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class DuplicatePatientException : RuntimeException("Pasienten finnes allerede")

class DuplicateMeasurementException : RuntimeException("Målingen finnes allerede")

class DuplicateClinicalNoteException : RuntimeException("Journalnotatet finnes allerede")

class ConsultationBelongsToOtherPatientException(
    consultationId: ConsultationId,
    patientId: PatientId,
) :
    RuntimeException(
        "Konsultasjon med id=${consultationId.value} tilhører ikke pasient med id=${patientId.value}"
    )
