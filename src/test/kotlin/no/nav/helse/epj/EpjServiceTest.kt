package no.nav.helse.epj

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.db.HelsepersonellRepository
import no.nav.helse.epj.db.KonsultasjonRepository
import no.nav.helse.epj.db.PasientRepository
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class EpjServiceTest {

  val pasientRepository = mockk<PasientRepository>()
  val helsepersonellRepository = mockk<HelsepersonellRepository>()
  val konsultasjonRepository = mockk<KonsultasjonRepository>()
  val epjService = EpjService(pasientRepository, helsepersonellRepository, konsultasjonRepository)

  private val pasientId = Uuid.generateV4().toString()
  val konsultasjonId = Uuid.generateV4().toString()
  private val hpr = "1234567"
  private val aktivKonsultasjon =
    Konsultasjon(
      id = konsultasjonId,
      pasientId = pasientId,
      hpr = listOf(hpr),
      startetTidspunkt = LocalDateTime(2026, 7, 6, 12, 0),
      avsluttetTidspunkt = null,
      type = "fysisk",
      status = "pågående",
      problemstilling = null,
      journalnotat = null,
    )

  @Test
  fun `returnerer eksisterende aktiv konsultasjon hvis den finnes`() = runTest {
    coEvery { konsultasjonRepository.getAktivKonsultasjon(pasientId) } returns aktivKonsultasjon
    val konsultasjon = epjService.getOrCreateKonsultasjon(pasientId, hpr)
    konsultasjon shouldBe aktivKonsultasjon
  }
}
