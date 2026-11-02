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
import no.nav.helse.epj.clinic.ClinicRepository
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class KonsultasjonRepositoryQueryTest : KonsultasjonRepositoryTestBase() {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `finds no konsultasjon`() = runTest {
        val pasientId = PasientId(Uuid.generateV4())
        val konsultasjon = konsultasjonRepository.listByPasientId(pasientId, DEFAULT_ORG_NUMBER)
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
                fornavn = "fornavn",
                etternavn = "etternavn",
                personident = "personident",
            )
        )
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                pasientId = pasientId,
                hpr = listOf(hpr),
                startetTidspunkt = LocalDateTime.now(),
                status = KonsultasjonStatus.PAAGAAENDE,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        val konsultasjon = konsultasjonRepository.listByPasientId(pasientId, DEFAULT_ORG_NUMBER)
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
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                pasientB,
                listOf(hpr),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )

        val konsultasjonerA = konsultasjonRepository.listByPasientId(pasientA, DEFAULT_ORG_NUMBER)
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
                OpprettKonsultasjon(
                    pasientId,
                    listOf(hpr),
                    eldst,
                    KonsultasjonStatus.FULLFOERT,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )
        val nyesteId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    listOf(hpr),
                    nyest,
                    KonsultasjonStatus.PAAGAAENDE,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val konsultasjoner = konsultasjonRepository.listByPasientId(pasientId, DEFAULT_ORG_NUMBER)
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
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )

        val konsultasjon =
            konsultasjonRepository.listByPasientId(pasientId, DEFAULT_ORG_NUMBER).single()
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
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

            val konsultasjon =
                konsultasjonRepository.listByPasientId(pasientId, DEFAULT_ORG_NUMBER).single()
            assertTrue(konsultasjon.hpr.isEmpty())
        }

    @Test
    fun `findActiveByPasientId returns null when no konsultasjoner exist`() = runTest {
        val pasientId = opprettPasient()
        assertNull(konsultasjonRepository.findActiveByPasientId(pasientId, DEFAULT_ORG_NUMBER))
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
                    orgNumber = DEFAULT_ORG_NUMBER,
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
            DEFAULT_ORG_NUMBER,
        )

        assertNull(konsultasjonRepository.findActiveByPasientId(pasientId, DEFAULT_ORG_NUMBER))
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
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        val nyesteId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val aktiv = konsultasjonRepository.findActiveByPasientId(pasientId, DEFAULT_ORG_NUMBER)
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
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

            assertNull(
                konsultasjonRepository.findActiveByPasientIdAndHpr(
                    pasientId,
                    annenHpr,
                    DEFAULT_ORG_NUMBER,
                )
            )
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
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(legeB),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )

            val aktivForA =
                konsultasjonRepository.findActiveByPasientIdAndHpr(
                    pasientId,
                    legeA,
                    DEFAULT_ORG_NUMBER,
                )
            val aktivForB =
                konsultasjonRepository.findActiveByPasientIdAndHpr(
                    pasientId,
                    legeB,
                    DEFAULT_ORG_NUMBER,
                )

            assertNotNull(aktivForA)
            assertNotNull(aktivForB)
            assertEquals(konsultasjonA, aktivForA.id)
            assertEquals(konsultasjonB, aktivForB.id)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `findByKonsultasjonId returns null for an unknown id`() = runTest {
        assertNull(
            konsultasjonRepository.findByKonsultasjonId(
                KonsultasjonId(Uuid.generateV4()),
                DEFAULT_ORG_NUMBER,
            )
        )
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
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val konsultasjon =
            konsultasjonRepository.findByKonsultasjonId(konsultasjonId, DEFAULT_ORG_NUMBER)
        assertNotNull(konsultasjon)
        assertEquals(konsultasjonId, konsultasjon.id)
    }

    @Test
    fun `insert places the konsultasjon at the clinic with the given org number`() = runTest {
        val pasientId = opprettPasient()
        val konsultasjonId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    pasientId,
                    emptyList(),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val konsultasjon =
            konsultasjonRepository.findByKonsultasjonId(konsultasjonId, DEFAULT_ORG_NUMBER)
        assertNotNull(konsultasjon)
        assertEquals(
            ClinicRepository().findByOrgNumber(DEFAULT_ORG_NUMBER)?.id,
            konsultasjon.clinicId,
        )
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
                            INSERT INTO consultation (id, patient_id, clinic_id, started_at, status)
                            VALUES ('$konsultasjonId', '${pasientId.value}', NULL, now(), 'PÅGÅENDE')
                            """
                                .trimIndent()
                        )
                    }
                }
        }
    }

    @Test
    fun `findActive ignores avlyst and planlagt konsultasjoner without avsluttet tidspunkt`() =
        runTest {
            val hpr = HelsepersonellHpr("123")
            val pasientId = opprettPasient(hpr = hpr)
            listOf(KonsultasjonStatus.AVLYST, KonsultasjonStatus.PLANLAGT).forEach {
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now().minusDays(1),
                        it,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            }

            assertNull(konsultasjonRepository.findActiveByPasientId(pasientId, DEFAULT_ORG_NUMBER))
            assertNull(
                konsultasjonRepository.findActiveByPasientIdAndHpr(
                    pasientId,
                    hpr,
                    DEFAULT_ORG_NUMBER,
                )
            )

            val pagaaendeId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        pasientId,
                        listOf(hpr),
                        LocalDateTime.now().minusDays(2),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )

            assertEquals(
                pagaaendeId,
                konsultasjonRepository
                    .findActiveByPasientIdAndHpr(pasientId, hpr, DEFAULT_ORG_NUMBER)
                    ?.id,
            )
            assertEquals(
                pagaaendeId,
                konsultasjonRepository.findActiveByPasientId(pasientId, DEFAULT_ORG_NUMBER)?.id,
            )
        }
}
