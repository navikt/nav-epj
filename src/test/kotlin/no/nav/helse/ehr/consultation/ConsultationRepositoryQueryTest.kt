package no.nav.helse.ehr.consultation

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.clinic.ClinicRepository
import no.nav.helse.ehr.patient.Patient
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class ConsultationRepositoryQueryTest : ConsultationRepositoryTestBase() {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `finds no consultation`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val consultation = consultationRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER)
        assertEquals(0, consultation.size)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `finds one consultation`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val hpr = PractitionerHpr("123")
        patientRepository.insert(
            Patient(
                id = patientId,
                givenName = "givenName",
                familyName = "familyName",
                personident = "personident",
            )
        )
        consultationRepository.insert(
            CreateConsultation(
                patientId = patientId,
                hpr = listOf(hpr),
                startedAt = LocalDateTime.now(),
                status = ConsultationStatus.IN_PROGRESS,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        val consultation = consultationRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER)
        assertEquals(1, consultation.size)
    }

    @Test
    fun `listByPatientId returns only consultations for the correct patient`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientA = createPatient(hpr = hpr)
        val patientB = createPatient(hpr = hpr)
        consultationRepository.insert(
            CreateConsultation(
                patientA,
                listOf(hpr),
                LocalDateTime.now(),
                ConsultationStatus.IN_PROGRESS,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        consultationRepository.insert(
            CreateConsultation(
                patientB,
                listOf(hpr),
                LocalDateTime.now(),
                ConsultationStatus.IN_PROGRESS,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )

        val consultationsA = consultationRepository.listByPatientId(patientA, DEFAULT_ORG_NUMBER)
        assertEquals(1, consultationsA.size)
        assertEquals(patientA, consultationsA.single().patientId)
    }

    @Test
    fun `listByPatientId sorts consultations descending by startedAt`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        val oldest = LocalDateTime.now().minusHours(2)
        val newest = LocalDateTime.now()
        val oldestId =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    oldest,
                    ConsultationStatus.FINISHED,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )
        val newestId =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    newest,
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val consultations = consultationRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER)
        assertEquals(2, consultations.size)
        assertEquals(newestId, consultations[0].id)
        assertEquals(oldestId, consultations[1].id)
    }

    @Test
    fun `insert stores multiple hpr for a consultation`() = runTest {
        val hprA = PractitionerHpr("123")
        val hprB = PractitionerHpr("456")
        val patientId = createPatient(hpr = hprA)
        consultationRepository.insert(
            CreateConsultation(
                patientId,
                listOf(hprA, hprB),
                LocalDateTime.now(),
                ConsultationStatus.IN_PROGRESS,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )

        val consultation =
            consultationRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER).single()
        assertEquals(setOf("123", "456"), consultation.hpr.toSet())
    }

    @Test
    fun `insert with an empty hpr list gives a consultation without healthcare personnel`() =
        runTest {
            val patientId = createPatient()
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    emptyList(),
                    LocalDateTime.now(),
                    ConsultationStatus.PLANNED,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

            val consultation =
                consultationRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER).single()
            assertTrue(consultation.hpr.isEmpty())
        }

    @Test
    fun `findActiveByPatientId returns null when no consultations exist`() = runTest {
        val patientId = createPatient()
        assertNull(consultationRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `findActiveByPatientId ignores completed consultations`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        val updateConsultationRequest =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    java.time.LocalDateTime.now(),
                    ConsultationStatus.FINISHED,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )
        consultationRepository.update(
            UpdateConsultationRequest(
                updateConsultationRequest,
                emptyList(),
                null,
                finalize = true,
            ),
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        assertNull(consultationRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `findActiveByPatientId returns the newest active consultation`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        consultationRepository.insert(
            CreateConsultation(
                patientId,
                listOf(hpr),
                LocalDateTime.now().minusHours(2),
                ConsultationStatus.IN_PROGRESS,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        val newestId =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val active = consultationRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER)
        assertNotNull(active)
        assertEquals(newestId, active.id)
    }

    @Test
    fun `findActiveByPatientIdAndHpr returns null when the doctor has no active consultation`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val otherHpr = PractitionerHpr("456")
            val patientId = createPatient(hpr = hpr)
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

            assertNull(
                consultationRepository.findActiveByPatientIdAndHpr(
                    patientId,
                    otherHpr,
                    DEFAULT_ORG_NUMBER,
                )
            )
        }

    @Test
    fun `findActiveByPatientIdAndHpr lets two different doctors have simultaneous active consultations for the same patient`() =
        runTest {
            val hprA = PractitionerHpr("123")
            val hprB = PractitionerHpr("456")
            val patientId = createPatient(hpr = hprA)
            val consultationA =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        listOf(hprA),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val consultationB =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        listOf(hprB),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )

            val activeForA =
                consultationRepository.findActiveByPatientIdAndHpr(
                    patientId,
                    hprA,
                    DEFAULT_ORG_NUMBER,
                )
            val activeForB =
                consultationRepository.findActiveByPatientIdAndHpr(
                    patientId,
                    hprB,
                    DEFAULT_ORG_NUMBER,
                )

            assertNotNull(activeForA)
            assertNotNull(activeForB)
            assertEquals(consultationA, activeForA.id)
            assertEquals(consultationB, activeForB.id)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `findByConsultationId returns null for an unknown id`() = runTest {
        assertNull(
            consultationRepository.findByConsultationId(
                ConsultationId(Uuid.generateV4()),
                DEFAULT_ORG_NUMBER,
            )
        )
    }

    @Test
    fun `findByConsultationId returns the consultation for a known id`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        val consultationId =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        assertEquals(consultationId, consultation.id)
    }

    @Test
    fun `insert places the consultation at the clinic with the given org number`() = runTest {
        val patientId = createPatient()
        val consultationId =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    emptyList(),
                    LocalDateTime.now(),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val consultation =
            consultationRepository.findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)
        assertNotNull(consultation)
        assertEquals(
            ClinicRepository().findByOrgNumber(DEFAULT_ORG_NUMBER)?.id,
            consultation.clinicId,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `the database rejects a consultation without an organization`() = runTest {
        val patientId = createPatient()
        val consultationId = Uuid.generateV4()

        assertFailsWith<java.sql.SQLException> {
            java.sql.DriverManager.getConnection(
                    config.postgres.url,
                    config.postgres.username,
                    config.postgres.password,
                )
                .use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            """
                            INSERT INTO consultation (id, patient_id, clinic_id, started_at, status)
                            VALUES ('$consultationId', '${patientId.value}', NULL, now(), 'IN_PROGRESS')
                            """
                                .trimIndent()
                        )
                    }
                }
        }
    }

    @Test
    fun `findActive ignores cancelled and planned consultations without ended at`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        listOf(ConsultationStatus.CANCELLED, ConsultationStatus.PLANNED).forEach {
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now().minusDays(1),
                    it,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )
        }

        assertNull(consultationRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER))
        assertNull(
            consultationRepository.findActiveByPatientIdAndHpr(patientId, hpr, DEFAULT_ORG_NUMBER)
        )

        val inProgressId =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now().minusDays(2),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        assertEquals(
            inProgressId,
            consultationRepository
                .findActiveByPatientIdAndHpr(patientId, hpr, DEFAULT_ORG_NUMBER)
                ?.id,
        )
        assertEquals(
            inProgressId,
            consultationRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER)?.id,
        )
    }
}
