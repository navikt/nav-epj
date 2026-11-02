package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.OTHER_ORG_NUMBER
import no.nav.helse.utils.ensureOrg
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class KonsultasjonRepositoryOrgScopeTest : KonsultasjonRepositoryTestBase() {
    private val hpr = PractitionerHpr("123")

    private suspend fun startKonsultasjonAt(pasientId: PasientId, orgNumber: String) =
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                pasientId,
                listOf(hpr),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
                orgNumber,
            )
        )

    @Test
    fun `konsultasjoner recorded at one org are invisible at another`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORG_NUMBER)

        assertEquals(
            listOf(konsultasjonId),
            konsultasjonRepository.listByPasientId(pasientId, DEFAULT_ORG_NUMBER).map { it.id },
        )
        assertTrue(konsultasjonRepository.listByPasientId(pasientId, OTHER_ORG_NUMBER).isEmpty())
        assertNull(konsultasjonRepository.findActiveByPasientId(pasientId, OTHER_ORG_NUMBER))
        assertNull(
            konsultasjonRepository.findActiveByPasientIdAndHpr(pasientId, hpr, OTHER_ORG_NUMBER)
        )
        assertNull(konsultasjonRepository.findByKonsultasjonId(konsultasjonId, OTHER_ORG_NUMBER))
    }

    @Test
    fun `a null org is unrestricted`() = runTest {
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORG_NUMBER)

        assertEquals(
            konsultasjonId,
            konsultasjonRepository.findByKonsultasjonId(konsultasjonId, null)?.id,
        )
    }

    @Test
    fun `avbryt and update at another org change nothing`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORG_NUMBER)

        assertEquals(0, konsultasjonRepository.avbryt(konsultasjonId, pasientId, OTHER_ORG_NUMBER))
        assertEquals(
            KonsultasjonStatus.PAAGAAENDE,
            konsultasjonRepository.findByKonsultasjonId(konsultasjonId, DEFAULT_ORG_NUMBER)?.status,
        )
    }

    @Test
    fun `journalnotater recorded at one org are invisible at another`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORG_NUMBER)
        val id = JournalnotatId(Uuid.generateV4())
        konsultasjonRepository.opprettJournalnotat(
            id,
            OpprettJournalnotatRequest(pasientId, konsultasjonId, "notat"),
            DEFAULT_ORG_NUMBER,
        )

        assertEquals(
            "notat",
            konsultasjonRepository.findJournalnotat(id, DEFAULT_ORG_NUMBER)?.journalnotat,
        )
        assertNull(konsultasjonRepository.findJournalnotat(id, OTHER_ORG_NUMBER))
        assertTrue(
            konsultasjonRepository.listJournalnotat(pasientId, null, OTHER_ORG_NUMBER).isEmpty()
        )
    }

    @Test
    fun `a journalnotat cannot be written to a konsultasjon at another org`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val pasientId = opprettPasient()
        val konsultasjonId = startKonsultasjonAt(pasientId, DEFAULT_ORG_NUMBER)

        val result = runCatching {
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientId, konsultasjonId, "notat"),
                OTHER_ORG_NUMBER,
            )
        }

        assertTrue(result.isFailure)
        assertTrue(konsultasjonRepository.listJournalnotat(pasientId, null, null).isEmpty())
    }
}
