package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.utils.DEFAULT_ORGNUMMER
import no.nav.helse.utils.OTHER_ORGNUMMER
import no.nav.helse.utils.ensureOrg
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class KonsultasjonRepositoryOrgScopeTest : KonsultasjonRepositoryTestBase() {
    private val hpr = HelsepersonellHpr("123")

    private suspend fun startKonsultasjonAt(pasientId: PasientId, orgnummer: String) =
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                pasientId,
                listOf(hpr),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
                orgnummer,
            )
        )

    @Test
    fun `konsultasjoner recorded at one org are invisible at another`() = runTest {
        ensureOrg(OTHER_ORGNUMMER)
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORGNUMMER)

        assertEquals(
            listOf(konsultasjonId),
            konsultasjonRepository.listByPasientId(pasientId, DEFAULT_ORGNUMMER).map { it.id },
        )
        assertTrue(konsultasjonRepository.listByPasientId(pasientId, OTHER_ORGNUMMER).isEmpty())
        assertNull(konsultasjonRepository.findActiveByPasientId(pasientId, OTHER_ORGNUMMER))
        assertNull(
            konsultasjonRepository.findActiveByPasientIdAndHpr(pasientId, hpr, OTHER_ORGNUMMER)
        )
        assertNull(konsultasjonRepository.findByKonsultasjonId(konsultasjonId, OTHER_ORGNUMMER))
    }

    @Test
    fun `a null org is unrestricted`() = runTest {
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORGNUMMER)

        assertEquals(
            konsultasjonId,
            konsultasjonRepository.findByKonsultasjonId(konsultasjonId, null)?.id,
        )
    }

    @Test
    fun `avbryt and update at another org change nothing`() = runTest {
        ensureOrg(OTHER_ORGNUMMER)
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORGNUMMER)

        assertEquals(0, konsultasjonRepository.avbryt(konsultasjonId, pasientId, OTHER_ORGNUMMER))
        assertEquals(
            KonsultasjonStatus.PAAGAAENDE,
            konsultasjonRepository.findByKonsultasjonId(konsultasjonId, DEFAULT_ORGNUMMER)?.status,
        )
    }

    @Test
    fun `journalnotater recorded at one org are invisible at another`() = runTest {
        ensureOrg(OTHER_ORGNUMMER)
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORGNUMMER)
        val id = JournalnotatId(Uuid.generateV4())
        konsultasjonRepository.opprettJournalnotat(
            id,
            OpprettJournalnotatRequest(pasientId, konsultasjonId, "notat"),
            DEFAULT_ORGNUMMER,
        )

        assertEquals(
            "notat",
            konsultasjonRepository.findJournalnotat(id, DEFAULT_ORGNUMMER)?.journalnotat,
        )
        assertNull(konsultasjonRepository.findJournalnotat(id, OTHER_ORGNUMMER))
        assertTrue(
            konsultasjonRepository.listJournalnotat(pasientId, null, OTHER_ORGNUMMER).isEmpty()
        )
    }

    @Test
    fun `a journalnotat cannot be written to a konsultasjon at another org`() = runTest {
        ensureOrg(OTHER_ORGNUMMER)
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORGNUMMER)

        val result = runCatching {
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientId, konsultasjonId, "notat"),
                OTHER_ORGNUMMER,
            )
        }

        assertTrue(result.isFailure)
        assertTrue(konsultasjonRepository.listJournalnotat(pasientId, null, null).isEmpty())
    }
}
