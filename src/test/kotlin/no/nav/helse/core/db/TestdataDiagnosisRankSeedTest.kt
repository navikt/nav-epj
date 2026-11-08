package no.nav.helse.core.db

import kotlin.test.assertEquals
import org.junit.Test

class TestdataDiagnosisRankSeedTest : SeededPostgresqlTest() {

    private fun ranked(consultationId: String): List<Pair<String, String>> =
        queryStrings(
                "SELECT rank, diagnosis_system || ' ' || diagnosis_code " +
                    "FROM consultation_diagnosis_code WHERE consultation_id = $consultationId " +
                    "ORDER BY rank"
            )
            .map { it[0]!! to it[1]!! }

    private val rl1 = "md5('testdata-konsultasjon-RL1')::uuid"
    private val apa = "md5('testdata-konsultasjon-APa')::uuid"
    private val userConsultation = "'55555555-5555-5555-5555-555555555555'"

    private fun restoreLegacyLayout() = withConnection { connection ->
        connection.createStatement().use {
            it.execute(
                "DELETE FROM consultation_diagnosis_code WHERE consultation_id IN ($rl1, $apa)"
            )
            it.execute(
                "INSERT INTO consultation_diagnosis_code VALUES " +
                    "($rl1, 'ICPC2', 'K86', 1), ($rl1, 'ICPC2', 'T90', 2), " +
                    "($rl1, 'ICD10', 'E11.9', 3), ($rl1, 'ICD10', 'I10', 4), " +
                    "($rl1, 'ICD10', 'J06.9', 5), " +
                    "($apa, 'ICPC2', 'D73', 1), ($apa, 'ICD10', 'A09', 2)"
            )
            it.execute(
                "INSERT INTO consultation (id, patient_id, clinic_id, started_at, status) " +
                    "SELECT $userConsultation, patient_id, clinic_id, now(), 'FINISHED' " +
                    "FROM consultation WHERE id = $rl1"
            )
            it.execute(
                "INSERT INTO consultation_diagnosis_code VALUES " +
                    "($userConsultation, 'ICD10', 'E11.9', 1), " +
                    "($userConsultation, 'ICPC2', 'T90', 2)"
            )
        }
    }

    @Test
    fun `seeded consultations have an ICPC2 main diagnosis first and no ICD10 twins`() {
        assertEquals(listOf("1" to "ICPC2 T90", "2" to "ICPC2 K86"), ranked(rl1))
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM consultation_diagnosis_code WHERE diagnosis_system = 'ICD10'"
                )
                .single()
                .single(),
        )
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM consultation c WHERE c.status = 'FINISHED' AND " +
                        "NOT EXISTS (SELECT 1 FROM consultation_diagnosis_code d " +
                        "WHERE d.consultation_id = c.id AND d.rank = 1)"
                )
                .single()
                .single(),
        )
    }

    @Test
    fun `legacy twin layout on seeded consultations is rewritten without touching other conditions`() {
        restoreLegacyLayout()

        applySeedAgain()

        assertEquals(
            listOf("1" to "ICPC2 T90", "2" to "ICPC2 K86", "3" to "ICD10 J06.9"),
            ranked(rl1),
        )
        assertEquals(listOf("1" to "ICPC2 D73"), ranked(apa))
        assertEquals(listOf("1" to "ICD10 E11.9", "2" to "ICPC2 T90"), ranked(userConsultation))
    }

    @Test
    fun `applying the seed again after the cleanup keeps ranks changed by the user`() {
        restoreLegacyLayout()
        applySeedAgain()
        withConnection { connection ->
            connection.createStatement().use {
                it.execute("DELETE FROM consultation_diagnosis_code WHERE consultation_id = $rl1")
                it.execute(
                    "INSERT INTO consultation_diagnosis_code VALUES " +
                        "($rl1, 'ICPC2', 'K86', 1), ($rl1, 'ICPC2', 'T90', 2)"
                )
            }
        }

        applySeedAgain()

        assertEquals(listOf("1" to "ICPC2 K86", "2" to "ICPC2 T90"), ranked(rl1))
    }
}
