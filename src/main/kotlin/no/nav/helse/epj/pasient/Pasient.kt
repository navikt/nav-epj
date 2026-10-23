package no.nav.helse.epj.pasient

import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.time.LocalDate
import kotlin.uuid.Uuid

@JvmInline value class PasientId(val value: Uuid)

data class Pasient(
    val id: PasientId,
    val fornavn: String,
    val etternavn: String,
    val personident: String,
    val personidentType: PersonidentType? = null,
    val birthDate: LocalDate? = null,
    val gender: AdministrativeGender? = null,
)

data class PasientListeElement(@JsonUnwrapped val pasient: Pasient, val selectable: Boolean)

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

data class OpprettPasientRequest(
    val fornavn: String,
    val etternavn: String,
    val personident: String,
    val personidentType: PersonidentType,
    val birthDate: LocalDate,
    val gender: AdministrativeGender,
)
