package no.nav.helse.epj.pasient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.PasientCreationException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr

class PasientService(private val pasientRepository: PasientRepository) {
    suspend fun listAtLegekontor(
        orgnummer: String,
        ownHpr: HelsepersonellHpr,
    ): List<PasientListeElement> {
        return pasientRepository.listAtLegekontor(orgnummer, ownHpr)
    }

    suspend fun isListedAt(id: PasientId, orgnummer: String): Boolean {
        return pasientRepository.isListedAt(id.value, orgnummer)
    }

    suspend fun hasKonsultasjonAt(id: PasientId, orgnummer: String): Boolean {
        return pasientRepository.hasKonsultasjonAt(id.value, orgnummer)
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
        orgnummer: String,
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
        pasientRepository.insertOnList(newPasient, HelsepersonellHpr(hpr), orgnummer)
        return pasientRepository.findByPersonident(request.personident)
            ?: throw PasientCreationException()
    }
}
