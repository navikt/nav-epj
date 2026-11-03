package no.nav.helse.epj.maaling

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.patient.PatientId

class MaalingService(private val maalingRepository: MaalingRepository) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun opprettMaaling(request: OpprettMaalingRequest, orgNumber: String): Maaling {
        val nyMaaling =
            Maaling(
                id = MaalingId(Uuid.generateV4()),
                patientId = request.patientId,
                consultationId = request.consultationId,
                hpr = request.hpr,
                loincKode = request.loincKode,
                loincVisningsnavn = request.loincVisningsnavn,
                verdi = request.verdi,
                enhetKode = request.enhetKode,
                enhetVisningsnavn = request.enhetVisningsnavn,
                effektivTidspunkt = request.effektivTidspunkt,
                status = request.status,
            )

        maalingRepository.insert(nyMaaling, orgNumber)
        return nyMaaling
    }

    suspend fun getMaaling(id: MaalingId, orgNumber: String?): Maaling? {
        return maalingRepository.findById(id, orgNumber)
    }

    suspend fun getMaalingerForPatient(patientId: PatientId, orgNumber: String?): List<Maaling> {
        return maalingRepository.listByPatientId(patientId, orgNumber)
    }

    suspend fun getMaalingerForConsultation(
        consultationId: ConsultationId,
        orgNumber: String?,
    ): List<Maaling> {
        return maalingRepository.listByConsultationId(consultationId, orgNumber)
    }
}
