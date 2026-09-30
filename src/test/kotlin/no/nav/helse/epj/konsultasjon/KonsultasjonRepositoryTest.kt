package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.KonsultasjonDiagnosekodeTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientRepository
import no.nav.helse.utils.WithPostgresql
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
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

    @OptIn(ExperimentalUuidApi::class)
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
                personident = "personident-${pasientId.value}",
                hprNumbers = listOf(hpr),
            )
        )
        return pasientId
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `finds no konsultasjon`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjon = konsultasjonRepository.listByPasientId(pasientId)
        assertEquals(0, konsultasjon.size)
    }

    @OptIn(ExperimentalUuidApi::class)
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
                personident = "personident",
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
            OpprettKonsultasjon(
                pasientA,
                listOf(hpr),
                LocalDateTime.now(),
                KonsultasjonStatus.PÅGÅENDE,
            )
        )
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                pasientB,
                listOf(hpr),
                LocalDateTime.now(),
                KonsultasjonStatus.PÅGÅENDE,
            )
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

    @OptIn(ExperimentalUuidApi::class)
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
    fun `insert derives legekontorId from the konsultasjon's pasient`() = runTest {
        val pasientId = opprettPasient()
        val konsultasjonId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    emptyList(),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PÅGÅENDE,
                )
            )

        val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
        assertNotNull(konsultasjon)
        assertEquals(Legekontor.DEFAULT.id, konsultasjon.legekontorId)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `the database rejects a konsultasjon without an organization`() = runTest {
        val pasientId = opprettPasient()
        val konsultasjonId = Uuid.generateV4()

        assertFailsWith<java.sql.SQLException> {
            java.sql.DriverManager.getConnection(
                    config.postgres.url,
                    config.postgres.username,
                    config.postgres.password,
                )
                .use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute(
                            """
                            INSERT INTO konsultasjon (id, pasient_id, legekontor_id, startet_tidspunkt, status)
                            VALUES ('$konsultasjonId', '${pasientId.value}', NULL, now(), 'PÅGÅENDE')
                            """
                                .trimIndent()
                        )
                    }
                }
        }
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
                                OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)
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
                        listOf(OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)),
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
        assertEquals(1, konsultasjon.diagnoser.size)
        val lagretDiagnose = konsultasjon.diagnoser.single()
        assertEquals(DiagnoseType.ICPC2, lagretDiagnose.system)
        assertEquals("A01", lagretDiagnose.code)
        assertEquals(Diagnose.from(DiagnoseType.ICPC2, "A01")!!.text, lagretDiagnose.text)
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
    fun `update saving the same journalnotat text twice does not create a duplicate row`() =
        runTest {
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
            val request =
                OppdaterKonsultasjonRequest(
                    konsultasjonId,
                    emptyList(),
                    "notat",
                    ferdigstill = false,
                )

            konsultasjonRepository.update(request, pasientId)
            konsultasjonRepository.update(request, pasientId)

            val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
            assertNotNull(konsultasjon)
            assertEquals(1, konsultasjon.journalnotat.size)
            assertEquals("notat", konsultasjon.journalnotat.single().journalnotat)
        }

    @Test
    fun `update saving a changed journalnotat text updates the existing row instead of adding one`() =
        runTest {
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
                OppdaterKonsultasjonRequest(
                    konsultasjonId,
                    emptyList(),
                    "første notat",
                    ferdigstill = false,
                ),
                pasientId,
            )
            konsultasjonRepository.update(
                OppdaterKonsultasjonRequest(
                    konsultasjonId,
                    emptyList(),
                    "oppdatert notat",
                    ferdigstill = false,
                ),
                pasientId,
            )

            val konsultasjon = konsultasjonRepository.findByKonsultasjonId(konsultasjonId)
            assertNotNull(konsultasjon)
            assertEquals(1, konsultasjon.journalnotat.size)
            assertEquals("oppdatert notat", konsultasjon.journalnotat.single().journalnotat)
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
                                system = DiagnoseType.ICPC2,
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
    fun `updateDiagnose does not store the same diagnose twice on the same konsultasjon`() =
        runTest {
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
            val diagnose = OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)

            val forsteInsert = konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId.value)
            val andreInsert = konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId.value)

            assertEquals(1, forsteInsert)
            assertEquals(0, andreInsert)
            val antallDiagnoser = dbQuery {
                KonsultasjonDiagnosekodeTable.selectAll()
                    .where { KonsultasjonDiagnosekodeTable.konsultasjonId eq konsultasjonId.value }
                    .count()
            }
            assertEquals(1, antallDiagnoser)
        }

    @Test
    fun `updateDiagnose stores the katalog's canonical ICD10 code regardless of dot formatting`() =
        runTest {
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

            val utenPunktum =
                konsultasjonRepository.updateDiagnose(
                    OpprettDiagnoseRequest(kode = "A000", system = DiagnoseType.ICD10),
                    konsultasjonId.value,
                )
            val medPunktum =
                konsultasjonRepository.updateDiagnose(
                    OpprettDiagnoseRequest(kode = "A00.0", system = DiagnoseType.ICD10),
                    konsultasjonId.value,
                )

            assertEquals(1, utenPunktum)
            assertEquals(0, medPunktum)
            val lagredeDiagnoser = konsultasjonRepository.listDiagnoser(konsultasjonId)
            assertEquals(1, lagredeDiagnoser.size)
            assertEquals("A00.0", lagredeDiagnoser.single().code)
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

        val diagnose = OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)

        val førsteInsert = konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId1.value)

        val andreInsert = konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId2.value)

        assertEquals(1, førsteInsert)
        assertEquals(1, andreInsert)

        val lagredeDiagnoser = konsultasjonRepository.listDiagnoser(pasientId)

        assertEquals(2, lagredeDiagnoser.size)
    }

    @Test
    fun `listDiagnoser by pasientId only includes diagnoser for konsultasjoner owned by that patient`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientA = opprettPasient(hpr = hpr)
            val pasientB = opprettPasient(hpr = hpr)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientA,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PÅGÅENDE,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientB,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PÅGÅENDE,
                    )
                )
            konsultasjonRepository.updateDiagnose(
                OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2),
                konsultasjonA.value,
            )
            konsultasjonRepository.updateDiagnose(
                OpprettDiagnoseRequest(kode = "A02", system = DiagnoseType.ICPC2),
                konsultasjonB.value,
            )

            val diagnoserForA = konsultasjonRepository.listDiagnoser(pasientA)

            assertEquals(1, diagnoserForA.size)
            assertEquals("A01", diagnoserForA.single().code)
        }

    @Test
    fun `listDiagnoser resolves official catalogue text for both patient and konsultasjon lookups`() =
        runTest {
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
            val diagnose = OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)

            konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId.value)

            val forventetTekst = Diagnose.from(DiagnoseType.ICPC2, "A01")!!.text
            val diagnoserByPasient = konsultasjonRepository.listDiagnoser(pasientId)
            val diagnoserByKonsultasjon = konsultasjonRepository.listDiagnoser(konsultasjonId)

            assertEquals(forventetTekst, diagnoserByPasient.single().text)
            assertEquals(forventetTekst, diagnoserByKonsultasjon.single().text)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat stores a complete round trip of a journalnotat`() = runTest {
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
        val id = JournalnotatId(Uuid.generateV4())
        val request =
            OpprettJournalnotatRequest(
                pasientId = pasientId,
                konsultasjonId = konsultasjonId,
                journalnotat = "notat",
            )

        val opprettet = konsultasjonRepository.opprettJournalnotat(id, request)

        assertEquals(id, opprettet.id)
        assertEquals(pasientId, opprettet.pasientId)
        assertEquals(konsultasjonId, opprettet.konsultasjonId)
        assertEquals("notat", opprettet.journalnotat)
        val lagret = konsultasjonRepository.findJournalnotat(id)
        assertEquals("notat", lagret?.journalnotat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat throws KonsultasjonNotFoundException for an unknown encounter`() =
        runTest {
            val pasientId = opprettPasient()
            val request =
                OpprettJournalnotatRequest(
                    pasientId = pasientId,
                    konsultasjonId = KonsultasjonId(Uuid.generateV4()),
                    journalnotat = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.KonsultasjonNotFoundException> {
                konsultasjonRepository.opprettJournalnotat(
                    JournalnotatId(Uuid.generateV4()),
                    request,
                )
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat throws KonsultasjonTilhorerAnnenPasientException when the encounter belongs to another patient`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val eier = opprettPasient(hpr = hpr)
            val konsultasjonId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        eier,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PÅGÅENDE,
                    )
                )
            val annenPasient = opprettPasient(hpr = hpr)
            val request =
                OpprettJournalnotatRequest(
                    pasientId = annenPasient,
                    konsultasjonId = konsultasjonId,
                    journalnotat = "notat",
                )

            assertFailsWith<no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException> {
                konsultasjonRepository.opprettJournalnotat(
                    JournalnotatId(Uuid.generateV4()),
                    request,
                )
            }
            assertNull(konsultasjonRepository.findJournalnotat(JournalnotatId(Uuid.generateV4())))
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettJournalnotat with a colliding id throws DuplikatJournalnotatException and leaves data unchanged`() =
        runTest {
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
            val id = JournalnotatId(Uuid.generateV4())
            val request =
                OpprettJournalnotatRequest(
                    pasientId = pasientId,
                    konsultasjonId = konsultasjonId,
                    journalnotat = "original",
                )
            konsultasjonRepository.opprettJournalnotat(id, request)

            assertFailsWith<no.nav.helse.core.utils.DuplikatJournalnotatException> {
                konsultasjonRepository.opprettJournalnotat(
                    id,
                    request.copy(journalnotat = "endret"),
                )
            }

            assertEquals("original", konsultasjonRepository.findJournalnotat(id)?.journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat by pasientId only returns journalnotater for the correct patient`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientA = opprettPasient(hpr = hpr)
            val pasientB = opprettPasient(hpr = hpr)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientA,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PÅGÅENDE,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientB,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PÅGÅENDE,
                    )
                )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientA, konsultasjonA, "for A"),
            )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientB, konsultasjonB, "for B"),
            )

            val notaterForA = konsultasjonRepository.listJournalnotat(pasientA, null)

            assertEquals(1, notaterForA.size)
            assertEquals("for A", notaterForA.single().journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat filters by encounter when both patient and encounter are given`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientId = opprettPasient(hpr = hpr)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PÅGÅENDE,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PÅGÅENDE,
                    )
                )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientId, konsultasjonA, "for A"),
            )
            konsultasjonRepository.opprettJournalnotat(
                JournalnotatId(Uuid.generateV4()),
                OpprettJournalnotatRequest(pasientId, konsultasjonB, "for B"),
            )

            val notater = konsultasjonRepository.listJournalnotat(pasientId, konsultasjonA)

            assertEquals(1, notater.size)
            assertEquals("for A", notater.single().journalnotat)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `listJournalnotat returns an empty list when the patient has no journalnotater`() =
        runTest {
            val pasientId = opprettPasient()

            val notater = konsultasjonRepository.listJournalnotat(pasientId, null)

            assertTrue(notater.isEmpty())
        }

    private suspend fun opprettKonsultasjon(pasientId: PasientId): KonsultasjonId =
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                pasientId,
                listOf(HelsepersonellHpr("123")),
                LocalDateTime.now(),
                KonsultasjonStatus.PÅGÅENDE,
            )
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat rejects an encounter that belongs to another patient`() = runTest {
        val eier = opprettPasient()
        val konsultasjonId = opprettKonsultasjon(eier)
        val annenPasient = opprettPasient()
        val id = JournalnotatId(Uuid.generateV4())

        assertFailsWith<no.nav.helse.core.utils.KonsultasjonTilhorerAnnenPasientException> {
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, konsultasjonId, annenPasient, "notat")
            )
        }
        assertNull(konsultasjonRepository.findJournalnotat(id))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat does not move an existing journalnotat to another konsultasjon`() =
        runTest {
            val eier = opprettPasient()
            val eierKonsultasjon = opprettKonsultasjon(eier)
            val id = JournalnotatId(Uuid.generateV4())
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, eierKonsultasjon, eier, "original")
            )
            val angriper = opprettPasient()
            val angriperKonsultasjon = opprettKonsultasjon(angriper)

            val rows =
                konsultasjonRepository.insertJournalnotat(
                    Journalnotat(id, angriperKonsultasjon, angriper, "overskrevet")
                )

            assertEquals(0, rows)
            val lagret = konsultasjonRepository.findJournalnotat(id)
            assertEquals("original", lagret?.journalnotat)
            assertEquals(eier, lagret?.pasientId)
            assertEquals(eierKonsultasjon, lagret?.konsultasjonId)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insertJournalnotat updates the text of an existing journalnotat in the same konsultasjon`() =
        runTest {
            val pasientId = opprettPasient()
            val konsultasjonId = opprettKonsultasjon(pasientId)
            val id = JournalnotatId(Uuid.generateV4())
            konsultasjonRepository.insertJournalnotat(
                Journalnotat(id, konsultasjonId, pasientId, "første")
            )

            val rows =
                konsultasjonRepository.insertJournalnotat(
                    Journalnotat(id, konsultasjonId, pasientId, "andre")
                )

            assertEquals(1, rows)
            assertEquals("andre", konsultasjonRepository.findJournalnotat(id)?.journalnotat)
        }
}
