package no.nav.helse.epj.pasient

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.utils.WithPostgresql
import org.junit.Test

class PasientRepositoryTest : WithPostgresql() {
  init {
    runMigrations(true)
    connect()
  }

  val pasientRepository = PasientRepository()

  private fun nyPasient(
    id: PatientId = PatientId(Uuid.generateV4()),
    hpr: HelsepersonellHpr = HelsepersonellHpr("123"),
    fnr: String = "fnr-${id.value}",
  ) =
    Pasient(
      id = id,
      legekontorId = Legekontor.DEFAULT.id,
      hprNumbers = listOf(hpr),
      navn = "navn",
      fnr = fnr,
    )

  @Test
  fun `findById returnerer null når pasient ikke finnes`() = runTest {
    assertNull(pasientRepository.findById(Uuid.generateV4()))
  }

  @Test
  fun `findById returnerer pasient etter insert`() = runTest {
    val pasient = nyPasient()
    pasientRepository.insert(pasient)

    val funnet = pasientRepository.findById(pasient.id.value)

    assertEquals(pasient.id, funnet?.id)
    assertEquals(pasient.fnr, funnet?.fnr)
    assertEquals(pasient.navn, funnet?.navn)
  }

  @Test
  fun `findByFnr returnerer null når fnr ikke finnes`() = runTest {
    assertNull(pasientRepository.findByFnr("finnes-ikke"))
  }

  @Test
  fun `findByFnr returnerer pasient med riktig fnr`() = runTest {
    val pasient = nyPasient(fnr = "12345678910")
    pasientRepository.insert(pasient)

    val funnet = pasientRepository.findByFnr("12345678910")

    assertEquals(pasient.id, funnet?.id)
  }

  @Test
  fun `insert med samme id to ganger oppretter ikke duplikat`() = runTest {
    val pasient = nyPasient()
    pasientRepository.insert(pasient)
    pasientRepository.insert(pasient.copy(navn = "annet navn"))

    val funnet = pasientRepository.findById(pasient.id.value)

    assertEquals("navn", funnet?.navn)
  }
}
