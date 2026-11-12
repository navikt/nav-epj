@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir

import com.google.fhir.model.r4.FhirR4Json
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.mockk.mockk
import java.util.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import no.nav.helse.core.Environment
import no.nav.helse.ehr.consultation.ConsultationId
import no.nav.helse.ehr.consultation.ConsultationRepositoryTestBase
import no.nav.helse.ehr.consultation.ConsultationService
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.fhir.condition.ConditionService
import no.nav.helse.fhir.documentreference.DocumentReferenceService
import no.nav.helse.fhir.encounter.EncounterService
import no.nav.helse.fhir.observation.ObservationService
import no.nav.helse.fhir.organization.OrganizationService
import no.nav.helse.fhir.patient.PatientService
import no.nav.helse.fhir.practitioner.PractitionerService
import no.nav.helse.fhir.practitionerrole.PractitionerRoleService
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseRepository
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseService
import no.nav.helse.plugins.configureStatusPages
import no.nav.helse.smart.security.Interaction
import no.nav.helse.smart.security.ScopeContext
import no.nav.helse.smart.security.SmartPrincipal
import no.nav.helse.smart.security.SmartScope
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.simpleTestEnvironment

const val QUESTIONNAIRE = "https://www.nav.no/samarbeidspartner/sykmelding/fhir/R4/Questionnaire/V1"
const val HPR = "123"
const val FHIR_BASE = "http://test/fhir"
val FHIR_JSON = ContentType("application", "fhir+json")

abstract class FhirWriteBackTestSupport : ConsultationRepositoryTestBase() {
    protected val consultationService =
        ConsultationService(consultationRepository, clinicalDocumentRepository)
    protected val documentReferenceService =
        DocumentReferenceService(consultationService, clinicalDocumentRepository)
    protected val questionnaireResponseService =
        QuestionnaireResponseService(QuestionnaireResponseRepository())
    protected val fhirJson = FhirR4Json()

    protected fun decodeWire(body: String) =
        fhirJson.decodeFromString(body.replace("\"NO-nb\"", "\"no-NO\""))

    protected fun scope(
        type: String,
        vararg interactions: Interaction,
        context: ScopeContext = ScopeContext.USER,
    ) = SmartScope.Fhir(context, type, interactions.toSet())

    protected val allWrites =
        setOf(
            scope("DocumentReference", *Interaction.entries.toTypedArray()),
            scope("QuestionnaireResponse", *Interaction.entries.toTypedArray()),
        )

    protected val allWritesForPatient =
        setOf(
            scope(
                "DocumentReference",
                *Interaction.entries.toTypedArray(),
                context = ScopeContext.PATIENT,
            ),
            scope(
                "QuestionnaireResponse",
                *Interaction.entries.toTypedArray(),
                context = ScopeContext.PATIENT,
            ),
        )

    protected fun testApp(
        scopes: Set<SmartScope> = allWrites,
        boundPatient: String? = null,
        orgNumber: String? = DEFAULT_ORG_NUMBER,
        block: suspend HttpClient.() -> Unit,
    ) = testApplication {
        application {
            configureStatusPages()
            dependencies {
                provide<ConditionService> { mockk(relaxed = true) }
                provide<EncounterService> { mockk(relaxed = true) }
                provide<ObservationService> { mockk(relaxed = true) }
                provide<OrganizationService> { mockk(relaxed = true) }
                provide<PatientService> { mockk(relaxed = true) }
                provide<PractitionerService> { mockk(relaxed = true) }
                provide<PractitionerRoleService> { mockk(relaxed = true) }
                provide<DocumentReferenceService> { documentReferenceService }
                provide<QuestionnaireResponseService> { questionnaireResponseService }
                provide<Environment> { simpleTestEnvironment }
            }
            authentication {
                provider("smart-access-token") {
                    authenticate { ctx ->
                        ctx.principal(
                            SmartPrincipal(
                                subject = "test-client",
                                scopes = scopes,
                                patient = boundPatient,
                                encounter = null,
                                orgNumber = orgNumber,
                            )
                        )
                    }
                }
            }
            routing { fhirRoutes() }
        }
        client.block()
    }

