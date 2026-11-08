package no.nav.helse.core.db

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.nav.helse.utils.WithPostgresql
import org.flywaydb.core.Flyway
import org.junit.After
import org.junit.Before
import org.junit.Test

class DiagnosisRankMigrationTest : WithPostgresql() {
    private val consultationId = "11111111-1111-1111-1111-111111111111"
    private val otherConsultationId = "22222222-2222-2222-2222-222222222222"

    private fun flyway(target: String) =
        Flyway.configure()
            .dataSource(config.postgres.url, config.postgres.username, config.postgres.password)
            .locations("db/migration")
            .cleanDisabled(false)
            .target(target)
            .load()

    private fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use(block)

    private fun execute(vararg statements: String) = withConnection { connection ->
        connection.createStatement().use { statement ->
            statements.forEach { statement.execute(it) }
        }
    }

    private fun ranks(id: String): List<Pair<Int, String>> = withConnection { connection ->
        connection.createStatement().use { statement ->
            statement
                .executeQuery(
                    "SELECT rank, diagnosis_code FROM consultation_diagnosis_code " +
                        "WHERE consultation_id = '$id' ORDER BY rank"
                )
                .use { rs -> buildList { while (rs.next()) add(rs.getInt(1) to rs.getString(2)) } }
        }
    }

    @Before
    fun migrateToLegacyLayout() {
        flyway("7").clean()
        flyway("7").migrate()
        execute(
            "INSERT INTO clinic (id, name, phone, org_number) " +
                "VALUES ('33333333-3333-3333-3333-333333333333', 'Klinikk', '1', '999')",
            "INSERT INTO patient (id, given_name, family_name, personident) " +
                "VALUES ('44444444-4444-4444-4444-444444444444', 'Ola', 'Nordmann', 'ident')",
            "INSERT INTO consultation (id, patient_id, clinic_id, started_at, status) " +
                "SELECT c, '44444444-4444-4444-4444-444444444444', " +
                "'33333333-3333-3333-3333-333333333333', now(), 'FINISHED' " +
                "FROM (VALUES ('$consultationId'::uuid), ('$otherConsultationId'::uuid)) v(c)",
            "INSERT INTO consultation_diagnosis_code VALUES " +
                "('$consultationId', 'ICD10', 'I10'), " +
                "('$consultationId', 'ICPC2', 'T90'), " +
                "('$consultationId', 'ICD10', 'E11.9'), " +
                "('$consultationId', 'ICPC2', 'K86'), " +
                "('$otherConsultationId', 'ICD10', 'J45.9')",
        )
    }

    @After
    fun restoreSchema() {
        runMigrations(true)
    }

    @Test
    fun `existing diagnoses are ranked with ICPC2 first then by system and code`() {
        flyway("8").migrate()

        assertEquals(
            listOf(1 to "K86", 2 to "T90", 3 to "E11.9", 4 to "I10"),
            ranks(consultationId),
        )
        assertEquals(listOf(1 to "J45.9"), ranks(otherConsultationId))
    }

    @Test
    fun `rank must be positive and unique within a consultation`() {
        flyway("8").migrate()

        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO consultation_diagnosis_code VALUES " +
                    "('$otherConsultationId', 'ICPC2', 'A01', 0)"
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO consultation_diagnosis_code VALUES " +
                    "('$otherConsultationId', 'ICPC2', 'A01', 1)"
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO consultation_diagnosis_code (consultation_id, diagnosis_system, " +
                    "diagnosis_code) VALUES ('$otherConsultationId', 'ICPC2', 'A01')"
            )
        }
    }
}
