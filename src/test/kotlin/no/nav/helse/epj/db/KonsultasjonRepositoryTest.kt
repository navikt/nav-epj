package no.nav.helse.epj.db

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.BeforeTest
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.core.db.dbQuery
import no.nav.helse.epj.api.Diagnose
import no.nav.helse.epj.api.DiagnoseSystem
import no.nav.helse.epj.api.KonsultasjonStatus
import no.nav.helse.epj.api.OppdaterKonsultasjonRequest
import no.nav.helse.epj.api.OpprettDiagnoseRequest
import no.nav.helse.epj.api.OpprettKonsultasjon
import no.nav.helse.utils.TestHelper
import no.nav.helse.utils.TestRepository
import org.junit.Ignore
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
@Ignore("ikke imeplementert")
class KonsultasjonRepositoryTest : TestRepository() {

  val testHelper = TestHelper()
  val konsultasjonRepository = KonsultasjonRepository()

  @BeforeTest fun setup() = runTest { dbQuery { deleteAllTestData() } }

  @Test
  fun `should create konsultasjon`() = runTest {
    val pasientId = Uuid.generateV4().toString()
    val legeId = Uuid.generateV4().toString()
    testHelper.insertHelsepersonellOgPasientTestData(pasientId, legeId)
    val opprettKonsultasjon =
      OpprettKonsultasjon(
        pasientId = pasientId,
        hpr = listOf("111"),
        startetTidspunkt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        status = KonsultasjonStatus.PÅGÅENDE,
      )
    konsultasjonRepository.createKonsultasjon(opprettKonsultasjon)
    val konsultasjon = konsultasjonRepository.getAktivKonsultasjon(pasientId)
    konsultasjon shouldNotBe null
    konsultasjon?.pasientId shouldBe pasientId
    konsultasjon?.hpr shouldBe listOf("111")
  }

  @Test
  fun `should not return an avsluttet konsultasjon as active`() = runTest {
    val pasientId = Uuid.generateV4().toString()
    val legeId = Uuid.generateV4().toString()
    val konsultasjonid = Uuid.generateV4()
    testHelper.insertHelsepersonellOgPasientTestData(pasientId, legeId)
    testHelper.insertKonsultasjonWithPredefinedId(legeId, pasientId, konsultasjonid)
    konsultasjonRepository.oppdaterKonsultasjon(
      OppdaterKonsultasjonRequest(
        konsultasjonId = konsultasjonid.toString(),
        diagnoser = emptyList(),
        journalNotat = "notat",
        ferdigstill = true,
      ),
      pasientId,
    )
    val konsultasjon = konsultasjonRepository.getAktivKonsultasjon(pasientId)
    konsultasjon shouldBe null
  }

  @Test
  fun `should return konsultasjoner with hpr list`() = runTest {
    val pasientId = Uuid.generateV4().toString()
    val legeId = Uuid.generateV4().toString()
    testHelper.insertHelsepersonellOgPasientTestData(pasientId, legeId)
    testHelper.createKonsultasjon(legeId, pasientId)
    testHelper.createKonsultasjon(legeId, pasientId)

    val result = konsultasjonRepository.getKonsultasjoner(pasientId)

    result.map { it.pasientId } shouldBe listOf(pasientId, pasientId)
  }

  @Test
  fun `should get diagnoser`() = runTest {
    val konsultasjonId = Uuid.generateV4()
    val pasientId = Uuid.generateV4().toString()
    val legeId = Uuid.generateV4().toString()
    testHelper.insertHelsepersonellOgPasientTestData(pasientId, legeId)
    testHelper.insertKonsultasjonWithPredefinedId(legeId, pasientId, konsultasjonId)
    val diagnoser =
      listOf(
        OpprettDiagnoseRequest(
          kode = "kode",
          system = DiagnoseSystem.ICD10,
          beskrivelse = "beskrivelse",
        ),
        OpprettDiagnoseRequest(
          kode = "kode2",
          system = DiagnoseSystem.ICD10,
          beskrivelse = "beskrivelse2",
        ),
      )
    diagnoser.forEach {
      konsultasjonRepository.insertDiagnoseIfNotExists(it, konsultasjonId.toString())
    }

    val result = konsultasjonRepository.getDiagnoser(konsultasjonId.toString())

    result shouldBe
      listOf(
        Diagnose(kode = "kode", system = DiagnoseSystem.ICD10, beskrivelse = "beskrivelse"),
        Diagnose(kode = "kode2", system = DiagnoseSystem.ICD10, beskrivelse = "beskrivelse2"),
      )
  }
}
