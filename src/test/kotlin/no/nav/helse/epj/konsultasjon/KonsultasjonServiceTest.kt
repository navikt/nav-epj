package no.nav.helse.epj.konsultasjon

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.core.utils.KonsultasjonNotFoundForPatientException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.PatientId
import org.junit.Test

class KonsultasjonServiceTest {

  private val konsultasjonRepository = mockk<KonsultasjonRepository>()
  private val konsultasjonService = KonsultasjonService(konsultasjonRepository)

  private fun konsultasjon(
    id: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
    pasientId: PatientId = PatientId(Uuid.generateV4()),
    status: KonsultasjonStatus = KonsultasjonStatus.PÅGÅENDE,
  ) =
    Konsultasjon(
      id = id,
      pasientId = pasientId,
      hpr = emptyList(),
      journalnotat = emptyList(),
      diagnoser = emptyList(),
      startetTidspunkt = LocalDateTime(2024, 1, 1, 0, 0),
      avsluttetTidspunkt = null,
      status = status,
      problemstilling = null,
    )

  @Test
  fun `getKonsultasjon kaster KonsultasjonNotFoundException når konsultasjon ikke finnes`() =
    runTest {
      val konsultasjonId = KonsultasjonId(Uuid.generateV4())
      coEvery { konsultasjonRepository.findByKonsultasjonId(konsultasjonId) } returns null

      assertFailsWith<KonsultasjonNotFoundException> {
        konsultasjonService.getKonsultasjon(konsultasjonId)
      }
    }

  @Test
  fun `getKonsultasjon returnerer konsultasjon når den finnes`() = runTest {
    val konsultasjonId = KonsultasjonId(Uuid.generateV4())
    val forventet = konsultasjon(id = konsultasjonId)
    coEvery { konsultasjonRepository.findByKonsultasjonId(konsultasjonId) } returns forventet

    val resultat = konsultasjonService.getKonsultasjon(konsultasjonId)

    assertSame(forventet, resultat)
  }

  @Test
  fun `getOrCreateKonsultasjon returnerer aktiv konsultasjon uten å opprette ny`() = runTest {
    val pasientId = PatientId(Uuid.generateV4())
    val hpr = HelsepersonellHpr("123")
    val aktivKonsultasjon = konsultasjon(pasientId = pasientId)
    coEvery { konsultasjonRepository.findActiveByPasientId(pasientId) } returns aktivKonsultasjon

    val resultat = konsultasjonService.getOrCreateKonsultasjon(pasientId, hpr)

    assertSame(aktivKonsultasjon, resultat)
    coVerify(exactly = 0) { konsultasjonRepository.insert(any()) }
  }

  @Test
  fun `getOrCreateKonsultasjon oppretter ny konsultasjon når ingen aktiv finnes`() = runTest {
    val pasientId = PatientId(Uuid.generateV4())
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
  fun `createKonsultasjon kaster IllegalStateException når konsultasjon ikke finnes etter insert`() =
    runTest {
      val pasientId = PatientId(Uuid.generateV4())
      val opprettetId = KonsultasjonId(Uuid.generateV4())
      val opprettKonsultasjon =
        OpprettKonsultasjon(
          pasientId = pasientId,
          hpr = emptyList(),
          startetTidspunkt = LocalDateTime(2024, 1, 1, 0, 0),
          status = KonsultasjonStatus.PÅGÅENDE,
        )
      coEvery { konsultasjonRepository.insert(opprettKonsultasjon) } returns opprettetId
      coEvery { konsultasjonRepository.findByKonsultasjonId(opprettetId) } returns null

      assertFailsWith<IllegalStateException> {
        konsultasjonService.createKonsultasjon(opprettKonsultasjon)
      }
    }

  @Test
  fun `updateKonsultasjon kaster KonsultasjonNotFoundForPatientException når 0 rader oppdateres`() =
    runTest {
      val pasientId = PatientId(Uuid.generateV4())
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
  fun `updateKonsultasjon fullfører uten feil når rader blir oppdatert`() = runTest {
    val pasientId = PatientId(Uuid.generateV4())
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
  fun `createJournalnotat returnerer true når nøyaktig en rad settes inn`() = runTest {
    val journalnotat =
      Journalnotat(
        id = PatientId(Uuid.generateV4()),
        konsultasjonId = KonsultasjonId(Uuid.generateV4()),
        pasientId = PatientId(Uuid.generateV4()),
        journalnotat = "notat",
      )
    coEvery { konsultasjonRepository.insertJournalnotat(journalnotat) } returns 1

    assertEquals(true, konsultasjonService.createJournalnotat(journalnotat))
  }

  @Test
  fun `createJournalnotat returnerer false når ingen rad settes inn`() = runTest {
    val journalnotat =
      Journalnotat(
        id = PatientId(Uuid.generateV4()),
        konsultasjonId = KonsultasjonId(Uuid.generateV4()),
        pasientId = PatientId(Uuid.generateV4()),
        journalnotat = "notat",
      )
    coEvery { konsultasjonRepository.insertJournalnotat(journalnotat) } returns 0

    assertEquals(false, konsultasjonService.createJournalnotat(journalnotat))
  }
}
