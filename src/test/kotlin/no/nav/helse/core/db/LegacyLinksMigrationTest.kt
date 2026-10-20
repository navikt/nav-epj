package no.nav.helse.core.db

import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import no.nav.helse.utils.WithPostgresql
import org.flywaydb.core.Flyway
import org.junit.Test

class LegacyLinksMigrationTest : WithPostgresql() {

    private fun flyway(target: String) =
        Flyway.configure()
            .dataSource(config.postgres.url, config.postgres.username, config.postgres.password)
            .locations("db/migration")
            .cleanDisabled(false)
            .target(target)
            .load()

    private fun <T> query(block: (java.sql.Statement) -> T): T =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use { connection -> connection.createStatement().use(block) }

    @Test
    fun `V11 turns legacy patient links into fastlegeliste membership`() {
        flyway("10").clean()
        flyway("10").migrate()

        val org = UUID.randomUUID()
        val linked = UUID.randomUUID()
        val unlinked = UUID.randomUUID()
        val onList = UUID.randomUUID()
        query { statement ->
            statement.execute(
                "INSERT INTO legekontor (id, navn, tlf, orgnummer) " +
                    "VALUES ('$org', 'Legekontor', '12345678', '999999999')"
            )
            statement.execute(
                "INSERT INTO pasient (id, legekontor_id, fornavn, etternavn, personident) VALUES " +
                    "('$linked', '$org', 'A', 'A', 'legacy-1'), " +
                    "('$unlinked', NULL, 'B', 'B', 'legacy-2'), " +
                    "('$onList', '$org', 'C', 'C', 'legacy-3')"
            )
            statement.execute(
                "INSERT INTO pasient_helsepersonell (pasient_id, hpr) VALUES " +
                    "('$linked', '111'), ('$linked', '222'), ('$unlinked', '111'), " +
                    "('$onList', '333')"
            )
            statement.execute(
                "INSERT INTO fastlegeliste (hpr, legekontor_id) VALUES ('900', '$org')"
            )
            statement.execute(
                "INSERT INTO fastlegeliste_pasient (fastlegeliste_id, pasient_id) " +
                    "SELECT id, '$onList' FROM fastlegeliste WHERE hpr = '900'"
            )
        }

        flyway("latest").migrate()

        val membership = query { statement ->
            statement
                .executeQuery(
                    "SELECT flp.pasient_id, fl.hpr FROM fastlegeliste_pasient flp " +
                        "JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id " +
                        "WHERE flp.pasient_id IN ('$linked', '$unlinked', '$onList') " +
                        "ORDER BY fl.hpr"
                )
                .use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) }
                }
        }
        assertEquals(listOf(linked.toString() to "111", onList.toString() to "900"), membership)
    }
}
