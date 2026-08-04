package no.nav.helse.epj.konsultasjon

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.helse.core.db.DiagnoseTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientRepository
import no.nav.helse.epj.pasient.PatientId
import no.nav.helse.utils.WithPostgresql
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Test

class KonsultasjonRepositoryTest : WithPostgresql() {
  init {
    runMigrations(true)
    connect()
  }

  val konsultasjonRepository = KonsultasjonRepository()
  val pasientRepository = PasientRepository()

  private suspend fun opprettPasient(
    pasientId: PatientId = PatientId(Uuid.generateV4()),
    hpr: HelsepersonellHpr = HelsepersonellHpr("123"),
  ): PatientId {
    pasientRepository.insert(
      Pasient(
        id = pasientId,
        legekontorId = Legekontor.DEFAULT.id,
        hpr = hpr,
        navn = "navn",
        fnr = "fnr-${pasientId.value}",
      )
    )
    return pasientId
  }

  private fun now(offset: kotlin.time.Duration = kotlin.time.Duration.ZERO): LocalDateTime =
    (Clock.System.now() - offset).toLocalDateTime(TimeZone.currentSystemDefault())

  /*  // Legger til en journalnotat-rad direkte i databasen for å simulere at det finnes et
  // tidligere notat på konsultasjonen. `update` oppretter alltid en ny journalnotat-rad
  // (ren insert) og erstatter ikke eksisterende notater.
  private suspend fun opprettJournalnotat(
    konsultasjonId: KonsultasjonId,
    pasientId: PasientId,
    tekst: String? = null,
  ) = dbQuery {
    JournalnotatTable.insert {
      it[id] = Uuid.generateV4()
      it[JournalnotatTable.konsultasjonId] = konsultasjonId.value
      it[JournalnotatTable.pasientId] = pasientId.value
      it[journalnotat] = tekst
    }
  }*/

  @Test
  fun `finner ingen konsultasjon`() = runTest {
    val pasientId = PatientId(Uuid.generateV4())
    val konsultasjon = konsultasjonRepository.listByPasientId(pasientId)
    assertEquals(0, konsultasjon.size)
  }

  @Test
  fun `finner en konsultasjon`() = runTest {
    val pasientId = PatientId(Uuid.generateV4())
    val hpr = HelsepersonellHpr("123")
    pasientRepository.insert(
      Pasient(
        id = pasientId,
        legekontorId = Legekontor.DEFAULT.id,
        hpr = hpr,
        navn = "navn",
        fnr = "fnr",
      )
    )
    konsultasjonRepository.insert(
      OpprettKonsultasjon(
        pasientId = pasientId,
        hpr = listOf(hpr),
        startetTidspunkt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        status = KonsultasjonStatus.PÅGÅENDE,
      )
    )
    val konsultasjon = konsultasjonRepository.listByPasientId(pasientId)
    assertEquals(1, konsultasjon.size)
  }

