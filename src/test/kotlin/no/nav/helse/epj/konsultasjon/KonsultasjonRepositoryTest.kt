package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.DiagnoseTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.legekontor.LegekontorRepository
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientRepository
import no.nav.helse.utils.WithPostgresql
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Test

class KonsultasjonRepositoryTest : WithPostgresql() {
  init {
    runMigrations(true)
    connect()
    runBlocking { LegekontorRepository().insertLegekontor(Legekontor.DEFAULT.id.value) }
  }

  val konsultasjonRepository = KonsultasjonRepository()
  val pasientRepository = PasientRepository()

  private suspend fun opprettPasient(
    pasientId: PasientId = PasientId(Uuid.generateV4()),
    hpr: HelsepersonellHpr = HelsepersonellHpr("123"),
  ): PasientId {
    pasientRepository.insert(
      Pasient(
        id = pasientId,
        legekontorId = Legekontor.DEFAULT.id,
        fornavn = "fornavn",
        etternavn = "etternavn",
        fnr = "fnr-${pasientId.value}",
        hprNumbers = listOf(hpr),
      )
    )
    return pasientId
  }

  @Test
  fun `finds no konsultasjon`() = runTest {
    val pasientId = PasientId(Uuid.generateV4())
    val konsultasjon = konsultasjonRepository.listByPasientId(pasientId)
    assertEquals(0, konsultasjon.size)
  }

  @Test
  fun `finds one konsultasjon`() = runTest {
    val pasientId = PasientId(Uuid.generateV4())
    val hpr = HelsepersonellHpr("123")
    pasientRepository.insert(
      Pasient(
        id = pasientId,
        legekontorId = Legekontor.DEFAULT.id,
        fornavn = "fornavn",
        etternavn = "etternavn",
        fnr = "fnr",
        hprNumbers = listOf(hpr),
      )
    )
    konsultasjonRepository.insert(
      OpprettKonsultasjon(
        pasientId = pasientId,
        hpr = listOf(hpr),
        startetTidspunkt = LocalDateTime.now(),
        status = KonsultasjonStatus.PÅGÅENDE,
      )
    )
    val konsultasjon = konsultasjonRepository.listByPasientId(pasientId)
    assertEquals(1, konsultasjon.size)
  }

  @Test
  fun `listByPasientId returns only konsultasjoner for the correct patient`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientA = opprettPasient(hpr = hpr)
    val pasientB = opprettPasient(hpr = hpr)
    konsultasjonRepository.insert(
      OpprettKonsultasjon(pasientA, listOf(hpr), LocalDateTime.now(), KonsultasjonStatus.PÅGÅENDE)
    )
    konsultasjonRepository.insert(
      OpprettKonsultasjon(pasientB, listOf(hpr), LocalDateTime.now(), KonsultasjonStatus.PÅGÅENDE)
    )

