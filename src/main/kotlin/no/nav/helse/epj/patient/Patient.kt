package no.nav.helse.epj.patient

import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.ConsultationStatus

@JvmInline value class PatientId(val value: Uuid)

data class Patient(
    val id: PatientId,
    val givenName: String,
    val familyName: String,
    val personident: String,
    val personidentType: PersonidentType? = null,
    val birthDate: LocalDate? = null,
    val gender: AdministrativeGender? = null,
)

data class LatestConsultation(val status: ConsultationStatus, val timestamp: LocalDateTime)

data class PatientListItem(
    @JsonUnwrapped val patient: Patient,
    val selectable: Boolean,
    val latestConsultation: LatestConsultation? = null,
)

enum class PersonidentType {
    FNR,
    DNR,
}

enum class AdministrativeGender {
    FEMALE,
    MALE,
    OTHER,
    UNKNOWN,
}

data class CreatePatientRequest(
    val givenName: String,
    val familyName: String,
    val personident: String,
    val personidentType: PersonidentType,
    val birthDate: LocalDate,
    val gender: AdministrativeGender,
)
