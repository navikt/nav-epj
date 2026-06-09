package no.nav.helse.modules

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.BeforeTest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.PasientTable
import no.nav.helse.core.db.Repository
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.EpjService
import no.nav.helse.epj.Pasient
import no.nav.helse.utils.WithPostgresql
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.Test

// refererer til ider som er seedet i databasen
// TODO: finn en annen måte å gjøre dette på med disse id greiene ..
@OptIn(ExperimentalUuidApi::class)
val legekontorId = Uuid.parse("a1000000-0000-0000-0000-000000000001")

@OptIn(ExperimentalUuidApi::class)
val fastlegeId = Uuid.parse("b2000000-0000-0000-0000-000000000001")

@OptIn(ExperimentalUuidApi::class)
val fastlege2Id = Uuid.parse("b2000000-0000-0000-0000-000000000002")

@OptIn(ExperimentalUuidApi::class)
class EpjServiceTest : WithPostgresql() {

  val pasient =
    Pasient(
      id = Uuid.generateV4(),
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

  val repository = Repository()
  val epjService = EpjService(repository)

  @Test
  fun `should return empty list when no pasienter exist`() = runTest {
    val result = epjService.getPasienter()
    result shouldHaveSize 0
  }

  @Test
  fun `should return pasient with correct fields`() = runTest {
    repository.insertPasient(pasient)
    val result = epjService.getPasienter().first()
    result.navn shouldBe pasient.navn
    result.legekontorId shouldBe pasient.legekontorId
    result.fastlegeId shouldBe pasient.fastlegeId
  }

  @Test
  fun `should return all pasienter when multiple are inserted`() = runTest {
    val pasient2 =
      Pasient(
        id = Uuid.generateV4(),
        legekontorId = legekontorId,
        fastlegeId = fastlege2Id,
        navn = "annen pasient",
      )
    dbQuery {
      PasientTable.insert {
        it[id] = pasient.id
        it[PasientTable.legekontorId] = pasient.legekontorId
        it[PasientTable.fastlegeId] = pasient.fastlegeId
        it[navn] = pasient.navn
      }
      PasientTable.insert {
        it[id] = pasient2.id
        it[PasientTable.legekontorId] = pasient2.legekontorId
        it[PasientTable.fastlegeId] = pasient2.fastlegeId
        it[navn] = pasient2.navn
      }
    }

    val result = epjService.getPasienter()
    result shouldHaveSize 2
    result.map { it.navn } shouldContain pasient.navn
    result.map { it.navn } shouldContain pasient2.navn
  }
}