    val konsultasjonerA = konsultasjonRepository.listByPasientId(pasientA)
    assertEquals(1, konsultasjonerA.size)
    assertEquals(pasientA, konsultasjonerA.single().pasientId)
  }

  @Test
  fun `listByPasientId sorts konsultasjoner descending by startetTidspunkt`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val eldst = LocalDateTime.now().minusHours(2)
    val nyest = LocalDateTime.now()
    val eldstId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(pasientId, listOf(hpr), eldst, KonsultasjonStatus.FULLFØRT)
      )
    val nyesteId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(pasientId, listOf(hpr), nyest, KonsultasjonStatus.PÅGÅENDE)
      )

    val konsultasjoner = konsultasjonRepository.listByPasientId(pasientId)
    assertEquals(2, konsultasjoner.size)
    assertEquals(nyesteId, konsultasjoner[0].id)
    assertEquals(eldstId, konsultasjoner[1].id)
  }

  @Test
  fun `insert stores multiple hpr for a konsultasjon`() = runTest {
    val hprA = HelsepersonellHpr("123")
    val hprB = HelsepersonellHpr("456")
    val pasientId = opprettPasient(hpr = hprA)
    konsultasjonRepository.insert(
      OpprettKonsultasjon(
        pasientId,
        listOf(hprA, hprB),
        LocalDateTime.now(),
        KonsultasjonStatus.PÅGÅENDE,
      )
    )

    val konsultasjon = konsultasjonRepository.listByPasientId(pasientId).single()
    assertEquals(setOf("123", "456"), konsultasjon.hpr.toSet())
  }

  @Test
  fun `insert with an empty hpr list gives a konsultasjon without healthcare personnel`() =
    runTest {
      val pasientId = opprettPasient()
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          emptyList(),
          LocalDateTime.now(),
          KonsultasjonStatus.PLANLAGT,
        )
      )

      val konsultasjon = konsultasjonRepository.listByPasientId(pasientId).single()
      assertTrue(konsultasjon.hpr.isEmpty())
    }

  @Test
  fun `findActiveByPasientId returns null when no konsultasjoner exist`() = runTest {
    val pasientId = opprettPasient()
    assertNull(konsultasjonRepository.findActiveByPasientId(pasientId))
  }

  @Test
  fun `findActiveByPasientId ignores completed konsultasjoner`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val oppdaterKonsultasjonRequest =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          java.time.LocalDateTime.now(),
          KonsultasjonStatus.FULLFØRT,
        )
      )
    konsultasjonRepository.update(
      OppdaterKonsultasjonRequest(
        oppdaterKonsultasjonRequest,
        emptyList(),
        null,
        ferdigstill = true,
      ),
      pasientId,
    )

    assertNull(konsultasjonRepository.findActiveByPasientId(pasientId))
  }

  @Test
  fun `findActiveByPasientId returns the newest active konsultasjon`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    konsultasjonRepository.insert(
      OpprettKonsultasjon(
        pasientId,
        listOf(hpr),
        LocalDateTime.now().minusHours(2),
        KonsultasjonStatus.PÅGÅENDE,
      )
    )
    val nyesteId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )

    val aktiv = konsultasjonRepository.findActiveByPasientId(pasientId)
    assertNotNull(aktiv)
    assertEquals(nyesteId, aktiv.id)
  }

  @Test
  fun `findByKonsultasjonId returns null for an unknown id`() = runTest {
    assertNull(konsultasjonRepository.findByKonsultasjonId(KonsultasjonId(Uuid.generateV4())))
  }

  @Test
  fun `findByKonsultasjonId returns the konsultasjon for a known id`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )

    val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
    assertNotNull(konsultasjon)
    assertEquals(konsultasjonId, konsultasjon.id)
  }

  @Test
  fun `update returns 0 rows and makes no changes when pasientId does not own the konsultasjon`() =
    runTest {
      val hpr = HelsepersonellHpr("123")
      val pasientId = opprettPasient(hpr = hpr)
      val annenPasientId = opprettPasient(hpr = hpr)
      val konsultasjonId =
        konsultasjonRepository.insert(
          OpprettKonsultasjon(
            pasientId,
            listOf(hpr),
            LocalDateTime.now(),
            KonsultasjonStatus.PÅGÅENDE,
          )
        )

      val updatedRows =
        konsultasjonRepository.update(
          OppdaterKonsultasjonRequest(
            konsultasjonId = konsultasjonId,
            diagnoser =
              listOf(
                OpprettDiagnoseRequest(
                  kode = "A01",
                  system = DiagnoseSystem.ICPC2,
                  beskrivelse = "",
                )
              ),
            journalNotat = "notat",
            ferdigstill = true,
          ),
          annenPasientId,
        )

      assertEquals(0, updatedRows)
    }

  @Test
  fun `update adds a new journalnotat, stores diagnose and completes konsultasjon`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )
    val updatedRows =
      konsultasjonRepository.update(
        OppdaterKonsultasjonRequest(
          konsultasjonId = konsultasjonId,
          diagnoser =
            listOf(
              OpprettDiagnoseRequest(kode = "A01", system = DiagnoseSystem.ICPC2, beskrivelse = "")
            ),
          journalNotat = "oppdatert notat",
          ferdigstill = true,
        ),
        pasientId,
      )

    // 1 for ny diagnose + 1 for nytt journalnotat (insert) + 1 for ferdigstilt konsultasjon
    assertEquals(3, updatedRows)
    val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
    assertNotNull(konsultasjon)
    assertEquals(KonsultasjonStatus.FULLFØRT, konsultasjon.status)
    assertNotNull(konsultasjon.avsluttetTidspunkt)
    assertEquals(1, konsultasjon.journalnotat.size)
    assertEquals("oppdatert notat", konsultasjon.journalnotat.last().journalnotat)
  }

  @Test
  fun `update leaves the konsultasjon open when ferdigstill is false`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )

    konsultasjonRepository.update(
      OppdaterKonsultasjonRequest(konsultasjonId, emptyList(), "notat", ferdigstill = false),
      pasientId,
    )

    val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
    assertNotNull(konsultasjon)
    assertEquals(KonsultasjonStatus.PÅGÅENDE, konsultasjon.status)
    assertNull(konsultasjon.avsluttetTidspunkt)
  }

  @Test
  fun `konsultasjon retrieves an empty list of diagnoses when there are none`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )

    val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)

    assertEquals(emptyList(), konsultasjon?.diagnoser)
  }

  @Test
  fun `update throws UgyldigDiagnoseException for an unknown diagnosis code`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )

    assertFailsWith<UgyldigDiagnoseException> {
      konsultasjonRepository.update(
        OppdaterKonsultasjonRequest(
          konsultasjonId = konsultasjonId,
          diagnoser =
            listOf(
              OpprettDiagnoseRequest(
                kode = "IKKE-EN-GYLDIG-KODE",
                system = DiagnoseSystem.ICPC2,
                beskrivelse = "",
              )
            ),
          journalNotat = "notat",
          ferdigstill = false,
        ),
        pasientId,
      )
    }
  }

  @Test
  fun `updateDiagnose does not store the same diagnose twice on the same konsultasjon`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )
    val diagnose =
      OpprettDiagnoseRequest(kode = "A01", system = DiagnoseSystem.ICPC2, beskrivelse = "")

    val forsteInsert =
      konsultasjonRepository.updateDiagnose(diagnose, pasientId.value, konsultasjonId.value)
    val andreInsert =
      konsultasjonRepository.updateDiagnose(diagnose, pasientId.value, konsultasjonId.value)

    assertEquals(1, forsteInsert)
    assertEquals(0, andreInsert)
    val antallDiagnoser = dbQuery {
      DiagnoseTable.selectAll()
        .where { DiagnoseTable.konsultasjonId eq konsultasjonId.value }
        .count()
    }
    assertEquals(1, antallDiagnoser)
  }

  @Test
  fun `updateDiagnose stores the same diagnose on different konsultasjoner`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)

    val konsultasjonId1 =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )

    val konsultasjonId2 =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(
          pasientId,
          listOf(hpr),
          LocalDateTime.now(),
          KonsultasjonStatus.PÅGÅENDE,
        )
      )

    val diagnose =
      OpprettDiagnoseRequest(kode = "A01", system = DiagnoseSystem.ICPC2, beskrivelse = "")

    val førsteInsert =
      konsultasjonRepository.updateDiagnose(diagnose, pasientId.value, konsultasjonId1.value)

    val andreInsert =
      konsultasjonRepository.updateDiagnose(diagnose, pasientId.value, konsultasjonId2.value)

    assertEquals(1, førsteInsert)
    assertEquals(1, andreInsert)

    val lagredeDiagnoser = konsultasjonRepository.listDiagnoser(pasientId)

    assertEquals(2, lagredeDiagnoser.size)
  }
}
