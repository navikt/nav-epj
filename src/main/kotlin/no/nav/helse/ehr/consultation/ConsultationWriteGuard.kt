package no.nav.helse.ehr.consultation

import no.nav.helse.core.db.ConsultationPractitioner
import no.nav.helse.core.db.ConsultationTable
import no.nav.helse.core.db.consultationAtOrg
import no.nav.helse.core.utils.AuthorNotOnConsultationException
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.ehr.patient.PatientId
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

fun requireConsultationForWrite(
    consultationId: ConsultationId,
    patientId: PatientId,
    authorHprs: List<String>,
    orgNumber: String?,
) {
    val consultationPatientId = consultationPatientId(consultationId, orgNumber)
    if (consultationPatientId != patientId.value) {
        throw ConsultationBelongsToOtherPatientException(consultationId, patientId)
    }
    requireAuthorsOnConsultation(consultationId, authorHprs)
}

private fun consultationPatientId(consultationId: ConsultationId, orgNumber: String?) =
    ConsultationTable.select(ConsultationTable.patientId)
        .where { (ConsultationTable.id eq consultationId.value) and consultationAtOrg(orgNumber) }
        .singleOrNull()
        ?.get(ConsultationTable.patientId) ?: throw ConsultationNotFoundException(consultationId)

private fun requireAuthorsOnConsultation(consultationId: ConsultationId, authorHprs: List<String>) {
    if (authorHprs.isEmpty()) return
    val consultationHprs =
        ConsultationPractitioner.select(ConsultationPractitioner.hpr)
            .where { ConsultationPractitioner.consultationId eq consultationId.value }
            .map { it[ConsultationPractitioner.hpr] }
    authorHprs
        .firstOrNull { it !in consultationHprs }
        ?.let { throw AuthorNotOnConsultationException(it) }
}