  @Test
  fun `listByPasientId returnerer kun konsultasjoner for riktig pasient`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientA = opprettPasient(hpr = hpr)
    val pasientB = opprettPasient(hpr = hpr)
    konsultasjonRepository.insert(
      OpprettKonsultasjon(pasientA, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
    )
    konsultasjonRepository.insert(
      OpprettKonsultasjon(pasientB, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
    )

    val konsultasjonerA = konsultasjonRepository.listByPasientId(pasientA)
    assertEquals(1, konsultasjonerA.size)
    assertEquals(pasientA, konsultasjonerA.single().pasientId)
  }

  @Test
  fun `listByPasientId sorterer konsultasjoner synkende etter startetTidspunkt`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val eldst = now(2.hours)
    val nyest = now()
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
  fun `insert lagrer flere hpr for en konsultasjon`() = runTest {
    val hprA = HelsepersonellHpr("123")
    val hprB = HelsepersonellHpr("456")
    val pasientId = opprettPasient(hpr = hprA)
    konsultasjonRepository.insert(
      OpprettKonsultasjon(pasientId, listOf(hprA, hprB), now(), KonsultasjonStatus.PÅGÅENDE)
    )

    val konsultasjon = konsultasjonRepository.listByPasientId(pasientId).single()
    assertEquals(setOf("123", "456"), konsultasjon.hpr.toSet())
  }

  @Test
  fun `insert med tom hpr-liste gir konsultasjon uten helsepersonell`() = runTest {
    val pasientId = opprettPasient()
    konsultasjonRepository.insert(
      OpprettKonsultasjon(pasientId, emptyList(), now(), KonsultasjonStatus.PLANLAGT)
    )

    val konsultasjon = konsultasjonRepository.listByPasientId(pasientId).single()
    assertTrue(konsultasjon.hpr.isEmpty())
  }

  @Test
  fun `findActiveByPasientId returnerer null når det ikke finnes noen konsultasjoner`() = runTest {
    val pasientId = opprettPasient()
    assertNull(konsultasjonRepository.findActiveByPasientId(pasientId))
  }

  @Test
  fun `findActiveByPasientId ignorerer avsluttede konsultasjoner`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val oppdaterKonsultasjonRequest =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(pasientId, listOf(hpr), now(1.hours), KonsultasjonStatus.FULLFØRT)
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
  fun `findActiveByPasientId returnerer nyeste aktive konsultasjon`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    konsultasjonRepository.insert(
      OpprettKonsultasjon(pasientId, listOf(hpr), now(2.hours), KonsultasjonStatus.PÅGÅENDE)
    )
    val nyesteId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(pasientId, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
      )

    val aktiv = konsultasjonRepository.findActiveByPasientId(pasientId)
    assertNotNull(aktiv)
    assertEquals(nyesteId, aktiv.id)
  }

  @Test
  fun `findByKonsultasjonId returnerer null for ukjent id`() = runTest {
    assertNull(konsultasjonRepository.findByKonsultasjonId(KonsultasjonId(Uuid.generateV4())))
  }

  @Test
  fun `findByKonsultasjonId returnerer konsultasjon for kjent id`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(pasientId, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
      )

    val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
    assertNotNull(konsultasjon)
    assertEquals(konsultasjonId, konsultasjon.id)
  }

  @Test
  fun `update returnerer 0 rader og gjør ingen endringer når pasientId ikke eier konsultasjonen`() =
    runTest {
      val hpr = HelsepersonellHpr("123")
      val pasientId = opprettPasient(hpr = hpr)
      val annenPasientId = opprettPasient(hpr = hpr)
      val konsultasjonId =
        konsultasjonRepository.insert(
          OpprettKonsultasjon(pasientId, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
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
  fun `update legger til nytt journalnotat, lagrer diagnose og ferdigstiller konsultasjon`() =
    runTest {
      val hpr = HelsepersonellHpr("123")
      val pasientId = opprettPasient(hpr = hpr)
      val konsultasjonId =
        konsultasjonRepository.insert(
          OpprettKonsultasjon(pasientId, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
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
  fun `update lar konsultasjonen forbli åpen når ferdigstill er false`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(pasientId, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
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
  fun `update kaster UgyldigDiagnoseException for ukjent diagnosekode`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(pasientId, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
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
  fun `updateDiagnose lagrer samme diagnose to ganger`() = runTest {
    val hpr = HelsepersonellHpr("123")
    val pasientId = opprettPasient(hpr = hpr)
    val konsultasjonId =
      konsultasjonRepository.insert(
        OpprettKonsultasjon(pasientId, listOf(hpr), now(), KonsultasjonStatus.PÅGÅENDE)
      )
    val diagnose =
      OpprettDiagnoseRequest(kode = "A01", system = DiagnoseSystem.ICPC2, beskrivelse = "")

    val forsteInsert =
      konsultasjonRepository.updateDiagnose(diagnose, pasientId.value, konsultasjonId.value)
    val andreInsert =
      konsultasjonRepository.updateDiagnose(diagnose, pasientId.value, konsultasjonId.value)

    assertEquals(1, forsteInsert)
    assertEquals(1, andreInsert)
    val antallDiagnoser = dbQuery {
      DiagnoseTable.selectAll()
        .where { DiagnoseTable.konsultasjonId eq konsultasjonId.value }
        .count()
    }
    assertEquals(2, antallDiagnoser)
  }
}
