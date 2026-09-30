package no.nav.helse.core.db

import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import no.nav.helse.utils.WithPostgresql
import org.flywaydb.core.Flyway
import org.junit.Test

class DiagnosekodeMigrationTest : WithPostgresql() {

    private fun migrateTo(target: String) {
        Flyway.configure()
            .dataSource(config.postgres.url, config.postgres.username, config.postgres.password)
            .locations("db/migration")
            .cleanDisabled(false)
            .target(target)
            .load()
            .migrate()
    }

    @Test
    fun `V5 migrates existing diagnose rows into konsultasjon_diagnosekode and drops diagnose`() {
        Flyway.configure()
            .dataSource(config.postgres.url, config.postgres.username, config.postgres.password)
            .locations("db/migration")
            .cleanDisabled(false)
            .load()
            .clean()
        migrateTo("4")

        val legekontorId = UUID.randomUUID()
        val pasientId = UUID.randomUUID()
        val konsultasjonId = UUID.randomUUID()

        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        INSERT INTO legekontor (id, navn, tlf, orgnummer)
                        VALUES ('$legekontorId', 'Legekontor', '12345678', '999999999')
                        """
                            .trimIndent()
                    )
                    statement.execute(
                        """
                        INSERT INTO pasient (id, legekontor_id, fornavn, etternavn, personident)
                        VALUES ('$pasientId', '$legekontorId', 'Fornavn', 'Etternavn', 'personident-1')
                        """
                            .trimIndent()
                    )
                    statement.execute(
                        """
                        INSERT INTO konsultasjon (id, pasient_id, startet_tidspunkt, status)
                        VALUES ('$konsultasjonId', '$pasientId', now(), 'PÅGÅENDE')
                        """
                            .trimIndent()
                    )
                    statement.execute(
                        """
                        INSERT INTO diagnose (patient_id, konsultasjon_id, diagnosekode, diagnosesystem, beskrivelse)
                        VALUES ('$pasientId', '$konsultasjonId', 'A01', 'ICPC2', 'Gammel beskrivelse')
                        """
                            .trimIndent()
                    )
                }
            }

        migrateTo("latest")

        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    val diagnosekoder =
                        statement.executeQuery(
                            "SELECT konsultasjon_id, diagnosesystem, diagnosekode " +
                                "FROM konsultasjon_diagnosekode"
                        )
                    assertEquals(true, diagnosekoder.next())
                    assertEquals(konsultasjonId.toString(), diagnosekoder.getString(1))
                    assertEquals("ICPC2", diagnosekoder.getString(2))
                    assertEquals("A01", diagnosekoder.getString(3))
                    assertEquals(false, diagnosekoder.next())

                    val diagnoseTabellFinnes =
                        statement.executeQuery("SELECT to_regclass('public.diagnose')")
                    diagnoseTabellFinnes.next()
                    assertNull(diagnoseTabellFinnes.getString(1))
                }
            }
    }

    @Test
    fun `V5 applies cleanly on an empty database migrated through all versions`() {
        runMigrations(true)

        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    val diagnosekoder =
                        statement.executeQuery("SELECT count(*) FROM konsultasjon_diagnosekode")
                    diagnosekoder.next()
                    assertEquals(0, diagnosekoder.getInt(1))

                    val diagnoseTabellFinnes =
                        statement.executeQuery("SELECT to_regclass('public.diagnose')")
                    diagnoseTabellFinnes.next()
                    assertNull(diagnoseTabellFinnes.getString(1))
                }
            }
    }
}
