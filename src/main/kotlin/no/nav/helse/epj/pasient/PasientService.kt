package no.nav.helse.epj.pasient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.PasientCreationException
import no.nav.helse.epj.practitioner.PractitionerHpr

class PasientService(private val pasientRepository: PasientRepository) {
    suspend fun listAtClinic(
        orgNumber: String,
        ownHpr: PractitionerHpr,
    ): List<PasientListeElement> {
        return pasientRepository.listAtClinic(orgNumber, ownHpr)
    }

    suspend fun isListedAt(id: PasientId, orgNumber: String): Boolean {
        return pasientRepository.isListedAt(id.value, orgNumber)
    }

    suspend fun hasKonsultasjonAt(id: PasientId, orgNumber: String): Boolean {
        return pasientRepository.hasKonsultasjonAt(id.value, orgNumber)
    }

    suspend fun getPasientById(id: PasientId): Pasient? {
        return pasientRepository.findById(id.value)
    }

    suspend fun getPasientByPersonident(personident: String): Pasient? {
        return pasientRepository.findByPersonident(personident)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createPasient(
        request: OpprettPasientRequest,
        hpr: String,
        orgNumber: String,
    ): Pasient {
        PersonidentValidator.validate(
            personident = request.personident,
            personidentType = request.personidentType,
            birthDate = request.birthDate,
        )

        val newPasient =
            Pasient(
                id = PasientId(Uuid.generateV4()),
                fornavn = request.fornavn,
                etternavn = request.etternavn,
                personident = request.personident,
                personidentType = request.personidentType,
                birthDate = request.birthDate,
                gender = request.gender,
            )
        pasientRepository.insertOnList(newPasient, PractitionerHpr(hpr), orgNumber)
        return pasientRepository.findByPersonident(request.personident)
            ?: throw PasientCreationException()
    }
}
