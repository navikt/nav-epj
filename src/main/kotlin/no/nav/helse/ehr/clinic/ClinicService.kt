package no.nav.helse.ehr.clinic

import no.nav.helse.core.utils.ClinicNotFoundException

class ClinicService(val clinicRepository: ClinicRepository) {

    suspend fun getClinic(clinicId: ClinicId, orgNumber: String?): Clinic {
        return clinicRepository.findByClinicId(clinicId.value)?.takeIf {
            orgNumber == null || it.orgNumber == orgNumber
        } ?: throw ClinicNotFoundException()
    }
}