    protected fun pdfBytes(size: Int = 32) = "%PDF-1.4".toByteArray() + ByteArray(size - 8)

    protected fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    protected fun documentReference(
        id: String?,
        patientId: PatientId,
        consultationId: ConsultationId,
        data: String? = base64(pdfBytes()),
        related: Uuid? = null,
        title: String? = "Sykmelding æøå.pdf",
        author: String? = HPR,
    ): String {
        val attachment = buildList {
            add("\"contentType\":\"application/pdf\"")
            if (data != null) {
                add("\"language\":\"NO-nb\"")
                add("\"title\":\"$title\"")
                add("\"data\":\"$data\"")
            }
        }
        val fields = buildList {
            add("\"resourceType\":\"DocumentReference\"")
            if (id != null) add("\"id\":\"$id\"")
            add("\"status\":\"current\"")
            add(
                "\"type\":{\"coding\":[{\"system\":\"urn:oid:2.16.578.1.12.4.1.1.9602\"," +
                    "\"code\":\"J01-2\"}],\"text\":\"Sykmelding\"}"
            )
            add("\"subject\":{\"reference\":\"Patient/${patientId.value}\"}")
            if (author != null) add("\"author\":[{\"reference\":\"Practitioner/$author\"}]")
            add("\"description\":\"Sykmelding sendt til Nav\"")
            add("\"content\":[{\"attachment\":{${attachment.joinToString(",")}}}]")
            add(
                "\"context\":{\"encounter\":[{\"reference\":\"Encounter/${consultationId.value}\"}]," +
                    "\"period\":{\"start\":\"2025-01-01\",\"end\":\"2025-01-10\"}" +
                    (related?.let { ",\"related\":[{\"reference\":\"QuestionnaireResponse/$it\"}]" }
                        ?: "") +
                    "}"
            )
        }
        return "{${fields.joinToString(",")}}"
    }

    protected fun questionnaireResponse(
        id: String?,
        patientId: PatientId,
        consultationId: ConsultationId,
        questionnaire: String = QUESTIONNAIRE,
        author: String = "{\"reference\":\"Practitioner/$HPR\"}",
    ) =
        "{\"resourceType\":\"QuestionnaireResponse\"" +
            (id?.let { ",\"id\":\"$it\"" } ?: "") +
            ",\"status\":\"completed\",\"questionnaire\":\"$questionnaire\"" +
            ",\"subject\":{\"reference\":\"Patient/${patientId.value}\"}" +
            ",\"encounter\":{\"reference\":\"Encounter/${consultationId.value}\"}" +
            ",\"author\":$author" +
            ",\"authored\":\"2025-01-01T10:00:00Z\"" +
            ",\"item\":[{\"linkId\":\"1\",\"item\":[{\"linkId\":\"1.1\"," +
            "\"answer\":[{\"valueString\":\"nestet\"}]}]}]}"

    protected suspend fun HttpClient.putJson(path: String, body: String) =
        put(path) {
            contentType(FHIR_JSON)
            setBody(body)
        }

    protected suspend fun HttpClient.postJson(path: String, body: String) =
        post(path) {
            contentType(FHIR_JSON)
            setBody(body)
        }

    protected fun batch(vararg entries: String, type: String = "batch") =
        "{\"resourceType\":\"Bundle\",\"type\":\"$type\",\"entry\":[${entries.joinToString(",")}]}"

    protected fun entry(method: String, url: String, resource: String? = null) =
        "{" +
            (resource?.let { "\"resource\":$it," } ?: "") +
            "\"request\":{\"method\":\"$method\",\"url\":\"$url\"}}"

    protected suspend fun consultationFor(): Pair<PatientId, ConsultationId> {
        val patientId = createPatient()
        return patientId to createConsultation(patientId)
    }

    protected fun runBlockingFixture(): Pair<PatientId, ConsultationId> =
        kotlinx.coroutines.runBlocking { consultationFor() }
}
