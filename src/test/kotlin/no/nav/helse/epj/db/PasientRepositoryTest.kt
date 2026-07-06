package no.nav.helse.epj.db

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.BeforeTest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.dbQuery
import no.nav.helse.utils.TestHelper
import no.nav.helse.utils.TestRepository
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class PasientRepositoryTest : TestRepository() {

  val pasientRepository = PasientRepository()
  val testHelper = TestHelper()

  @BeforeTest fun setup() = runTest { dbQuery { deleteAllTestData() } }

  @Test
  fun `should return pasient by id`() = runTest {
    val pasientId = Uuid.generateV4().toString()
    val legeId = Uuid.generateV4().toString()
    testHelper.insertHelsepersonellOgPasientTestData(pasientId, legeId)
    val result = pasientRepository.getPasient(pasientId)
    result?.id shouldBe pasientId
  }

  @Test
  fun `should return empty list when no pasienter exist`() = runTest {
    val result = pasientRepository.getAllPatients()
    result shouldHaveSize 0
  }

  @Test
  fun `should return all pasienter when multiple are inserted`() = runTest {
    val pasientId1 = Uuid.generateV4().toString()
    val pasientId2 = Uuid.generateV4().toString()
    val legeId = Uuid.generateV4().toString()
    testHelper.insertHelsepersonellOgPasientTestData(pasientId1, legeId)
    testHelper.insertPasientTestdata(pasientId2, legeId)
    val result = pasientRepository.getAllPatients()
    result shouldHaveSize 2
    result[0].id shouldBe pasientId1
    result[1].id shouldBe pasientId2
  }
}
