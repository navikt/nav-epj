package no.nav.helse.fhir.documentreference

import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId

@Serializable @JvmInline value class DocumentReferenceId(val value: Uuid)

@Serializable
data class CreateJournalnotat(
  val id: DocumentReferenceId? = null,
  val konsultasjonId: EncounterId? = null,
  val pasientId: PatientInputId? = null,
  val journalnotat: String?,
)
