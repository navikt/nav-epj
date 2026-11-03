package no.nav.helse.epj.patient

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.utils.PatientCreationException
import no.nav.helse.epj.practitioner.PractitionerHpr

class PatientService(private val patientRepository: PatientRepository) {
    suspend fun listAtClinic(orgNumber: String, ownHpr: PractitionerHpr): List<PatientListItem> {
        return patientRepository.listAtClinic(orgNumber, ownHpr)
    }

    suspend fun isListedAt(id: PatientId, orgNumber: String): Boolean {
        return patientRepository.isListedAt(id.value, orgNumber)
    }

    suspend fun hasKonsultasjonAt(id: PatientId, orgNumber: String): Boolean {
        return patientRepository.hasKonsultasjonAt(id.value, orgNumber)
    }

    suspend fun getPatientById(id: PatientId): Patient? {
        return patientRepository.findById(id.value)
    }

    suspend fun getPatientByPersonident(personident: String): Patient? {
        return patientRepository.findByPersonident(personident)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createPatient(
        request: CreatePatientRequest,
        hpr: String,
        orgNumber: String,
    ): Patient {
        PersonidentValidator.validate(
            personident = request.personident,
            personidentType = request.personidentType,
            birthDate = request.birthDate,
        )

        val newPatient =
            Patient(
                id = PatientId(Uuid.generateV4()),
                givenName = request.givenName,
                familyName = request.familyName,
                personident = request.personident,
                personidentType = request.personidentType,
                birthDate = request.birthDate,
                gender = request.gender,
            )
        patientRepository.insertOnList(newPatient, PractitionerHpr(hpr), orgNumber)
        return patientRepository.findByPersonident(request.personident)
            ?: throw PatientCreationException()
    }
}
