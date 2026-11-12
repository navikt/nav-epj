package no.nav.helse.ehr.consultation

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.OTHER_ORG_NUMBER
import no.nav.helse.utils.ensureOrg
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class ConsultationRepositoryOrgScopeTest : ConsultationRepositoryTestBase() {
    private val hpr = PractitionerHpr("123")

    private suspend fun startConsultationAt(patientId: PatientId, orgNumber: String) =
        consultationRepository.insert(
            CreateConsultation(
                patientId,
                listOf(hpr),
                LocalDateTime.now(),
                ConsultationStatus.IN_PROGRESS,
                orgNumber,
            )
        )

    @Test
    fun `consultations recorded at one org are invisible at another`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val patientId = createPatient()
        val consultationId = startConsultationAt(patientId, DEFAULT_ORG_NUMBER)

        assertEquals(
            listOf(consultationId),
            consultationRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER).map { it.id },
        )
        assertTrue(consultationRepository.listByPatientId(patientId, OTHER_ORG_NUMBER).isEmpty())
        assertNull(consultationRepository.findActiveByPatientId(patientId, OTHER_ORG_NUMBER))
        assertNull(
            consultationRepository.findActiveByPatientIdAndHpr(patientId, hpr, OTHER_ORG_NUMBER)
        )
        assertNull(consultationRepository.findByConsultationId(consultationId, OTHER_ORG_NUMBER))
    }

    @Test
    fun `a null org is unrestricted`() = runTest {
        val patientId = createPatient()
        val consultationId = startConsultationAt(patientId, DEFAULT_ORG_NUMBER)

        assertEquals(
            consultationId,
            consultationRepository.findByConsultationId(consultationId, null)?.id,
        )
    }

    @Test
    fun `cancel and update at another org change nothing`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val patientId = createPatient()
        val consultationId = startConsultationAt(patientId, DEFAULT_ORG_NUMBER)

        assertEquals(0, consultationRepository.cancel(consultationId, patientId, OTHER_ORG_NUMBER))
        assertEquals(
            ConsultationStatus.IN_PROGRESS,
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)?.status,
        )
    }

    @Test
    fun `clinical notes recorded at one org are invisible at another`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val patientId = createPatient()
        val consultationId = startConsultationAt(patientId, DEFAULT_ORG_NUMBER)
        val id = writeFhirNote(patientId, consultationId, "notat")

        assertEquals(
            "notat",
            consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)?.clinicalNote,
        )
        assertNull(consultationRepository.findClinicalNote(id, OTHER_ORG_NUMBER))
        assertNull(clinicalDocumentRepository.find(id, OTHER_ORG_NUMBER))
        assertTrue(
            clinicalDocumentRepository.list(patientId, null, null, OTHER_ORG_NUMBER).isEmpty()
        )
    }

    @Test
    fun `a clinical note cannot be written to a consultation at another org`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val patientId = createPatient()
        val consultationId = startConsultationAt(patientId, DEFAULT_ORG_NUMBER)

        assertFailsWith<ConsultationNotFoundException> {
            writeFhirNote(patientId, consultationId, "notat", orgNumber = OTHER_ORG_NUMBER)
        }
        assertTrue(clinicalDocumentRepository.list(patientId, null, null, null).isEmpty())
    }
}
