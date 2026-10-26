package no.nav.helse.epj.pasient

import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.KonsultasjonStatus

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

data class SisteKonsultasjon(val status: KonsultasjonStatus, val tidspunkt: LocalDateTime)

data class PasientListeElement(
    @JsonUnwrapped val pasient: Pasient,
    val selectable: Boolean,
    val sisteKonsultasjon: SisteKonsultasjon? = null,
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

data class OpprettPasientRequest(
    val fornavn: String,
    val etternavn: String,
    val personident: String,
    val personidentType: PersonidentType,
    val birthDate: LocalDate,
    val gender: AdministrativeGender,
)
