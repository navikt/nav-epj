package no.nav.helse.core.db

import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.ehr.consultation.ConsultationRepository
import no.nav.helse.ehr.measurement.MeasurementRepository
import no.nav.helse.ehr.measurement.MeasurementStatus
import no.nav.helse.ehr.patient.PatientId
import no.nav.helse.ehr.patient.PatientRepository
import no.nav.helse.utils.WithPostgresql
import no.nav.tsm.diagnoser.DiagnoseType
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class HackathonClinicalSeedTest : WithPostgresql() {

    private val seedConfig = config.postgres.copy(seedTestdata = true)
    private val consultationRepository = ConsultationRepository()
    private val measurementRepository = MeasurementRepository()
    private val patientRepository = PatientRepository()

    private val orgWithGunnar = "312000032"
    private val orgWithBjarne = "312000016"
    private val eventOrgs = listOf("312000016", "312000024", "312000032", "312000040")

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
    fun `seeded consultations, diagnoses and trend series are read at the list org`() = runTest {
        val gunnar = patientId("23895710156")

        val consultations = consultationRepository.listByPatientId(gunnar, orgWithGunnar)
        assertEquals(10, consultations.size)
        assertEquals(1, consultations.count { it.status == ConsultationStatus.PLANNED })
        assertEquals(
            setOf("K86", "T90"),
            consultations.flatMap { it.diagnoses }.map { it.code }.toSet(),
        )
        assertTrue(consultations.flatMap { it.diagnoses }.all { it.system == DiagnoseType.ICPC2 })
        assertTrue(consultations.all { it.hpr == listOf("900000103") })

        val hba1c =
            measurementRepository.listByPatientId(gunnar, orgWithGunnar).filter {
                it.loincCode == "4548-4"
            }
        assertEquals(
            listOf("8.7", "8.8", "8.4", "8", "8.1", "7.9", "7.6", "7.2", "6.9"),
            hba1c.sortedBy { it.effectiveAt }.map { it.value.stripTrailingZeros().toPlainString() },
        )
        assertTrue(hba1c.all { it.unitCode == "%" })
    }

    @Test
    fun `seeded clinical data is invisible at another org`() = runTest {
        val gunnar = patientId("23895710156")

        assertTrue(consultationRepository.listByPatientId(gunnar, orgWithBjarne).isEmpty())
        assertTrue(measurementRepository.listByPatientId(gunnar, orgWithBjarne).isEmpty())
    }

    @Test
    fun `active consultation is seeded for some patients`() = runTest {
        val bjarne = patientId("20856210112")

        assertNotNull(consultationRepository.findActiveByPatientId(bjarne, orgWithBjarne))
        assertNull(consultationRepository.findActiveByPatientId(bjarne, orgWithGunnar))
    }

    @Test
    fun `applying the seed again duplicates nothing and keeps edits`() = runTest {
        val before = counts()
        assertTrue(before.all { it.toInt() > 0 })
        execute(
            "UPDATE consultation SET reason_for_visit = 'Endret av bruker' " +
                "WHERE status = 'IN_PROGRESS'"
        )
        val sql =
            checkNotNull(javaClass.classLoader.getResource("db/testdata/R__hackathon_testdata.sql"))
                .readText()

        execute(sql)

        assertEquals(before, counts())
        val bjarne = patientId("20856210112")
        assertEquals(
            "Endret av bruker",
            consultationRepository.findActiveByPatientId(bjarne, orgWithBjarne)?.reasonForVisit,
        )
    }

    private fun applySeed() {
        listOf("db/testdata/R__testdata.sql", "db/testdata/R__hackathon_testdata.sql").forEach {
            val sql = checkNotNull(javaClass.classLoader.getResource(it)).readText()
            DriverManager.getConnection(
                    config.postgres.url,
                    config.postgres.username,
                    config.postgres.password,
                )
                .use { connection ->
                    connection.autoCommit = false
                    try {
                        connection.createStatement().use { statement -> statement.execute(sql) }
                        connection.commit()
                    } catch (e: Exception) {
                        connection.rollback()
                        throw e
                    }
                }
        }
    }

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
                "AND diagnosis_code = 'K86'",
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
                    "AND diagnosis_code = 'K86'"
            ),
        )
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM measurement WHERE consultation_id = $bb1 AND loinc_code = '29463-7'"
            ),
        )
    }

    private fun ranked(consultationId: String): List<String> =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT rank || ' ' || diagnosis_system || ' ' || diagnosis_code " +
                                "FROM consultation_diagnosis_code " +
                                "WHERE consultation_id = $consultationId ORDER BY rank"
                        )
                        .use { rs ->
                            generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
                        }
                }
            }

    @Test
    fun `seeded multi diagnosis consultations rank the main diagnosis before secondary ones`() {
        assertEquals(
            listOf("1 ICPC2 T90", "2 ICPC2 K86", "3 ICPC2 U99"),
            ranked("md5('hackathon-konsultasjon-PT1')::uuid"),
        )
        assertEquals(
            listOf("1 ICPC2 K86", "2 ICPC2 U99"),
            ranked("md5('hackathon-konsultasjon-SM1')::uuid"),
        )
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM consultation_diagnosis_code WHERE diagnosis_system = 'ICD10'"
            ),
        )
    }

    @Test
    fun `legacy twin layout on seeded consultations is rewritten without touching other conditions`() {
        val pt1 = "md5('hackathon-konsultasjon-PT1')::uuid"
        val bb1 = "md5('hackathon-konsultasjon-BB1')::uuid"
        val userConsultation = "'66666666-6666-6666-6666-666666666666'"
        execute(
            "DELETE FROM consultation_diagnosis_code WHERE consultation_id IN ($pt1, $bb1)",
            "INSERT INTO consultation_diagnosis_code VALUES " +
                "($pt1, 'ICPC2', 'K86', 1), ($pt1, 'ICPC2', 'T90', 2), ($pt1, 'ICPC2', 'U99', 3), " +
                "($pt1, 'ICD10', 'E11.9', 4), ($pt1, 'ICD10', 'I10', 5), ($pt1, 'ICD10', 'N18.3', 6), " +
                "($pt1, 'ICD10', 'J06.9', 7), " +
                "($bb1, 'ICPC2', 'K86', 1), ($bb1, 'ICD10', 'I10', 2)",
            "INSERT INTO consultation (id, patient_id, clinic_id, started_at, status) " +
                "SELECT $userConsultation, patient_id, clinic_id, now(), 'FINISHED' " +
                "FROM consultation WHERE id = $pt1",
            "INSERT INTO consultation_diagnosis_code VALUES " +
                "($userConsultation, 'ICD10', 'I10', 1), ($userConsultation, 'ICPC2', 'T90', 2)",
        )

        applySeed()

        assertEquals(
            listOf("1 ICPC2 T90", "2 ICPC2 K86", "3 ICPC2 U99", "4 ICD10 J06.9"),
            ranked(pt1),
        )
        assertEquals(listOf("1 ICPC2 K86"), ranked(bb1))
        assertEquals(listOf("1 ICD10 I10", "2 ICPC2 T90"), ranked(userConsultation))

        execute(
            "DELETE FROM consultation_diagnosis_code WHERE consultation_id = $pt1 " +
                "AND diagnosis_code = 'J06.9'"
        )
        applySeed()

        assertEquals(listOf("1 ICPC2 T90", "2 ICPC2 K86", "3 ICPC2 U99"), ranked(pt1))
    }

    @Test
    fun `legacy invalid A09 diagnosis is removed so the consultation can be read`() = runTest {
        val saa = "md5('hackathon-konsultasjon-SAa')::uuid"
        execute(
            "DELETE FROM consultation_diagnosis_code WHERE consultation_id = $saa",
            "INSERT INTO consultation_diagnosis_code VALUES " +
                "($saa, 'ICPC2', 'D73', 1), ($saa, 'ICD10', 'A09', 2)",
        )

        applySeed()

        assertEquals(listOf("1 ICPC2 D73"), ranked(saa))
        val diagnoses =
            consultationRepository
                .listByPatientId(patientId("49819110351"), "312000040")
                .flatMap { it.diagnoses }
                .map { it.code }
        assertTrue("D73" in diagnoses)
        assertTrue(diagnoses.none { it.startsWith("A09") })
    }

    @Test
    fun `started planned consultation receives no diagnoses when the seed is applied again`() {
        val gf5 = "md5('hackathon-konsultasjon-GF5')::uuid"
        execute("UPDATE consultation SET status = 'IN_PROGRESS' WHERE id = $gf5")

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
    fun `every event patient has at least three consultations over two years or more`() {
        val perPatient =
            queryInts(
                "SELECT count(c.id)::int FROM ($eventPatients) e " +
                    "LEFT JOIN consultation c ON c.patient_id = e.patient_id GROUP BY e.patient_id"
            )
        assertEquals(32, perPatient.size)
        assertTrue(perPatient.all { it >= 3 }, perPatient.toString())

        val spanDays =
            queryInts(
                "SELECT (max(c.started_at)::date - min(c.started_at)::date)::int " +
                    "FROM consultation c WHERE c.patient_id IN ($eventPatients) " +
                    "AND c.status <> 'PLANNED' GROUP BY c.patient_id"
            )
        assertTrue(spanDays.all { it >= 700 }, spanDays.toString())
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM consultation WHERE status <> 'PLANNED' " +
                    "AND started_at >= '2026-10-21'::timestamptz"
            ),
        )
    }

    @Test
    fun `every completed consultation has a diagnosis and most have a journal note`() {
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM consultation c WHERE c.status = 'FINISHED' AND NOT EXISTS " +
                    "(SELECT 1 FROM consultation_diagnosis_code d WHERE d.consultation_id = c.id)"
            ),
        )
        val completed = count("SELECT count(*) FROM consultation WHERE status = 'FINISHED'")
        val withNote =
            count(
                "SELECT count(*) FROM consultation c WHERE c.status = 'FINISHED' AND EXISTS " +
                    "(SELECT 1 FROM clinical_note cn WHERE cn.consultation_id = c.id)"
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
            measurementRepository
                .listByPatientId(sigrun, "312000016")
                .filter { it.loincCode == "62238-1" }
                .sortedBy { it.effectiveAt }
                .map { it.value.toInt() }
        assertEquals(8, egfr.size)
        assertTrue(egfr.first() > egfr.last())

        val eva = patientId("31857810085")
        val hba1c =
            measurementRepository
                .listByPatientId(eva, "312000040")
                .filter { it.loincCode == "4548-4" }
                .sortedBy { it.effectiveAt }
                .map { it.value }
        assertTrue(hba1c.first() < hba1c.last())
        assertTrue(hba1c.last() > java.math.BigDecimal("8.5"))
    }

    private data class CardPatient(
        val org: String,
        val personident: String,
        val codes: List<String>,
    )

    private val cardUnits =
        mapOf(
            "62238-1" to "mL/min/{1.73_m2}",
            "14682-9" to "umol/L",
            "69737-5" to "{score}",
            "44261-6" to "{score}",
            "8480-6" to "mm[Hg]",
            "8462-4" to "mm[Hg]",
            "29463-7" to "kg",
            "4548-4" to "%",
        )

    private val cardPatients =
        listOf(
            CardPatient("312000016", "09894810030", listOf("62238-1", "14682-9")),
            CardPatient("312000024", "12924510194", listOf("62238-1", "14682-9")),
            CardPatient(
                "312000032",
                "23895710156",
                listOf("62238-1", "8480-6", "4548-4", "29463-7"),
            ),
            CardPatient(
                "312000040",
                "16834910156",
                listOf("62238-1", "14682-9", "8480-6", "8462-4"),
            ),
            CardPatient("312000016", "70837710381", listOf("69737-5")),
            CardPatient("312000024", "65866910027", listOf("44261-6")),
            CardPatient("312000032", "66820350082", listOf("44261-6")),
            CardPatient("312000016", "20856210112", listOf("8480-6", "8462-4", "29463-7")),
            CardPatient("312000024", "04845310039", listOf("4548-4", "62238-1")),
        )

    @Test
    fun `problem card patients have three final readings of each card code at their org`() =
        runTest {
            cardPatients.forEach { card ->
                val readings =
                    measurementRepository.listByPatientId(patientId(card.personident), card.org)
                card.codes.forEach { code ->
                    val series = readings.filter { it.loincCode == code }
                    val label = "${card.personident} at ${card.org}, $code"
                    assertTrue(series.size >= 3, "$label has ${series.size} readings")
                    assertTrue(series.all { it.status == MeasurementStatus.FINAL }, label)
                    assertTrue(series.all { it.unitCode == cardUnits.getValue(code) }, label)
                }
            }
        }

    @Test
    fun `problem card patients are visible at none of the other event orgs`() = runTest {
        cardPatients.forEach { card ->
            eventOrgs
                .filter { it != card.org }
                .forEach { otherOrg ->
                    assertTrue(
                        measurementRepository
                            .listByPatientId(patientId(card.personident), otherOrg)
                            .isEmpty(),
                        "${card.personident} leaks to $otherOrg",
                    )
                }
        }
    }

    @Test
    fun `hilde nes has phq9 readings only before 2026`() = runTest {
        val phq9 =
            measurementRepository.listByPatientId(patientId("65866910027"), "312000024").filter {
                it.loincCode == "44261-6"
            }
        assertTrue(phq9.isNotEmpty())
        assertTrue(phq9.all { it.effectiveAt.year < 2026 }, phq9.map { it.effectiveAt }.toString())
    }

    @Test
    fun `consultation prep patients Else Strand and Helge Bratt have an ongoing consultation`() =
        runTest {
            assertNotNull(
                consultationRepository.findActiveByPatientId(patientId("04845310039"), "312000024")
            )
            assertNotNull(
                consultationRepository.findActiveByPatientId(patientId("16834910156"), "312000040")
            )
        }
}
