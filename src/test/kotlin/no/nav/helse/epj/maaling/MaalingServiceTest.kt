package no.nav.helse.epj.maaling

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.pasient.PasientId
import org.junit.Test

class MaalingServiceTest {
    private val repository = mockk<MaalingRepository>()
    private val service = MaalingService(repository)

    @OptIn(ExperimentalUuidApi::class)
    private fun request(
        pasientId: PasientId = PasientId(Uuid.generateV4()),
        konsultasjonId: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
    ) =
        OpprettMaalingRequest(
            pasientId = pasientId,
            konsultasjonId = konsultasjonId,
            hpr = HelsepersonellHpr("123"),
            loincKode = "8310-5",
            loincVisningsnavn = "Body temperature",
            verdi = BigDecimal("37.2000"),
            enhetKode = "Cel",
            enhetVisningsnavn = "degree Celsius",
            effektivTidspunkt = LocalDateTime.now(),
            status = MaalingStatus.FINAL,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettMaaling persists a maaling with a generated id`() = runTest {
        val inserted = slot<Maaling>()
        val opprettRequest = request()
        coEvery { repository.insert(capture(inserted)) } returns Unit

        val opprettet = service.opprettMaaling(opprettRequest)

        assertEquals(opprettRequest.pasientId, opprettet.pasientId)
        assertEquals(opprettRequest.konsultasjonId, opprettet.konsultasjonId)
        assertEquals(opprettRequest.loincKode, opprettet.loincKode)
        assertEquals(opprettRequest.verdi, opprettet.verdi)
        assertEquals(opprettet, inserted.captured)
        coVerify(exactly = 1) { repository.insert(any()) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMaaling returns the maaling from the repository`() = runTest {
        val id = MaalingId(Uuid.generateV4())
        val opprettRequest = request()
        val maaling =
            Maaling(
                id = id,
                pasientId = opprettRequest.pasientId,
                konsultasjonId = opprettRequest.konsultasjonId,
                hpr = opprettRequest.hpr,
                loincKode = opprettRequest.loincKode,
                loincVisningsnavn = opprettRequest.loincVisningsnavn,
                verdi = opprettRequest.verdi,
                enhetKode = opprettRequest.enhetKode,
                enhetVisningsnavn = opprettRequest.enhetVisningsnavn,
                effektivTidspunkt = opprettRequest.effektivTidspunkt,
                status = opprettRequest.status,
            )
        coEvery { repository.findById(id) } returns maaling

        val resultat = service.getMaaling(id)

        assertEquals(maaling, resultat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMaalingerForPasient returns maalinger from the repository`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val opprettRequest = request(pasientId = pasientId)
        val maaling =
            Maaling(
                id = MaalingId(Uuid.generateV4()),
                pasientId = pasientId,
                konsultasjonId = opprettRequest.konsultasjonId,
                hpr = opprettRequest.hpr,
                loincKode = opprettRequest.loincKode,
                loincVisningsnavn = opprettRequest.loincVisningsnavn,
                verdi = opprettRequest.verdi,
                enhetKode = opprettRequest.enhetKode,
                enhetVisningsnavn = opprettRequest.enhetVisningsnavn,
                effektivTidspunkt = opprettRequest.effektivTidspunkt,
                status = opprettRequest.status,
            )
        coEvery { repository.listByPasientId(pasientId) } returns listOf(maaling)

        val resultat = service.getMaalingerForPasient(pasientId)

        assertEquals(listOf(maaling), resultat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMaalingerForKonsultasjon returns maalinger from the repository`() = runTest {
        val konsultasjonId = KonsultasjonId(Uuid.generateV4())
        val opprettRequest = request(konsultasjonId = konsultasjonId)
        val maaling =
            Maaling(
                id = MaalingId(Uuid.generateV4()),
                pasientId = opprettRequest.pasientId,
                konsultasjonId = konsultasjonId,
                hpr = opprettRequest.hpr,
                loincKode = opprettRequest.loincKode,
                loincVisningsnavn = opprettRequest.loincVisningsnavn,
                verdi = opprettRequest.verdi,
                enhetKode = opprettRequest.enhetKode,
                enhetVisningsnavn = opprettRequest.enhetVisningsnavn,
                effektivTidspunkt = opprettRequest.effektivTidspunkt,
                status = opprettRequest.status,
            )
        coEvery { repository.listByKonsultasjonId(konsultasjonId) } returns listOf(maaling)

        val resultat = service.getMaalingerForKonsultasjon(konsultasjonId)

        assertEquals(listOf(maaling), resultat)
    }
}
