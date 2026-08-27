package no.nav.helse.epj.konsultasjon

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.PasientId
import org.junit.Test

class KonsultasjonServiceTest {

  private val konsultasjonRepository = mockk<KonsultasjonRepository>()
  private val konsultasjonService = KonsultasjonService(konsultasjonRepository)

  private fun konsultasjon(
    id: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
    pasientId: PasientId = PasientId(Uuid.generateV4()),
    status: KonsultasjonStatus = KonsultasjonStatus.PÅGÅENDE,
  ) =
    Konsultasjon(
      id = id,
      pasientId = pasientId,
      hpr = emptyList(),
      journalnotat = emptyList(),
      diagnoser = emptyList(),
      startetTidspunkt = java.time.LocalDateTime.now().minusHours(1),
      avsluttetTidspunkt = null,
      status = status,
      problemstilling = null,
    )

  @Test
  fun `getKonsultasjon throws KonsultasjonNotFoundException when konsultasjon does not exist`() =
    runTest {
      val konsultasjonId = KonsultasjonId(Uuid.generateV4())
      coEvery { konsultasjonRepository.findByKonsultasjonId(konsultasjonId) } returns null

      assertFailsWith<KonsultasjonNotFoundException> {
        konsultasjonService.getKonsultasjon(konsultasjonId)
      }
    }

  @Test
  fun `getKonsultasjon returns konsultasjon when it exists`() = runTest {
    val konsultasjonId = KonsultasjonId(Uuid.generateV4())
    val forventet = konsultasjon(id = konsultasjonId)
    coEvery { konsultasjonRepository.findByKonsultasjonId(konsultasjonId) } returns forventet

    val resultat = konsultasjonService.getKonsultasjon(konsultasjonId)

    assertSame(forventet, resultat)
  }

  @Test
  fun `getOrCreateKonsultasjon returns the active konsultasjon without creating a new one`() =
    runTest {
      val pasientId = PasientId(Uuid.generateV4())
      val hpr = HelsepersonellHpr("123")
      val aktivKonsultasjon = konsultasjon(pasientId = pasientId)
      coEvery { konsultasjonRepository.findActiveByPasientId(pasientId) } returns aktivKonsultasjon

      val resultat = konsultasjonService.getOrCreateKonsultasjon(pasientId, hpr)

      assertSame(aktivKonsultasjon, resultat)
      coVerify(exactly = 0) { konsultasjonRepository.insert(any()) }
    }

  @Test
  fun `getOrCreateKonsultasjon creates a new konsultasjon when none is active`() = runTest {
    val pasientId = PasientId(Uuid.generateV4())
    val hpr = HelsepersonellHpr("123")
    val opprettetId = KonsultasjonId(Uuid.generateV4())
    val opprettetKonsultasjon = konsultasjon(id = opprettetId, pasientId = pasientId)

    coEvery { konsultasjonRepository.findActiveByPasientId(pasientId) } returns null
    coEvery { konsultasjonRepository.insert(any()) } returns opprettetId
    coEvery { konsultasjonRepository.findByKonsultasjonId(opprettetId) } returns
      opprettetKonsultasjon

    val resultat = konsultasjonService.getOrCreateKonsultasjon(pasientId, hpr)

    assertSame(opprettetKonsultasjon, resultat)
    coVerify(exactly = 1) { konsultasjonRepository.insert(any()) }
  }

  @Test
  fun `createKonsultasjon throws IllegalStateException when konsultasjon does not exist after insert`() =
    runTest {
      val pasientId = PasientId(Uuid.generateV4())
      val opprettetId = KonsultasjonId(Uuid.generateV4())
      val opprettKonsultasjon =
        OpprettKonsultasjon(
          pasientId = pasientId,
          hpr = emptyList(),
          startetTidspunkt = LocalDateTime.now().minusHours(1),
          status = KonsultasjonStatus.PÅGÅENDE,
        )
      coEvery { konsultasjonRepository.insert(opprettKonsultasjon) } returns opprettetId
      coEvery { konsultasjonRepository.findByKonsultasjonId(opprettetId) } returns null

      assertFailsWith<IllegalStateException> {
        konsultasjonService.createKonsultasjon(opprettKonsultasjon)
      }
    }

  @Test
  fun `updateKonsultasjon throws KonsultasjonNotFoundForPatientException when 0 rows are updated`() =
    runTest {
      val pasientId = PasientId(Uuid.generateV4())
      val request =
        OppdaterKonsultasjonRequest(
          konsultasjonId = KonsultasjonId(Uuid.generateV4()),
          diagnoser = emptyList(),
          journalNotat = null,
          ferdigstill = false,
        )
      coEvery { konsultasjonRepository.update(request, pasientId) } returns 0

      assertFailsWith<KonsultasjonNotFoundForPatientException> {
        konsultasjonService.updateKonsultasjon(request, pasientId)
      }
    }

  @Test
  fun `updateKonsultasjon completes without error when rows are updated`() = runTest {
    val pasientId = PasientId(Uuid.generateV4())
    val request =
      OppdaterKonsultasjonRequest(
        konsultasjonId = KonsultasjonId(Uuid.generateV4()),
        diagnoser = emptyList(),
        journalNotat = "notat",
        ferdigstill = true,
      )
    coEvery { konsultasjonRepository.update(request, pasientId) } returns 2

    konsultasjonService.updateKonsultasjon(request, pasientId)

    coVerify(exactly = 1) { konsultasjonRepository.update(request, pasientId) }
  }

  @Test
  fun `createJournalnotat returns true when exactly one row is inserted`() = runTest {
    val journalnotat =
      Journalnotat(
        id = JournalnotatId(Uuid.generateV4()),
        konsultasjonId = KonsultasjonId(Uuid.generateV4()),
        pasientId = PasientId(Uuid.generateV4()),
        journalnotat = "notat",
      )
    coEvery { konsultasjonRepository.insertJournalnotat(journalnotat) } returns 1

    assertEquals(true, konsultasjonService.createJournalnotat(journalnotat))
  }

  @Test
  fun `createJournalnotat returns false when no row is inserted`() = runTest {
    val journalnotat =
      Journalnotat(
        id = JournalnotatId(Uuid.generateV4()),
        konsultasjonId = KonsultasjonId(Uuid.generateV4()),
        pasientId = PasientId(Uuid.generateV4()),
        journalnotat = "notat",
      )
    coEvery { konsultasjonRepository.insertJournalnotat(journalnotat) } returns 0

    assertEquals(false, konsultasjonService.createJournalnotat(journalnotat))
  }
}
