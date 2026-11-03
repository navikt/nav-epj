package no.nav.helse.core.db

import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.konsultasjon.KonsultasjonRepository
import no.nav.helse.epj.maaling.MaalingRepository
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.patient.PatientRepository
import no.nav.helse.utils.WithPostgresql
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class HackathonClinicalSeedTest : WithPostgresql() {

    private val seedConfig = config.postgres.copy(seedTestdata = true)
    private val konsultasjonRepository = KonsultasjonRepository()
    private val maalingRepository = MaalingRepository()
    private val patientRepository = PatientRepository()

    private val orgWithGunnar = "312000032"
    private val orgWithBjarne = "312000016"

    private fun execute(vararg statements: String) =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    statements.forEach { statement.execute(it) }
                }
            }

    private fun counts(): List<String> =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    listOf(
                            "consultation",
                            "consultation_practitioner",
                            "consultation_diagnosis_code",
                            "clinical_note",
                            "measurement",
                        )
                        .map {
                            statement.executeQuery("SELECT count(*) FROM $it").use { rs ->
                                rs.next()
                                rs.getString(1)
                            }
                        }
                }
            }

    private suspend fun patientId(personident: String) =
        PatientId(checkNotNull(patientRepository.findByPersonident(personident)).id.value)

    @Before
    fun migrateWithSeed() {
        getFlyway(seedConfig).clean()
        getFlyway(seedConfig).migrate()
        connect()
    }

    @After
    fun restoreSchemaWithoutSeed() {
        getFlyway(config.postgres).clean()
        getFlyway(config.postgres).migrate()
    }

    @Test
    fun `seeded konsultasjoner, diagnoses and trend series are read at the list org`() = runTest {
        val gunnar = patientId("23895710156")

        val konsultasjoner = konsultasjonRepository.listByPatientId(gunnar, orgWithGunnar)
        assertEquals(10, konsultasjoner.size)
        assertEquals(1, konsultasjoner.count { it.status == KonsultasjonStatus.PLANLAGT })
        assertEquals(
            setOf("K86", "I10", "T90", "E11.9"),
            konsultasjoner.flatMap { it.diagnoser }.map { it.code }.toSet(),
        )
        assertTrue(konsultasjoner.all { it.hpr == listOf("900000103") })

        val hba1c =
            maalingRepository.listByPatientId(gunnar, orgWithGunnar).filter {
                it.loincKode == "4548-4"
            }
        assertEquals(
            listOf("8.7", "8.8", "8.4", "8", "8.1", "7.9", "7.6", "7.2", "6.9"),
            hba1c
                .sortedBy { it.effektivTidspunkt }
                .map { it.verdi.stripTrailingZeros().toPlainString() },
        )
        assertTrue(hba1c.all { it.enhetKode == "%" })
    }

    @Test
    fun `seeded clinical data is invisible at another org`() = runTest {
        val gunnar = patientId("23895710156")

        assertTrue(konsultasjonRepository.listByPatientId(gunnar, orgWithBjarne).isEmpty())
        assertTrue(maalingRepository.listByPatientId(gunnar, orgWithBjarne).isEmpty())
    }

    @Test
    fun `active konsultasjon is seeded for some patients`() = runTest {
        val bjarne = patientId("20856210112")

        assertNotNull(konsultasjonRepository.findActiveByPatientId(bjarne, orgWithBjarne))
        assertNull(konsultasjonRepository.findActiveByPatientId(bjarne, orgWithGunnar))
    }

    @Test
    fun `applying the seed again duplicates nothing and keeps edits`() = runTest {
        val before = counts()
        assertTrue(before.all { it.toInt() > 0 })
        execute(
            "UPDATE consultation SET reason_for_visit = 'Endret av bruker' " +
                "WHERE status = 'PÅGÅENDE'"
        )
        val sql =
            checkNotNull(javaClass.classLoader.getResource("db/testdata/R__hackathon_testdata.sql"))
                .readText()

        execute(sql)

        assertEquals(before, counts())
        val bjarne = patientId("20856210112")
        assertEquals(
            "Endret av bruker",
            konsultasjonRepository.findActiveByPatientId(bjarne, orgWithBjarne)?.problemstilling,
        )
    }

    private fun applySeed() =
        execute(
            checkNotNull(javaClass.classLoader.getResource("db/testdata/R__hackathon_testdata.sql"))
                .readText()
        )

    private fun count(query: String): Int =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { rs ->
                        rs.next()
                        rs.getInt(1)
                    }
                }
            }

    @Test
    fun `deleted seeded diagnoses and measurements stay deleted when the seed is applied again`() {
        val bb1 = "md5('hackathon-konsultasjon-BB1')::uuid"
        execute(
            "DELETE FROM consultation_diagnosis_code WHERE consultation_id = $bb1 " +
                "AND diagnosis_code = 'I10'",
            "DELETE FROM measurement WHERE consultation_id = $bb1 AND loinc_code = '29463-7'",
            "DELETE FROM clinical_note WHERE consultation_id = md5('hackathon-konsultasjon-BB3')::uuid",
        )
        val before = counts()

        applySeed()

        assertEquals(before, counts())
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM consultation_diagnosis_code WHERE consultation_id = $bb1 " +
                    "AND diagnosis_code = 'I10'"
            ),
        )
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM measurement WHERE consultation_id = $bb1 AND loinc_code = '29463-7'"
            ),
        )
    }

    @Test
    fun `started planned konsultasjon receives no diagnoses when the seed is applied again`() {
        val gf5 = "md5('hackathon-konsultasjon-GF5')::uuid"
        execute("UPDATE consultation SET status = 'PÅGÅENDE' WHERE id = $gf5")

        applySeed()

        assertEquals(
            0,
            count("SELECT count(*) FROM consultation_diagnosis_code WHERE consultation_id = $gf5"),
        )
    }

    private fun queryInts(query: String): List<Int> =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { rs ->
                        generateSequence { if (rs.next()) rs.getInt(1) else null }.toList()
                    }
                }
            }

    private val eventPatients =
        "SELECT flp.patient_id FROM fastlegeliste_patient flp " +
            "JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id WHERE fl.hpr LIKE '9000001%'"

    @Test
    fun `every event patient has at least three konsultasjoner over two years or more`() {
        val perPatient =
            queryInts(
                "SELECT count(k.id)::int FROM ($eventPatients) e " +
                    "LEFT JOIN consultation k ON k.patient_id = e.patient_id GROUP BY e.patient_id"
            )
        assertEquals(32, perPatient.size)
        assertTrue(perPatient.all { it >= 3 }, perPatient.toString())

        val spanDays =
            queryInts(
                "SELECT (max(k.started_at)::date - min(k.started_at)::date)::int " +
                    "FROM consultation k WHERE k.patient_id IN ($eventPatients) " +
                    "AND k.status <> 'PLANLAGT' GROUP BY k.patient_id"
            )
        assertTrue(spanDays.all { it >= 700 }, spanDays.toString())
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM consultation WHERE status <> 'PLANLAGT' " +
                    "AND started_at >= '2026-10-21'::timestamptz"
            ),
        )
    }

    @Test
    fun `every completed konsultasjon has a diagnosis and most have a journal note`() {
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM consultation k WHERE k.status = 'FULLFØRT' AND NOT EXISTS " +
                    "(SELECT 1 FROM consultation_diagnosis_code d WHERE d.consultation_id = k.id)"
            ),
        )
        val completed = count("SELECT count(*) FROM consultation WHERE status = 'FULLFØRT'")
        val withNote =
            count(
                "SELECT count(*) FROM consultation k WHERE k.status = 'FULLFØRT' AND EXISTS " +
                    "(SELECT 1 FROM clinical_note j WHERE j.consultation_id = k.id)"
            )
        assertTrue(withNote * 100 >= completed * 75, "$withNote of $completed")
    }

    @Test
    fun `at least twelve patients have a series of six readings of one measurement`() {
        val patients =
            count(
                "SELECT count(DISTINCT patient_id) FROM (SELECT patient_id, loinc_code FROM measurement " +
                    "GROUP BY patient_id, loinc_code HAVING count(*) >= 6) series"
            )
        assertTrue(patients >= 12, "$patients")
        assertTrue(
            count("SELECT count(*) FROM measurement WHERE patient_id IN ($eventPatients)") >= 1000
        )
        assertTrue(
            count("SELECT count(*) FROM consultation WHERE patient_id IN ($eventPatients)") in
                200..300
        )
    }

    @Test
    fun `rising and falling series exist for a flagging app`() = runTest {
        val sigrun = patientId("09894810030")
        val egfr =
            maalingRepository
                .listByPatientId(sigrun, "312000016")
                .filter { it.loincKode == "62238-1" }
                .sortedBy { it.effektivTidspunkt }
                .map { it.verdi.toInt() }
        assertEquals(8, egfr.size)
        assertTrue(egfr.first() > egfr.last())

        val eva = patientId("31857810085")
        val hba1c =
            maalingRepository
                .listByPatientId(eva, "312000040")
                .filter { it.loincKode == "4548-4" }
                .sortedBy { it.effektivTidspunkt }
                .map { it.verdi }
        assertTrue(hba1c.first() < hba1c.last())
        assertTrue(hba1c.last() > java.math.BigDecimal("8.5"))
    }
}
