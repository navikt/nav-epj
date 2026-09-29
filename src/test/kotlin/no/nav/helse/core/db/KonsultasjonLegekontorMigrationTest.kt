package no.nav.helse.core.db

import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import no.nav.helse.utils.WithPostgresql
import org.flywaydb.core.Flyway
import org.junit.Test

class KonsultasjonLegekontorMigrationTest : WithPostgresql() {

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
    fun `V6 backfills legekontor_id from the konsultasjon's pasient and preserves the row`() {
        Flyway.configure()
            .dataSource(config.postgres.url, config.postgres.username, config.postgres.password)
            .locations("db/migration")
            .cleanDisabled(false)
            .load()
            .clean()
        migrateTo("5")

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
                    val rows =
                        statement.executeQuery(
                            "SELECT id, pasient_id, legekontor_id FROM konsultasjon"
                        )
                    assertEquals(true, rows.next())
                    assertEquals(konsultasjonId.toString(), rows.getString("id"))
                    assertEquals(pasientId.toString(), rows.getString("pasient_id"))
                    assertEquals(legekontorId.toString(), rows.getString("legekontor_id"))
                    assertEquals(false, rows.next())
                }
            }
    }

    @Test
    fun `V6 applies cleanly on an empty database migrated through all versions`() {
        runMigrations(true)

        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection ->
                connection.createStatement().use { statement ->
                    val rows = statement.executeQuery("SELECT count(*) FROM konsultasjon")
                    rows.next()
                    assertEquals(0, rows.getInt(1))

                    val legekontorIdColumn =
                        statement.executeQuery(
                            "SELECT column_name FROM information_schema.columns " +
                                "WHERE table_name = 'konsultasjon' AND column_name = 'legekontor_id'"
                        )
                    assertEquals(true, legekontorIdColumn.next())
                }
            }
    }
}
