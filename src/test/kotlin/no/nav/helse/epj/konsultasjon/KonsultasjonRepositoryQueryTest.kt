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
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import org.junit.Test

class KonsultasjonRepositoryQueryTest : KonsultasjonRepositoryTestBase() {
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
                status = KonsultasjonStatus.PAAGAAENDE,
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
                KonsultasjonStatus.PAAGAAENDE,
            )
        )
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                pasientB,
                listOf(hpr),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
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
                OpprettKonsultasjon(pasientId, listOf(hpr), eldst, KonsultasjonStatus.FULLFOERT)
            )
        val nyesteId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(pasientId, listOf(hpr), nyest, KonsultasjonStatus.PAAGAAENDE)
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
                KonsultasjonStatus.PAAGAAENDE,
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
                    KonsultasjonStatus.FULLFOERT,
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
                KonsultasjonStatus.PAAGAAENDE,
            )
        )
        val nyesteId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                )
            )

        val aktiv = konsultasjonRepository.findActiveByPasientId(pasientId)
        assertNotNull(aktiv)
        assertEquals(nyesteId, aktiv.id)
    }

    @Test
    fun `findActiveByPasientIdAndHpr returns null when the doctor has no active konsultasjon`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val annenHpr = HelsepersonellHpr("456")
            val pasientId = opprettPasient(hpr = hpr)
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                )
            )

            assertNull(konsultasjonRepository.findActiveByPasientIdAndHpr(pasientId, annenHpr))
        }

    @Test
    fun `findActiveByPasientIdAndHpr lets two different doctors have simultaneous active konsultasjoner for the same patient`() =
        runTest {
            val legeA = HelsepersonellHpr("123")
            val legeB = HelsepersonellHpr("456")
            val pasientId = opprettPasient(hpr = legeA)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(legeA),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(legeB),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                    )
                )

            val aktivForA = konsultasjonRepository.findActiveByPasientIdAndHpr(pasientId, legeA)
            val aktivForB = konsultasjonRepository.findActiveByPasientIdAndHpr(pasientId, legeB)

            assertNotNull(aktivForA)
            assertNotNull(aktivForB)
            assertEquals(konsultasjonA, aktivForA.id)
            assertEquals(konsultasjonB, aktivForB.id)
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
                    KonsultasjonStatus.PAAGAAENDE,
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
                    KonsultasjonStatus.PAAGAAENDE,
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
}
