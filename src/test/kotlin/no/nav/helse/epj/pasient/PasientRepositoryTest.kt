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
      fornavn = "fornavn",
      etternavn = "etternavn",
      fnr = fnr,
    )

  @Test
  fun `findById returns null when patient does not exist`() = runTest {
    assertNull(pasientRepository.findById(Uuid.generateV4()))
  }

  @Test
  fun `findById returns patient after insert`() = runTest {
    val pasient = nyPasient()
    pasientRepository.insert(pasient)

    val funnet = pasientRepository.findById(pasient.id.value)

    assertEquals(pasient.id, funnet?.id)
    assertEquals(pasient.fnr, funnet?.fnr)
    assertEquals(pasient.fornavn, funnet?.fornavn)
    assertEquals(pasient.etternavn, funnet?.etternavn)
  }

  @Test
  fun `findByFnr returns null when fnr does not exist`() = runTest {
    assertNull(pasientRepository.findByFnr("finnes-ikke"))
  }

  @Test
  fun `findByFnr returns patient with the correct fnr`() = runTest {
    val pasient = nyPasient(fnr = "12345678910")
    pasientRepository.insert(pasient)

    val funnet = pasientRepository.findByFnr("12345678910")

    assertEquals(pasient.id, funnet?.id)
  }

  @Test
  fun `insert with the same id twice does not create a duplicate`() = runTest {
    val pasient = nyPasient()
    pasientRepository.insert(pasient)
    pasientRepository.insert(pasient.copy(fornavn = "annet navn"))

    val funnet = pasientRepository.findById(pasient.id.value)

    assertEquals("fornavn", funnet?.fornavn)
  }
}
