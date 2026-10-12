package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import org.junit.Test

class KonsultasjonRepositoryUpdateTest : KonsultasjonRepositoryTestBase() {
    @Test
    fun `update returns 0 rows and makes no changes when pasientId does not own the konsultasjon`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientId = opprettPasient(hpr = hpr)
            val annenPasientId = opprettPasient(hpr = hpr)
            val konsultasjonId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )

            val updatedRows =
                konsultasjonRepository.update(
                    OppdaterKonsultasjonRequest(
                        konsultasjonId = konsultasjonId,
                        diagnoser =
                            listOf(
                                OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)
                            ),
                        journalNotat = "notat",
                        ferdigstill = true,
                    ),
                    annenPasientId,
                )

            assertEquals(0, updatedRows)
        }

    @Test
    fun `update adds a new journalnotat, stores diagnose and completes konsultasjon`() = runTest {
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
        val updatedRows =
            konsultasjonRepository.update(
                OppdaterKonsultasjonRequest(
                    konsultasjonId = konsultasjonId,
                    diagnoser =
                        listOf(OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)),
                    journalNotat = "oppdatert notat",
                    ferdigstill = true,
                ),
                pasientId,
            )

        // 1 for ny diagnose + 1 for nytt journalnotat (insert) + 1 for ferdigstilt konsultasjon
        assertEquals(3, updatedRows)
        val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
        assertNotNull(konsultasjon)
        assertEquals(KonsultasjonStatus.FULLFOERT, konsultasjon.status)
        assertNotNull(konsultasjon.avsluttetTidspunkt)
        assertEquals(1, konsultasjon.journalnotat.size)
        assertEquals("oppdatert notat", konsultasjon.journalnotat.last().journalnotat)
        assertEquals(1, konsultasjon.diagnoser.size)
        val lagretDiagnose = konsultasjon.diagnoser.single()
        assertEquals(DiagnoseType.ICPC2, lagretDiagnose.system)
        assertEquals("A01", lagretDiagnose.code)
        assertEquals(Diagnose.from(DiagnoseType.ICPC2, "A01")!!.text, lagretDiagnose.text)
    }

    @Test
    fun `update removes a diagnose that is left out of a later diagnoser list`() = runTest {
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

        konsultasjonRepository.update(
            OppdaterKonsultasjonRequest(
                konsultasjonId = konsultasjonId,
                diagnoser =
                    listOf(
                        OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2),
                        OpprettDiagnoseRequest(kode = "A02", system = DiagnoseType.ICPC2),
                    ),
                journalNotat = null,
                ferdigstill = false,
            ),
            pasientId,
        )

        konsultasjonRepository.update(
            OppdaterKonsultasjonRequest(
                konsultasjonId = konsultasjonId,
                diagnoser =
                    listOf(OpprettDiagnoseRequest(kode = "A02", system = DiagnoseType.ICPC2)),
                journalNotat = null,
                ferdigstill = false,
            ),
            pasientId,
        )

        val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
        assertNotNull(konsultasjon)
        val gjenvarendeDiagnose = konsultasjon.diagnoser.single()
        assertEquals("A02", gjenvarendeDiagnose.code)
    }

    @Test
    fun `update removes all diagnoser when the diagnoser list is submitted empty`() = runTest {
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

        konsultasjonRepository.update(
            OppdaterKonsultasjonRequest(
                konsultasjonId = konsultasjonId,
                diagnoser =
                    listOf(OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)),
                journalNotat = null,
                ferdigstill = false,
            ),
            pasientId,
        )

        konsultasjonRepository.update(
            OppdaterKonsultasjonRequest(
                konsultasjonId = konsultasjonId,
                diagnoser = emptyList(),
                journalNotat = null,
                ferdigstill = false,
            ),
            pasientId,
        )

        val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
        assertNotNull(konsultasjon)
        assertEquals(emptyList(), konsultasjon.diagnoser)
    }

    @Test
    fun `update leaves diagnoser unchanged when an unknown diagnosekode is rejected`() = runTest {
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

        konsultasjonRepository.update(
            OppdaterKonsultasjonRequest(
                konsultasjonId = konsultasjonId,
                diagnoser =
                    listOf(OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)),
                journalNotat = null,
                ferdigstill = false,
            ),
            pasientId,
        )

        assertFailsWith<UgyldigDiagnoseException> {
            konsultasjonRepository.update(
                OppdaterKonsultasjonRequest(
                    konsultasjonId = konsultasjonId,
                    diagnoser =
                        listOf(
                            OpprettDiagnoseRequest(
                                kode = "IKKE-EN-GYLDIG-KODE",
                                system = DiagnoseType.ICPC2,
                            )
                        ),
                    journalNotat = null,
                    ferdigstill = false,
                ),
                pasientId,
            )
        }

        val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
        assertNotNull(konsultasjon)
        assertEquals("A01", konsultasjon.diagnoser.single().code)
    }

    @Test
    fun `update leaves the konsultasjon open when ferdigstill is false`() = runTest {
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

        konsultasjonRepository.update(
            OppdaterKonsultasjonRequest(konsultasjonId, emptyList(), "notat", ferdigstill = false),
            pasientId,
        )

        val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
        assertNotNull(konsultasjon)
        assertEquals(KonsultasjonStatus.PAAGAAENDE, konsultasjon.status)
        assertNull(konsultasjon.avsluttetTidspunkt)
    }

    @Test
    fun `avbryt marks an ongoing konsultasjon as AVLYST`() = runTest {
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

        val updatedRows = konsultasjonRepository.avbryt(konsultasjonId, pasientId)

        assertEquals(1, updatedRows)
        val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
        assertNotNull(konsultasjon)
        assertEquals(KonsultasjonStatus.AVLYST, konsultasjon.status)
        assertNotNull(konsultasjon.avsluttetTidspunkt)
    }

    @Test
    fun `avbryt returns 0 rows and makes no changes when pasientId does not own the konsultasjon`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientId = opprettPasient(hpr = hpr)
            val annenPasientId = opprettPasient(hpr = hpr)
            val konsultasjonId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )

            val updatedRows = konsultasjonRepository.avbryt(konsultasjonId, annenPasientId)

            assertEquals(0, updatedRows)
            val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
            assertNotNull(konsultasjon)
            assertEquals(KonsultasjonStatus.PAAGAAENDE, konsultasjon.status)
        }

    @Test
    fun `avbryt does not reopen an already completed konsultasjon`() = runTest {
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
        konsultasjonRepository.update(
            OppdaterKonsultasjonRequest(konsultasjonId, emptyList(), null, ferdigstill = true),
            pasientId,
        )

        val updatedRows = konsultasjonRepository.avbryt(konsultasjonId, pasientId)

        assertEquals(0, updatedRows)
        val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
        assertNotNull(konsultasjon)
        assertEquals(KonsultasjonStatus.FULLFOERT, konsultasjon.status)
    }

    @Test
    fun `update saving the same journalnotat text twice does not create a duplicate row`() =
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
            val request =
                OppdaterKonsultasjonRequest(
                    konsultasjonId,
                    emptyList(),
                    "notat",
                    ferdigstill = false,
                )

            konsultasjonRepository.update(request, pasientId)
            konsultasjonRepository.update(request, pasientId)

            val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
            assertNotNull(konsultasjon)
            assertEquals(1, konsultasjon.journalnotat.size)
            assertEquals("notat", konsultasjon.journalnotat.single().journalnotat)
        }

    @Test
    fun `update saving a changed journalnotat text updates the existing row instead of adding one`() =
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

            konsultasjonRepository.update(
                OppdaterKonsultasjonRequest(
                    konsultasjonId,
                    emptyList(),
                    "første notat",
                    ferdigstill = false,
                ),
                pasientId,
            )
            konsultasjonRepository.update(
                OppdaterKonsultasjonRequest(
                    konsultasjonId,
                    emptyList(),
                    "oppdatert notat",
                    ferdigstill = false,
                ),
                pasientId,
            )

            val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
            assertNotNull(konsultasjon)
            assertEquals(1, konsultasjon.journalnotat.size)
            assertEquals("oppdatert notat", konsultasjon.journalnotat.single().journalnotat)
        }
}
