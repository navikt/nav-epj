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
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import org.junit.Test

class KonsultasjonRepositoryJournalnotatTest : KonsultasjonRepositoryTestBase() {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat stores a complete round trip of a journalnotat`() = runTest {
        val hpr = HelsepersonellHpr("123")
        val pasientId = opprettPasient(hpr = hpr)
        val konsultasjonId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                )
            )
        val id = JournalnotatId(Uuid.generateV4())
        val request =
            OpprettJournalnotatRequest(
                pasientId = pasientId,
                konsultasjonId = konsultasjonId,
                journalnotat = "notat",
            )

        val opprettet = konsultasjonRepository.opprettJournalnotat(id, request)

        assertEquals(id, opprettet.id)
        assertEquals(pasientId, opprettet.pasientId)
        assertEquals(konsultasjonId, opprettet.konsultasjonId)
        assertEquals("notat", opprettet.journalnotat)
        val lagret = konsultasjonRepository.findJournalnotat(id)
        assertEquals("notat", lagret?.journalnotat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat throws KonsultasjonNotFoundException for an unknown encounter`() =
        runTest {
            val pasientId = opprettPasient()
            val request =
                OpprettJournalnotatRequest(
                    pasientId = pasientId,
                    konsultasjonId = KonsultasjonId(Uuid.generateV4()),
                    journalnotat = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.KonsultasjonNotFoundException> {
                konsultasjonRepository.opprettJournalnotat(
                    JournalnotatId(Uuid.generateV4()),
                    request,
                )
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat throws KonsultasjonTilhorerAnnenPasientException when the encounter belongs to another patient`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val eier = opprettPasient(hpr = hpr)
            val konsultasjonId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        eier,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            val annenPasient = opprettPasient(hpr = hpr)
            val request =
                OpprettJournalnotatRequest(
                    pasientId = annenPasient,
                    konsultasjonId = konsultasjonId,
                    journalnotat = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException> {
                konsultasjonRepository.opprettJournalnotat(
                    JournalnotatId(Uuid.generateV4()),
                    request,
                )
            }
            assertNull(konsultasjonRepository.findJournalnotat(JournalnotatId(Uuid.generateV4())))
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat with a colliding id throws DuplikatJournalnotatException and leaves data unchanged`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientId = opprettPasient(hpr = hpr)
            val konsultasjonId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            val id = JournalnotatId(Uuid.generateV4())
            val request =
                OpprettJournalnotatRequest(
                    pasientId = pasientId,
                    konsultasjonId = konsultasjonId,
                    journalnotat = "original",
                )
            konsultasjonRepository.opprettJournalnotat(id, request)

            assertFailsWith<no.nav.helse.core.utils.DuplikatJournalnotatException> {
                konsultasjonRepository.opprettJournalnotat(
                    id,
                    request.copy(journalnotat = "endret"),
                )
            }

            assertEquals("original", konsultasjonRepository.findJournalnotat(id)?.journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat by pasientId only returns journalnotater for the correct patient`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientA = opprettPasient(hpr = hpr)
            val pasientB = opprettPasient(hpr = hpr)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientA,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientB,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientA, konsultasjonA, "for A"),
            )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientB, konsultasjonB, "for B"),
            )

            val notaterForA = konsultasjonRepository.listJournalnotat(pasientA, null)

            assertEquals(1, notaterForA.size)
            assertEquals("for A", notaterForA.single().journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat filters by encounter when both patient and encounter are given`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientId = opprettPasient(hpr = hpr)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientId, konsultasjonA, "for A"),
            )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientId, konsultasjonB, "for B"),
            )

            val notater = konsultasjonRepository.listJournalnotat(pasientId, konsultasjonA)

            assertEquals(1, notater.size)
            assertEquals("for A", notater.single().journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat returns an empty list when the patient has no journalnotater`() =
        runTest {
            val pasientId = opprettPasient()

            val notater = konsultasjonRepository.listJournalnotat(pasientId, null)

            assertTrue(notater.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat rejects an encounter that belongs to another patient`() = runTest {
        val eier = opprettPasient()
        val konsultasjonId = opprettKonsultasjon(eier)
        val annenPasient = opprettPasient()
        val id = JournalnotatId(Uuid.generateV4())

        assertFailsWith<no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException> {
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, konsultasjonId, annenPasient, "notat")
            )
        }
        assertNull(konsultasjonRepository.findJournalnotat(id))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat does not move an existing journalnotat to another konsultasjon`() =
        runTest {
            val eier = opprettPasient()
            val eierKonsultasjon = opprettKonsultasjon(eier)
            val id = JournalnotatId(Uuid.generateV4())
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, eierKonsultasjon, eier, "original")
            )
            val angriper = opprettPasient()
            val angriperKonsultasjon = opprettKonsultasjon(angriper)

            val rows =
                konsultasjonRepository.insertJournalnotat(
                    Journalnotat(id, angriperKonsultasjon, angriper, "overskrevet")
                )

            assertEquals(0, rows)
            val lagret = konsultasjonRepository.findJournalnotat(id)
            assertEquals("original", lagret?.journalnotat)
            assertEquals(eier, lagret?.pasientId)
            assertEquals(eierKonsultasjon, lagret?.konsultasjonId)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat updates the text of an existing journalnotat in the same konsultasjon`() =
        runTest {
            val pasientId = opprettPasient()
            val konsultasjonId = opprettKonsultasjon(pasientId)
            val id = JournalnotatId(Uuid.generateV4())
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, konsultasjonId, pasientId, "første")
            )

            val rows =
                konsultasjonRepository.insertJournalnotat(
                    Journalnotat(id, konsultasjonId, pasientId, "andre")
                )

            assertEquals(1, rows)
            assertEquals("andre", konsultasjonRepository.findJournalnotat(id)?.journalnotat)
        }
}
