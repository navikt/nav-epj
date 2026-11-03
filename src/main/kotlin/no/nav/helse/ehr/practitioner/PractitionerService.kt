package no.nav.helse.ehr.practitioner

import no.nav.helse.core.utils.PractitionerNotFoundException
import no.nav.helse.core.utils.logger

class PractitionerService(val practitionerRepository: PractitionerRepository) {
    val log = logger()

    suspend fun insertPractitioner(practitioner: CreatePractitioner): Boolean {
        val insertPractitioner = practitionerRepository.insert(practitioner)
        log.info("inserted count: ${insertPractitioner.insertedCount}")
        return (insertPractitioner.insertedCount == 1)
    }

    suspend fun getPractitioner(hpr: PractitionerHpr): Practitioner {
        val practitioner =
            practitionerRepository.findByHpr(hpr) ?: throw PractitionerNotFoundException(hpr)
        return practitioner
    }

    suspend fun findOrCreatePractitioner(hpr: PractitionerHpr, name: String): Practitioner {
        val practitioner = practitionerRepository.findByHpr(hpr)

        if (practitioner == null) {
            log.info("Fant ikke helsepersonell for hpr $hpr, oppretter ny")
            val createPractitioner =
                CreatePractitioner(
                    hpr = hpr,
                    name = name,
                    qualification = "Lege", // TODO hent fra UserInfo
                )
            insertPractitioner(createPractitioner)
            return getPractitioner(hpr)
        }
        return practitioner
    }
}
