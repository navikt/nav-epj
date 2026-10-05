package no.nav.helse.epj.konsultasjon

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.KonsultasjonDiagnosekodeTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.core.utils.UgyldigDiagnoseException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.tsm.diagnoser.Diagnose
import no.nav.tsm.diagnoser.DiagnoseType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Test

class KonsultasjonRepositoryDiagnoseTest : KonsultasjonRepositoryTestBase() {
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
                    KonsultasjonStatus.PAAGAAENDE,
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
                    KonsultasjonStatus.PAAGAAENDE,
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
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            val diagnose = OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)

            val forsteInsert = konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId.value)
            val secondInsert = konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId.value)

            assertEquals(1, forsteInsert)
            assertEquals(0, secondInsert)
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
                        KonsultasjonStatus.PAAGAAENDE,
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
                    KonsultasjonStatus.PAAGAAENDE,
                )
            )

        val konsultasjonId2 =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                )
            )

        val diagnose = OpprettDiagnoseRequest(kode = "A01", system = DiagnoseType.ICPC2)

        val firstInsert = konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId1.value)

        val secondInsert = konsultasjonRepository.updateDiagnose(diagnose, konsultasjonId2.value)

        assertEquals(1, firstInsert)
        assertEquals(1, secondInsert)

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
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientB,
                        listOf(hpr),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
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
                        KonsultasjonStatus.PAAGAAENDE,
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
}
