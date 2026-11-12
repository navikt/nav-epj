package no.nav.helse.fhir.questionnaireresponse

import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.QuestionnaireResponse
import io.ktor.http.*
import kotlin.uuid.Uuid
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.fhir.fhirFailure
import no.nav.helse.fhir.parsePractitionerHpr
import no.nav.helse.fhir.parseReferenceId

const val MAX_QUESTIONNAIRE_RESPONSE_BYTES = 1_000_000
private const val OWNER = "QuestionnaireResponse"

fun QuestionnaireResponse.toQuestionnaireResponseWrite(
    fhirJson: FhirR4Json,
    id: Uuid,
): QuestionnaireResponseWrite {
    val patientId = parseReferenceId(subject?.reference?.value, "Patient", OWNER, "subject")
    val consultationId =
        parseReferenceId(encounter?.reference?.value, "Encounter", OWNER, "encounter")
    val authorHprs =
        author
            ?.reference
            ?.value
            ?.takeIf { it.startsWith("Practitioner/") }
            ?.let { listOf(parsePractitionerHpr(it, OWNER)) }
            .orEmpty()

    val json = fhirJson.encodeToString(toBuilder().also { it.id = id.toString() }.build())
    if (json.toByteArray().size > MAX_QUESTIONNAIRE_RESPONSE_BYTES) {
        fhirFailure(
            HttpStatusCode.PayloadTooLarge,
            OperationOutcome.IssueType.Too_Long,
            "$OWNER er større enn $MAX_QUESTIONNAIRE_RESPONSE_BYTES bytes",
        )
    }
    return QuestionnaireResponseWrite(
        patientId = PatientId(patientId),
        consultationId = ConsultationId(consultationId),
        authorHprs = authorHprs,
        resourceJson = json,
    )
}
