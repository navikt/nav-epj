package no.nav.helse.epj.pasient

import java.time.LocalDate
import kotlin.uuid.Uuid
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.LegekontorId

@JvmInline value class PasientId(val value: Uuid)

data class Pasient(
    val id: PasientId,
    val legekontorId: LegekontorId,
    val hprNumbers: List<HelsepersonellHpr>,
    val fornavn: String,
    val etternavn: String,
    val personident: String,
    val personidentType: PersonidentType? = null,
    val birthDate: LocalDate? = null,
    val gender: AdministrativeGender? = null,
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
