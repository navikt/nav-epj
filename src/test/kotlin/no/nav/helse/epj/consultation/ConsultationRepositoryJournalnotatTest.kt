package no.nav.helse.epj.consultation

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class ConsultationRepositoryJournalnotatTest : ConsultationRepositoryTestBase() {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat stores a complete round trip of a journalnotat`() = runTest {
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
        val id = JournalnotatId(Uuid.generateV4())
        val request =
            OpprettJournalnotatRequest(
                patientId = patientId,
                consultationId = consultationId,
                journalnotat = "notat",
            )

        val opprettet = consultationRepository.opprettJournalnotat(id, request, DEFAULT_ORG_NUMBER)

        assertEquals(id, opprettet.id)
        assertEquals(patientId, opprettet.patientId)
        assertEquals(consultationId, opprettet.consultationId)
        assertEquals("notat", opprettet.journalnotat)
        val lagret = consultationRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)
        assertEquals("notat", lagret?.journalnotat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat throws ConsultationNotFoundException for an unknown encounter`() =
        runTest {
            val patientId = createPatient()
            val request =
                OpprettJournalnotatRequest(
                    patientId = patientId,
                    consultationId = ConsultationId(Uuid.generateV4()),
                    journalnotat = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.ConsultationNotFoundException> {
                consultationRepository.opprettJournalnotat(
                    JournalnotatId(Uuid.generateV4()),
                    request,
                    DEFAULT_ORG_NUMBER,
                )
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat throws ConsultationBelongsToOtherPatientException when the encounter belongs to another patient`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val eier = createPatient(hpr = hpr)
            val consultationId =
                consultationRepository.insert(
                    CreateConsultation(
                        eier,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val otherPatient = createPatient(hpr = hpr)
            val request =
                OpprettJournalnotatRequest(
                    patientId = otherPatient,
                    consultationId = consultationId,
                    journalnotat = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException> {
                consultationRepository.opprettJournalnotat(
                    JournalnotatId(Uuid.generateV4()),
                    request,
                    DEFAULT_ORG_NUMBER,
                )
            }
            assertNull(
                consultationRepository.findJournalnotat(
                    JournalnotatId(Uuid.generateV4()),
                    DEFAULT_ORG_NUMBER,
                )
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat with a colliding id throws DuplikatJournalnotatException and leaves data unchanged`() =
        runTest {
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
            val id = JournalnotatId(Uuid.generateV4())
            val request =
                OpprettJournalnotatRequest(
                    patientId = patientId,
                    consultationId = consultationId,
                    journalnotat = "original",
                )
            consultationRepository.opprettJournalnotat(id, request, DEFAULT_ORG_NUMBER)

            assertFailsWith<no.nav.helse.core.utils.DuplikatJournalnotatException> {
                consultationRepository.opprettJournalnotat(
                    id,
                    request.copy(journalnotat = "endret"),
                    DEFAULT_ORG_NUMBER,
                )
            }

            assertEquals(
                "original",
                consultationRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)?.journalnotat,
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat by patientId only returns journalnotater for the correct patient`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientA = createPatient(hpr = hpr)
            val patientB = createPatient(hpr = hpr)
            val consultationA =
                consultationRepository.insert(
                    CreateConsultation(
                        patientA,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val consultationB =
                consultationRepository.insert(
                    CreateConsultation(
                        patientB,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            consultationRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(patientA, consultationA, "for A"),
                DEFAULT_ORG_NUMBER,
            )
            consultationRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(patientB, consultationB, "for B"),
                DEFAULT_ORG_NUMBER,
            )

            val notaterForA =
                consultationRepository.listJournalnotat(patientA, null, DEFAULT_ORG_NUMBER)

            assertEquals(1, notaterForA.size)
            assertEquals("for A", notaterForA.single().journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat filters by encounter when both patient and encounter are given`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientId = createPatient(hpr = hpr)
            val consultationA =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val consultationB =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            consultationRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(patientId, consultationA, "for A"),
                DEFAULT_ORG_NUMBER,
            )
            consultationRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(patientId, consultationB, "for B"),
                DEFAULT_ORG_NUMBER,
            )

            val notater =
                consultationRepository.listJournalnotat(
                    patientId,
                    consultationA,
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(1, notater.size)
            assertEquals("for A", notater.single().journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat returns an empty list when the patient has no journalnotater`() =
        runTest {
            val patientId = createPatient()

            val notater =
                consultationRepository.listJournalnotat(patientId, null, DEFAULT_ORG_NUMBER)

            assertTrue(notater.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat rejects an encounter that belongs to another patient`() = runTest {
        val eier = createPatient()
        val consultationId = createConsultation(eier)
        val otherPatient = createPatient()
        val id = JournalnotatId(Uuid.generateV4())

        assertFailsWith<no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException> {
            consultationRepository.insertJournalnotat(
                Journalnotat(id, consultationId, otherPatient, "notat"),
                DEFAULT_ORG_NUMBER,
            )
        }
        assertNull(consultationRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat does not move an existing journalnotat to another consultation`() =
        runTest {
            val eier = createPatient()
            val ownerConsultation = createConsultation(eier)
            val id = JournalnotatId(Uuid.generateV4())
            consultationRepository.insertJournalnotat(
                Journalnotat(id, ownerConsultation, eier, "original"),
                DEFAULT_ORG_NUMBER,
            )
            val angriper = createPatient()
            val attackerConsultation = createConsultation(angriper)

            val rows =
                consultationRepository.insertJournalnotat(
                    Journalnotat(id, attackerConsultation, angriper, "overskrevet"),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(0, rows)
            val lagret = consultationRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)
            assertEquals("original", lagret?.journalnotat)
            assertEquals(eier, lagret?.patientId)
            assertEquals(ownerConsultation, lagret?.consultationId)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat updates the text of an existing journalnotat in the same consultation`() =
        runTest {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)
            val id = JournalnotatId(Uuid.generateV4())
            consultationRepository.insertJournalnotat(
                Journalnotat(id, consultationId, patientId, "første"),
                DEFAULT_ORG_NUMBER,
            )

            val rows =
                consultationRepository.insertJournalnotat(
                    Journalnotat(id, consultationId, patientId, "andre"),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(1, rows)
            assertEquals(
                "andre",
                consultationRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)?.journalnotat,
            )
        }
}
