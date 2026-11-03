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
import no.nav.helse.epj.patient.Patient
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class KonsultasjonRepositoryQueryTest : KonsultasjonRepositoryTestBase() {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `finds no konsultasjon`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val konsultasjon = konsultasjonRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER)
        assertEquals(0, konsultasjon.size)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `finds one konsultasjon`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val hpr = PractitionerHpr("123")
        patientRepository.insert(
            Patient(
                id = patientId,
                givenName = "givenName",
                familyName = "familyName",
                personident = "personident",
            )
        )
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                patientId = patientId,
                hpr = listOf(hpr),
                startetTidspunkt = LocalDateTime.now(),
                status = KonsultasjonStatus.PAAGAAENDE,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        val konsultasjon = konsultasjonRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER)
        assertEquals(1, konsultasjon.size)
    }

    @Test
    fun `listByPatientId returns only konsultasjoner for the correct patient`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientA = createPatient(hpr = hpr)
        val patientB = createPatient(hpr = hpr)
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                patientA,
                listOf(hpr),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                patientB,
                listOf(hpr),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )

        val konsultasjonerA = konsultasjonRepository.listByPatientId(patientA, DEFAULT_ORG_NUMBER)
        assertEquals(1, konsultasjonerA.size)
        assertEquals(patientA, konsultasjonerA.single().patientId)
    }

    @Test
    fun `listByPatientId sorts konsultasjoner descending by startetTidspunkt`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        val eldst = LocalDateTime.now().minusHours(2)
        val nyest = LocalDateTime.now()
        val eldstId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
                    listOf(hpr),
                    eldst,
                    KonsultasjonStatus.FULLFOERT,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )
        val nyesteId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
                    listOf(hpr),
                    nyest,
                    KonsultasjonStatus.PAAGAAENDE,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val konsultasjoner = konsultasjonRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER)
        assertEquals(2, konsultasjoner.size)
        assertEquals(nyesteId, konsultasjoner[0].id)
        assertEquals(eldstId, konsultasjoner[1].id)
    }

    @Test
    fun `insert stores multiple hpr for a konsultasjon`() = runTest {
        val hprA = PractitionerHpr("123")
        val hprB = PractitionerHpr("456")
        val patientId = createPatient(hpr = hprA)
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                patientId,
                listOf(hprA, hprB),
                LocalDateTime.now(),
                KonsultasjonStatus.PAAGAAENDE,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )

        val konsultasjon =
            konsultasjonRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER).single()
        assertEquals(setOf("123", "456"), konsultasjon.hpr.toSet())
    }

    @Test
    fun `insert with an empty hpr list gives a konsultasjon without healthcare personnel`() =
        runTest {
            val patientId = createPatient()
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
                    emptyList(),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PLANLAGT,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

            val konsultasjon =
                konsultasjonRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER).single()
            assertTrue(konsultasjon.hpr.isEmpty())
        }

    @Test
    fun `findActiveByPatientId returns null when no konsultasjoner exist`() = runTest {
        val patientId = createPatient()
        assertNull(konsultasjonRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `findActiveByPatientId ignores completed konsultasjoner`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        val oppdaterKonsultasjonRequest =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
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
            patientId,
            DEFAULT_ORG_NUMBER,
        )

        assertNull(konsultasjonRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `findActiveByPatientId returns the newest active konsultasjon`() = runTest {
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        konsultasjonRepository.insert(
            OpprettKonsultasjon(
                patientId,
                listOf(hpr),
                LocalDateTime.now().minusHours(2),
                KonsultasjonStatus.PAAGAAENDE,
                orgNumber = DEFAULT_ORG_NUMBER,
            )
        )
        val nyesteId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

        val aktiv = konsultasjonRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER)
        assertNotNull(aktiv)
        assertEquals(nyesteId, aktiv.id)
    }

    @Test
    fun `findActiveByPatientIdAndHpr returns null when the doctor has no active konsultasjon`() =
        runTest {
            val hpr = PractitionerHpr("123")
            val annenHpr = PractitionerHpr("456")
            val patientId = createPatient(hpr = hpr)
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )

            assertNull(
                konsultasjonRepository.findActiveByPatientIdAndHpr(
                    patientId,
                    annenHpr,
                    DEFAULT_ORG_NUMBER,
                )
            )
        }

    @Test
    fun `findActiveByPatientIdAndHpr lets two different doctors have simultaneous active konsultasjoner for the same patient`() =
        runTest {
            val legeA = PractitionerHpr("123")
            val legeB = PractitionerHpr("456")
            val patientId = createPatient(hpr = legeA)
            val konsultasjonA =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientId,
                        listOf(legeA),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            val konsultasjonB =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientId,
                        listOf(legeB),
                        LocalDateTime.now(),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )

            val aktivForA =
                konsultasjonRepository.findActiveByPatientIdAndHpr(
                    patientId,
                    legeA,
                    DEFAULT_ORG_NUMBER,
                )
            val aktivForB =
                konsultasjonRepository.findActiveByPatientIdAndHpr(
                    patientId,
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
        val hpr = PractitionerHpr("123")
        val patientId = createPatient(hpr = hpr)
        val konsultasjonId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
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
        val patientId = createPatient()
        val konsultasjonId =
            konsultasjonRepository.insert(
                OpprettKonsultasjon(
                    patientId,
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
        val patientId = createPatient()
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
                            VALUES ('$konsultasjonId', '${patientId.value}', NULL, now(), 'PÅGÅENDE')
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
            val hpr = PractitionerHpr("123")
            val patientId = createPatient(hpr = hpr)
            listOf(KonsultasjonStatus.AVLYST, KonsultasjonStatus.PLANLAGT).forEach {
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now().minusDays(1),
                        it,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )
            }

            assertNull(konsultasjonRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER))
            assertNull(
                konsultasjonRepository.findActiveByPatientIdAndHpr(
                    patientId,
                    hpr,
                    DEFAULT_ORG_NUMBER,
                )
            )

            val pagaaendeId =
                konsultasjonRepository.insert(
                    OpprettKonsultasjon(
                        patientId,
                        listOf(hpr),
                        LocalDateTime.now().minusDays(2),
                        KonsultasjonStatus.PAAGAAENDE,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )

            assertEquals(
                pagaaendeId,
                konsultasjonRepository
                    .findActiveByPatientIdAndHpr(patientId, hpr, DEFAULT_ORG_NUMBER)
                    ?.id,
            )
            assertEquals(
                pagaaendeId,
                konsultasjonRepository.findActiveByPatientId(patientId, DEFAULT_ORG_NUMBER)?.id,
            )
        }
}
