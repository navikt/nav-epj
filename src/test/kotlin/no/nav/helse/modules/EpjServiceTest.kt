package no.nav.helse.modules

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.EpjService
import no.nav.helse.epj.api.OpprettHelsepersonell
import no.nav.helse.epj.api.Pasient
import no.nav.helse.epj.db.HelsepersonellRepository
import no.nav.helse.epj.db.KonsultasjonRepository
import no.nav.helse.epj.db.PasientRepository
import no.nav.helse.utils.WithPostgresql
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.Test

// TODO: finn en annen måte å gjøre dette på med disse id greiene ..
val legekontorId = "a1000000-0000-0000-0000-000000000001"
val fastlegeId = "b2000000-0000-0000-0000-000000000001"
val fastlege2Id = "b2000000-0000-0000-0000-000000000002"

@OptIn(ExperimentalUuidApi::class)
class EpjServiceTest : WithPostgresql() {

  val pasient =
    Pasient(
      id = Uuid.generateV4().toString(),
      legekontorId = legekontorId,
      fastlegeId = fastlegeId,
      navn = "pasientnavn",
    )

  companion object {
    init {
      runMigrations(true)
      connect()
    }
  }

  @BeforeTest fun setup() = runTest { dbQuery { PasientTable.deleteAll() } }

  val pasientRepository = PasientRepository()
  val helsepersonellRepository = HelsepersonellRepository()
  val konsultasjonRepository = KonsultasjonRepository()
  val epjService = EpjService(pasientRepository, helsepersonellRepository, konsultasjonRepository)

  @Test
  fun `should insert helsepersonell`() = runTest {
    val helsepersonell =
      OpprettHelsepersonell(
        legekontorId = legekontorId,
        hpr = "123",
        navn = "grizzly",
        autorisasjon = "grizzlys-autorisasjon",
      )
    epjService.insertHelsepersonell(helsepersonell)
    val result = epjService.getHelspersonell("123")
    result.shouldNotBeNull()
    result.hpr shouldBe helsepersonell.hpr
    result.navn shouldBe helsepersonell.navn
  }

  @Test
  fun `should return pasient by id`() = runTest {
    pasientRepository.insertPasient(pasient)
    val result = epjService.getPasient(pasient.id)
    result?.id shouldBe pasient.id
    result?.navn shouldBe pasient.navn
  }

  @Test
  fun `should return empty list when no pasienter exist`() = runTest {
    val result = epjService.getPasienter()
    result shouldHaveSize 0
  }

  @Test
  fun `should return pasient with correct fields`() = runTest {
    pasientRepository.insertPasient(pasient)
    val result = epjService.getPasienter().first()
    result.navn shouldBe pasient.navn
    result.legekontorId shouldBe pasient.legekontorId
    result.fastlegeId shouldBe pasient.fastlegeId
  }

  @Test
  fun `should return all pasienter when multiple are inserted`() = runTest {
    val pasient2 =
      Pasient(
        id = UUID.randomUUID().toString(),
        legekontorId = legekontorId,
        fastlegeId = fastlege2Id,
        navn = "annen pasient",
      )
    dbQuery {
      PasientTable.insert {
        it[id] = Uuid.parse(pasient.id)
        it[PasientTable.legekontorId] = Uuid.parse(pasient.legekontorId)
        it[PasientTable.fastlegeId] = Uuid.parse(pasient.fastlegeId)
        it[navn] = pasient.navn
      }
      PasientTable.insert {
        it[id] = Uuid.parse(pasient2.id)
        it[PasientTable.legekontorId] = Uuid.parse(pasient2.legekontorId)
        it[PasientTable.fastlegeId] = Uuid.parse(pasient2.fastlegeId)
        it[navn] = pasient2.navn
      }
    }

    val result = epjService.getPasienter()
    result shouldHaveSize 2
    result.map { it.navn } shouldContain pasient.navn
    result.map { it.navn } shouldContain pasient2.navn
  }
}
