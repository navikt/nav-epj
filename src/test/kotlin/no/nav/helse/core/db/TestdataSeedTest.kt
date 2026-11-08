package no.nav.helse.core.db

import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.nav.helse.ehr.patient.PersonidentType
import no.nav.helse.ehr.patient.PersonidentValidator
import no.nav.tsm.diagnoser.Diagnose
import org.junit.Test

class TestdataSeedTest : SeededPostgresqlTest() {

    private fun count(table: String) = queryStrings("SELECT count(*) FROM $table").single().single()

    @Test
    fun `every seeded diagnosis code exists in the diagnosis library`() {
        val codes =
            queryStrings(
                "SELECT DISTINCT diagnosis_system, diagnosis_code FROM consultation_diagnosis_code"
            )

        assertTrue(codes.isNotEmpty())
        codes.forEach { (system, code) ->
            assertTrue(Diagnose.from(system!!, code!!) != null, "$system $code")
        }
    }

    @Test
    fun `cancelled and finished consultations have an end time while open ones do not`() {
        val endedAtMissing =
            "SELECT count(*) FROM consultation WHERE status IN ('CANCELLED', 'FINISHED') " +
                "AND ended_at IS NULL"
        assertEquals("0", queryStrings(endedAtMissing).single().single())
        assertTrue(
            queryStrings("SELECT count(*) FROM consultation WHERE status = 'CANCELLED'")
                .single()
                .single()!!
                .toInt() > 0
        )
        assertTrue(
            queryStrings(
                    "SELECT count(*) FROM consultation WHERE status IN ('PLANNED', 'IN_PROGRESS') " +
                        "AND ended_at IS NULL"
                )
                .single()
                .single()!!
                .toInt() > 0
        )
    }

