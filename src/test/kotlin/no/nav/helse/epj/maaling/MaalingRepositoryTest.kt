package no.nav.helse.epj.maaling

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.DuplikatMaalingException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonRepository
import no.nav.helse.epj.konsultasjon.OpprettKonsultasjon
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientRepository
import no.nav.helse.utils.WithPostgresql
import org.junit.Test

class MaalingRepositoryTest : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    val maalingRepository = MaalingRepository()
    val pasientRepository = PasientRepository()
    val konsultasjonRepository = KonsultasjonRepository()

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun opprettPasientMedKonsultasjon(
        hpr: HelsepersonellHpr = HelsepersonellHpr("123")
    ): Pair<PasientId, KonsultasjonId> {
        val pasientId = PasientId(Uuid.generateV4())
        pasientRepository.insert(
            Pasient(
                id = pasientId,
                legekontorId = Legekontor.DEFAULT.id,
                fornavn = "fornavn",
                etternavn = "etternavn",
                personident = "personident-${pasientId.value}",
                hprNumbers = listOf(hpr),
            )
        )
        val konsultasjonId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PÅGÅENDE,
                )
            )
        return pasientId to konsultasjonId
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun nyMaaling(
        pasientId: PasientId,
        konsultasjonId: KonsultasjonId,
        id: MaalingId = MaalingId(Uuid.generateV4()),
        hpr: HelsepersonellHpr? = HelsepersonellHpr("123"),
        loincKode: String = "8310-5",
        loincVisningsnavn: String = "Body temperature",
        verdi: BigDecimal = BigDecimal("37.2000"),
        enhetKode: String = "Cel",
        enhetVisningsnavn: String = "degree Celsius",
        effektivTidspunkt: LocalDateTime = LocalDateTime.now(),
        status: MaalingStatus = MaalingStatus.FINAL,
    ) =
        Maaling(
            id = id,
            pasientId = pasientId,
            konsultasjonId = konsultasjonId,
            hpr = hpr,
            loincKode = loincKode,
            loincVisningsnavn = loincVisningsnavn,
            verdi = verdi,
            enhetKode = enhetKode,
            enhetVisningsnavn = enhetVisningsnavn,
            effektivTidspunkt = effektivTidspunkt,
            status = status,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `findById returns null when maaling does not exist`() = runTest {
        assertNull(maalingRepository.findById(MaalingId(Uuid.generateV4())))
    }

    @Test
    fun `insert stores a complete round trip of a maaling`() = runTest {
        val (pasientId, konsultasjonId) = opprettPasientMedKonsultasjon()
        val maaling = nyMaaling(pasientId, konsultasjonId)

        maalingRepository.insert(maaling)
        val funnet = maalingRepository.findById(maaling.id)

        assertEquals(maaling.id, funnet?.id)
        assertEquals(maaling.pasientId, funnet?.pasientId)
        assertEquals(maaling.konsultasjonId, funnet?.konsultasjonId)
        assertEquals(maaling.hpr, funnet?.hpr)
        assertEquals(maaling.loincKode, funnet?.loincKode)
        assertEquals(maaling.loincVisningsnavn, funnet?.loincVisningsnavn)
        assertEquals(0, maaling.verdi.compareTo(funnet?.verdi))
        assertEquals(maaling.enhetKode, funnet?.enhetKode)
        assertEquals(maaling.enhetVisningsnavn, funnet?.enhetVisningsnavn)
        assertEquals(maaling.effektivTidspunkt, funnet?.effektivTidspunkt)
        assertEquals(maaling.status, funnet?.status)
    }

    @Test
    fun `insert preserves decimal precision, unit, code and status values`() = runTest {
        val (pasientId, konsultasjonId) = opprettPasientMedKonsultasjon()
        val maaling =
            nyMaaling(
                pasientId,
                konsultasjonId,
                loincKode = "2339-0",
                loincVisningsnavn = "Glucose",
                verdi = BigDecimal("5.6789"),
                enhetKode = "mmol/L",
                enhetVisningsnavn = "millimole per liter",
                status = MaalingStatus.ENTERED_IN_ERROR,
            )

        maalingRepository.insert(maaling)
        val funnet = maalingRepository.findById(maaling.id)

        assertEquals(0, BigDecimal("5.6789").compareTo(funnet?.verdi))
        assertEquals("mmol/L", funnet?.enhetKode)
        assertEquals("millimole per liter", funnet?.enhetVisningsnavn)
        assertEquals("2339-0", funnet?.loincKode)
        assertEquals(MaalingStatus.ENTERED_IN_ERROR, funnet?.status)
    }

    @Test
    fun `insert without a performer stores a null hpr`() = runTest {
        val (pasientId, konsultasjonId) = opprettPasientMedKonsultasjon()
        val maaling = nyMaaling(pasientId, konsultasjonId, hpr = null)

        maalingRepository.insert(maaling)
        val funnet = maalingRepository.findById(maaling.id)

        assertNull(funnet?.hpr)
    }

    @Test
    fun `listByPasientId returns only maalinger for the correct patient ordered by effektivTidspunkt`() =
        runTest {
            val (pasientId, konsultasjonId) = opprettPasientMedKonsultasjon()
            val (annenPasientId, annenKonsultasjonId) = opprettPasientMedKonsultasjon()

            val eldst =
                nyMaaling(
                    pasientId,
                    konsultasjonId,
                    effektivTidspunkt = LocalDateTime.now().minusDays(2),
                )
            val nyest =
                nyMaaling(pasientId, konsultasjonId, effektivTidspunkt = LocalDateTime.now())
            val mellom =
                nyMaaling(
                    pasientId,
                    konsultasjonId,
                    effektivTidspunkt = LocalDateTime.now().minusDays(1),
                )
            val annenPasientMaaling = nyMaaling(annenPasientId, annenKonsultasjonId)

            listOf(nyest, eldst, mellom, annenPasientMaaling).forEach {
                maalingRepository.insert(it)
            }

            val maalinger = maalingRepository.listByPasientId(pasientId)

            assertEquals(listOf(eldst.id, mellom.id, nyest.id), maalinger.map { it.id })
        }

    @Test
    fun `listByKonsultasjonId returns only maalinger for the correct konsultasjon ordered by effektivTidspunkt`() =
        runTest {
            val (pasientId, konsultasjonId) = opprettPasientMedKonsultasjon()
            val annenKonsultasjonId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        emptyList(),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PÅGÅENDE,
                    )
                )

            val eldst =
                nyMaaling(
                    pasientId,
                    konsultasjonId,
                    effektivTidspunkt = LocalDateTime.now().minusDays(1),
                )
            val nyest =
                nyMaaling(pasientId, konsultasjonId, effektivTidspunkt = LocalDateTime.now())
            val annenKonsultasjonMaaling = nyMaaling(pasientId, annenKonsultasjonId)

            listOf(nyest, eldst, annenKonsultasjonMaaling).forEach { maalingRepository.insert(it) }

            val maalinger = maalingRepository.listByKonsultasjonId(konsultasjonId)

            assertEquals(listOf(eldst.id, nyest.id), maalinger.map { it.id })
        }

    @Test
    fun `insert with the same id twice throws DuplikatMaalingException and leaves data unchanged`() =
        runTest {
            val (pasientId, konsultasjonId) = opprettPasientMedKonsultasjon()
            val maaling = nyMaaling(pasientId, konsultasjonId, loincKode = "8310-5")

            maalingRepository.insert(maaling)

            assertFailsWith<DuplikatMaalingException> {
                maalingRepository.insert(maaling.copy(loincKode = "annen-kode"))
            }

            val funnet = maalingRepository.findById(maaling.id)
            assertEquals("8310-5", funnet?.loincKode)
        }

    @Test
    fun `insert rejects a maaling whose konsultasjon belongs to another patient`() = runTest {
        val (_, konsultasjonId) = opprettPasientMedKonsultasjon()
        val (annenPasientId, _) = opprettPasientMedKonsultasjon()
        val maaling = nyMaaling(annenPasientId, konsultasjonId)

        assertFailsWith<KonsultasjonTilhorerAnnenPasientException> {
            maalingRepository.insert(maaling)
        }

        assertNull(maalingRepository.findById(maaling.id))
    }
}
