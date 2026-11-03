package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class KonsultasjonRepositoryJournalnotatTest : KonsultasjonRepositoryTestBase() {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat stores a complete round trip of a journalnotat`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        val konsultasjonId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )
        val id = JournalnotatId(Uuid.generateV4())
        val request =
            OpprettJournalnotatRequest(
                patientId = patientId,
                konsultasjonId = konsultasjonId,
                journalnotat = "notat",
            )

        val opprettet = konsultasjonRepository.opprettJournalnotat(id, request, DEFAULT_ORG_NUMBER)

        assertEquals(id, opprettet.id)
        assertEquals(patientId, opprettet.patientId)
        assertEquals(konsultasjonId, opprettet.konsultasjonId)
        assertEquals("notat", opprettet.journalnotat)
        val lagret = konsultasjonRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)
        assertEquals("notat", lagret?.journalnotat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat throws KonsultasjonNotFoundException for an unknown encounter`() =
        runTest {
            val patientId = createPatient()
            val request =
                OpprettJournalnotatRequest(
                    patientId = patientId,
                    konsultasjonId = KonsultasjonId(Uuid.generateV4()),
                    journalnotat = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.KonsultasjonNotFoundException> {
                konsultasjonRepository.opprettJournalnotat(
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
            val konsultasjonId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        eier,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val otherPatient = createPatient(hpr = hpr)
            val request =
                OpprettJournalnotatRequest(
                    patientId = otherPatient,
                    konsultasjonId = konsultasjonId,
                    journalnotat = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException> {
                konsultasjonRepository.opprettJournalnotat(
                    JournalnotatId(Uuid.generateV4()),
                    request,
                    DEFAULT_ORG_NUMBER,
                )
            }
            assertNull(
                konsultasjonRepository.findJournalnotat(
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
            val konsultasjonId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val id = JournalnotatId(Uuid.generateV4())
            val request =
                OpprettJournalnotatRequest(
                    patientId = patientId,
                    konsultasjonId = konsultasjonId,
                    journalnotat = "original",
                )
            konsultasjonRepository.opprettJournalnotat(id, request, DEFAULT_ORG_NUMBER)

            assertFailsWith<no.nav.helse.core.utils.DuplikatJournalnotatException> {
                konsultasjonRepository.opprettJournalnotat(
                    id,
                    request.copy(journalnotat = "endret"),
                    DEFAULT_ORG_NUMBER,
                )
            }

            assertEquals(
                "original",
                konsultasjonRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)?.journalnotat,
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat by patientId only returns journalnotater for the correct patient`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientA = createPatient(hpr = hpr)
            val patientB = createPatient(hpr = hpr)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientA,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientB,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(patientA, konsultasjonA, "for A"),
                DEFAULT_ORG_NUMBER,
            )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(patientB, konsultasjonB, "for B"),
                DEFAULT_ORG_NUMBER,
            )

            val notaterForA =
                konsultasjonRepository.listJournalnotat(patientA, null, DEFAULT_ORG_NUMBER)

            assertEquals(1, notaterForA.size)
            assertEquals("for A", notaterForA.single().journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat filters by encounter when both patient and encounter are given`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val patientId = createPatient(hpr = hpr)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(patientId, konsultasjonA, "for A"),
                DEFAULT_ORG_NUMBER,
            )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(patientId, konsultasjonB, "for B"),
                DEFAULT_ORG_NUMBER,
            )

            val notater =
                konsultasjonRepository.listJournalnotat(
                    patientId,
                    konsultasjonA,
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
                konsultasjonRepository.listJournalnotat(patientId, null, DEFAULT_ORG_NUMBER)

            assertTrue(notater.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat rejects an encounter that belongs to another patient`() = runTest {
        val eier = createPatient()
        val konsultasjonId = opprettKonsultasjon(eier)
        val otherPatient = createPatient()
        val id = JournalnotatId(Uuid.generateV4())

        assertFailsWith<no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException> {
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, konsultasjonId, otherPatient, "notat"),
                DEFAULT_ORG_NUMBER,
            )
        }
        assertNull(konsultasjonRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat does not move an existing journalnotat to another konsultasjon`() =
        runTest {
            val eier = createPatient()
            val eierKonsultasjon = opprettKonsultasjon(eier)
            val id = JournalnotatId(Uuid.generateV4())
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, eierKonsultasjon, eier, "original"),
                DEFAULT_ORG_NUMBER,
            )
            val angriper = createPatient()
            val angriperKonsultasjon = opprettKonsultasjon(angriper)

            val rows =
                konsultasjonRepository.insertJournalnotat(
                    Journalnotat(id, angriperKonsultasjon, angriper, "overskrevet"),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(0, rows)
            val lagret = konsultasjonRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)
            assertEquals("original", lagret?.journalnotat)
            assertEquals(eier, lagret?.patientId)
            assertEquals(eierKonsultasjon, lagret?.konsultasjonId)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat updates the text of an existing journalnotat in the same konsultasjon`() =
        runTest {
            val patientId = createPatient()
            val konsultasjonId = opprettKonsultasjon(patientId)
            val id = JournalnotatId(Uuid.generateV4())
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, konsultasjonId, patientId, "første"),
                DEFAULT_ORG_NUMBER,
            )

            val rows =
                konsultasjonRepository.insertJournalnotat(
                    Journalnotat(id, konsultasjonId, patientId, "andre"),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(1, rows)
            assertEquals(
                "andre",
                konsultasjonRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)?.journalnotat,
            )
        }
}