    @Test
    fun `existing cancelled consultations without end time get one when the seed is applied again`() {
        withConnection { connection ->
            connection.createStatement().use {
                it.execute("UPDATE consultation SET ended_at = NULL WHERE status = 'CANCELLED'")
            }
        }

        applySeedAgain()

        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM consultation WHERE status = 'CANCELLED' " +
                        "AND ended_at IS NULL"
                )
                .single()
                .single(),
        )
    }

    @Test
    fun `every seeded patient with demographics has a valid synthetic personident`() {
        val patients =
            queryStrings(
                "SELECT personident, personident_type, birth_date FROM patient WHERE birth_date IS NOT NULL"
            )

        assertTrue(patients.size >= 6)
        patients.forEach { (personident, type, birthDate) ->
            PersonidentValidator.validate(
                personident!!,
                PersonidentType.valueOf(type!!),
                LocalDate.parse(birthDate!!),
            )
            assertTrue(PersonidentValidator.isSynthetic(personident), personident)
        }
    }

    @Test
    fun `patient without demographics keeps a synthetic personident and no demographics`() {
        val row =
            queryStrings(
                    "SELECT personident, personident_type, birth_date, gender " +
                        "FROM patient WHERE given_name = 'MATEMATISK'"
                )
                .single()

        assertTrue(PersonidentValidator.isSynthetic(row[0]!!))
        assertEquals(listOf(null, null, null), row.drop(1))
    }

    @Test
    fun `patient with complete demographics is seeded once with a synthetic personident`() {
        val rows =
            queryStrings(
                "SELECT personident FROM patient WHERE given_name = 'FRISK' AND family_name = 'REV'"
            )

        assertEquals(1, rows.size)
        assertTrue(PersonidentValidator.isSynthetic(rows.single().single()!!))
    }

    @Test
    fun `seeded organisations have valid mod11 org number`() {
        val weights = intArrayOf(3, 2, 7, 6, 5, 4, 3, 2)
        val seeded =
            queryStrings(
                "SELECT org_number FROM clinic WHERE org_number ~ '^[0-9]{9}$' AND org_number LIKE '3%'"
            )

        assertEquals(6, seeded.size)
        seeded.forEach { (orgNumber) ->
            val digits = orgNumber!!.map { it - '0' }
            val remainder = digits.take(8).indices.sumOf { digits[it] * weights[it] } % 11
            assertEquals((11 - remainder) % 11, digits[8], orgNumber)
        }
    }

    @Test
    fun `fastlegelister resolve to doctor, office and patients`() {
        val lists =
            queryStrings(
                """
                SELECT fl.hpr, cl.org_number, count(flp.patient_id)
                FROM fastlegeliste fl
                         JOIN clinic cl ON cl.id = fl.clinic_id
                         LEFT JOIN fastlegeliste_patient flp ON flp.fastlegeliste_id = fl.id
                GROUP BY fl.hpr, cl.org_number
                ORDER BY fl.hpr
                """
                    .trimIndent()
            )

        assertEquals(
            listOf(
                listOf("565501872", "311111116", "5"),
                listOf("900000001", "311111124", "3"),
                listOf("900000101", "312000016", "8"),
                listOf("900000102", "312000024", "8"),
                listOf("900000103", "312000032", "8"),
                listOf("900000104", "312000040", "8"),
            ),
            lists,
        )
    }

    @Test
    fun `household is split across two organisations`() {
        val offices =
            queryStrings(
                """
                SELECT DISTINCT cl.org_number
                FROM patient p
                         JOIN fastlegeliste_patient flp ON flp.patient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                         JOIN clinic cl ON cl.id = fl.clinic_id
                WHERE p.family_name = 'FJORD'
                ORDER BY cl.org_number
                """
                    .trimIndent()
            )

        assertEquals(listOf(listOf("311111116"), listOf("311111124")), offices)
    }

    @Test
    fun `applying the seed again is idempotent and leaves clinical data untouched`() {
        val consultationId = UUID.randomUUID()
        withConnection { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    INSERT INTO consultation (id, patient_id, clinic_id, started_at, status)
                    SELECT '$consultationId', p.id, fl.clinic_id, now(), 'PLANNED'
                    FROM patient p
                             JOIN fastlegeliste_patient flp ON flp.patient_id = p.id
                             JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                    WHERE p.given_name = 'TRYGG'
                    """
                        .trimIndent()
                )
            }
        }
        val before =
            listOf("clinic", "patient", "fastlegeliste", "fastlegeliste_patient", "consultation")
                .map { count(it) }

        applySeedAgain()

        val after =
            listOf("clinic", "patient", "fastlegeliste", "fastlegeliste_patient", "consultation")
                .map { count(it) }
        assertEquals(before, after)
        assertEquals(
            listOf(listOf(consultationId.toString())),
            queryStrings("SELECT id FROM consultation WHERE id = '$consultationId'"),
        )
    }

    private fun clinicalCounts(orgNumber: String): List<String> =
        queryStrings(
                """
                SELECT (SELECT count(*) FROM consultation c WHERE c.clinic_id = cl.id),
                       (SELECT count(*) FROM consultation_diagnosis_code d
                                 JOIN consultation c ON c.id = d.consultation_id WHERE c.clinic_id = cl.id),
                       (SELECT count(*) FROM measurement m
                                 JOIN consultation c ON c.id = m.consultation_id WHERE c.clinic_id = cl.id),
                       (SELECT count(*) FROM clinical_note cn
                                 JOIN consultation c ON c.id = cn.consultation_id WHERE c.clinic_id = cl.id)
                FROM clinic cl
                WHERE cl.org_number = '$orgNumber'
                """
                    .trimIndent()
            )
            .single()
            .map { it!! }

    @Test
    fun `everyday test patients get clinical histories at their list office`() {
        assertEquals(listOf("40", "38", "193", "38"), clinicalCounts("311111116"))
        assertEquals(listOf("33", "41", "213", "30"), clinicalCounts("311111124"))
    }

    @Test
    fun `every patient on an everyday list has an active consultation at the list office`() {
        val withoutActive =
            queryStrings(
                """
                SELECT p.given_name
                FROM patient p
                         JOIN fastlegeliste_patient flp ON flp.patient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                WHERE fl.hpr IN ('565501872', '900000001')
                  AND NOT EXISTS (SELECT 1
                                  FROM consultation c
                                  WHERE c.patient_id = p.id
                                    AND c.clinic_id = fl.clinic_id
                                    AND c.status = 'IN_PROGRESS')
                """
                    .trimIndent()
            )

        assertEquals(emptyList(), withoutActive)
    }

    @Test
    fun `everyday patients have diagnoses, measurements and a dated history`() {
        val everyday =
            """
            SELECT flp.patient_id
            FROM fastlegeliste_patient flp
                     JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
            WHERE fl.hpr IN ('565501872', '900000001')
            """
                .trimIndent()

        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM consultation c WHERE c.status = 'FINISHED' AND NOT EXISTS " +
                        "(SELECT 1 FROM consultation_diagnosis_code d WHERE d.consultation_id = c.id)"
                )
                .single()
                .single(),
        )
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM ($everyday) e WHERE NOT EXISTS " +
                        "(SELECT 1 FROM measurement m WHERE m.patient_id = e.patient_id)"
                )
                .single()
                .single(),
        )
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM consultation WHERE status = 'PLANNED' " +
                        "AND started_at < '2026-10-21'::timestamptz"
                )
                .single()
                .single(),
        )
        val withSeries =
            queryStrings(
                    "SELECT count(DISTINCT patient_id) FROM (SELECT patient_id FROM measurement " +
                        "WHERE patient_id IN ($everyday) GROUP BY patient_id, loinc_code " +
                        "HAVING count(*) >= 6) s"
                )
                .single()
                .single()!!
                .toInt()
        assertTrue(withSeries >= 3, "$withSeries")
    }

    @Test
    fun `everyday series include abnormal rising values`() {
        val hba1c =
            queryStrings(
                    """
                    SELECT m.value
                    FROM measurement m
                             JOIN patient p ON p.id = m.patient_id
                    WHERE p.given_name = 'RASK' AND m.loinc_code = '4548-4'
                    ORDER BY m.effective_at
                    """
                        .trimIndent()
                )
                .map { it.single()!!.toBigDecimal() }

        assertTrue(hba1c.size >= 6)
        assertTrue(hba1c.last() > hba1c.first())
        assertTrue(hba1c.last() > "7.5".toBigDecimal())
    }

    @Test
    fun `applying both seeds again keeps clinical counts and does not restore deleted rows`() {
        val rl1ConsultationId = "md5('testdata-konsultasjon-RL1')::uuid"
        withConnection { connection ->
            connection.createStatement().use {
                it.execute(
                    "DELETE FROM consultation_diagnosis_code WHERE consultation_id = $rl1ConsultationId " +
                        "AND diagnosis_code = 'K86'"
                )
                it.execute(
                    "DELETE FROM measurement WHERE consultation_id = $rl1ConsultationId AND loinc_code = '29463-7'"
                )
            }
        }
        val tables =
            listOf(
                "consultation",
                "consultation_practitioner",
                "consultation_diagnosis_code",
                "clinical_note",
                "measurement",
            )
        val before = tables.map { count(it) }

        applySeedAgain()

        assertEquals(before, tables.map { count(it) })
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM consultation_diagnosis_code WHERE consultation_id = $rl1ConsultationId " +
                        "AND diagnosis_code = 'K86'"
                )
                .single()
                .single(),
        )
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM measurement WHERE consultation_id = $rl1ConsultationId AND loinc_code = '29463-7'"
                )
                .single()
                .single(),
        )
    }

    @Test
    fun `hackathon patients have valid synthetic personidents on one list each`() {
        val patients =
            queryStrings(
                """
                SELECT p.personident, p.personident_type, p.birth_date, p.gender
                FROM patient p
                         JOIN fastlegeliste_patient flp ON flp.patient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                WHERE fl.hpr LIKE '9000001%'
                """
                    .trimIndent()
            )

        assertEquals(32, patients.size)
        assertEquals(32, patients.map { it[0] }.toSet().size)
        patients.forEach { row ->
            val personident = row[0]!!
            PersonidentValidator.validate(
                personident,
                PersonidentType.valueOf(row[1]!!),
                LocalDate.parse(row[2]!!),
            )
            assertTrue(PersonidentValidator.isSynthetic(personident), personident)
            val oddIndividualNumber = personident[8].digitToInt() % 2 == 1
            assertEquals(if (oddIndividualNumber) "MALE" else "FEMALE", row[3], personident)
        }
    }

    @Test
    fun `every hackathon organisation has both FNR and DNR patients`() {
        val types =
            queryStrings(
                """
                SELECT cl.org_number, string_agg(DISTINCT p.personident_type, ',' ORDER BY p.personident_type)
                FROM patient p
                         JOIN fastlegeliste_patient flp ON flp.patient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                         JOIN clinic cl ON cl.id = fl.clinic_id
                WHERE fl.hpr LIKE '9000001%'
                GROUP BY cl.org_number
                ORDER BY cl.org_number
                """
                    .trimIndent()
            )

        assertEquals(4, types.size)
        types.forEach { assertEquals("DNR,FNR", it[1], it[0]) }
    }

    @Test
    fun `hackathon households are split across organisations`() {
        val households =
            queryStrings(
                """
                SELECT p.family_name, count(DISTINCT fl.clinic_id)
                FROM patient p
                         JOIN fastlegeliste_patient flp ON flp.patient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                WHERE fl.hpr LIKE '9000001%' AND p.family_name IN ('BERGE', 'STRAND')
                GROUP BY p.family_name
                ORDER BY p.family_name
                """
                    .trimIndent()
            )

        assertEquals(listOf(listOf("BERGE", "2"), listOf("STRAND", "2")), households)
    }

    @Test
    fun `seed overwrites demographics and restores removed list membership`() {
        withConnection { connection ->
            connection.createStatement().use {
                it.execute("UPDATE patient SET given_name = 'ENDRET' WHERE given_name = 'TRYGG'")
                it.execute("DELETE FROM fastlegeliste_patient")
            }
        }

        applySeedAgain()

        assertEquals(
            "1",
            queryStrings("SELECT count(*) FROM patient WHERE given_name = 'TRYGG'")
                .single()
                .single(),
        )
        assertEquals("40", count("fastlegeliste_patient"))
    }

    @Test
    fun `migrating again with the seed disabled succeeds without clean`() {
        getFlyway(config.postgres).migrate()

        assertEquals("40", count("fastlegeliste_patient"))
    }

    @Test
    fun `seed keeps list membership of an app-created patient on a seeded list`() {
        val patientId = UUID.randomUUID()
        withConnection { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    INSERT INTO patient (id, given_name, family_name, personident)
                    VALUES ('$patientId', 'APP', 'OPPRETTET', 'app-created-1')
                    """
                        .trimIndent()
                )
                it.execute(
                    """
                    INSERT INTO fastlegeliste_patient (fastlegeliste_id, patient_id)
                    SELECT id, '$patientId' FROM fastlegeliste WHERE hpr = '565501872'
                    """
                        .trimIndent()
                )
            }
        }

        applySeedAgain()

        assertEquals(
            listOf(listOf("565501872")),
            queryStrings(
                """
                SELECT fl.hpr
                FROM fastlegeliste_patient flp
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                WHERE flp.patient_id = '$patientId'
                """
                    .trimIndent()
            ),
        )
        assertEquals("6", count("fastlegeliste"))
    }

    @Test
    fun `seed moves a seeded patient back to the list defined in the seed`() {
        withConnection { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    UPDATE fastlegeliste_patient
                    SET fastlegeliste_id = (SELECT id FROM fastlegeliste WHERE hpr = '900000001')
                    WHERE patient_id = (SELECT id FROM patient WHERE given_name = 'TRYGG')
                    """
                        .trimIndent()
                )
            }
        }

        applySeedAgain()

        assertEquals(
            listOf(listOf("565501872")),
            queryStrings(
                """
                SELECT fl.hpr
                FROM fastlegeliste_patient flp
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                         JOIN patient p ON p.id = flp.patient_id
                WHERE p.given_name = 'TRYGG'
                """
                    .trimIndent()
            ),
        )
    }
}
