package no.nav.helse.core.db

import java.sql.Connection
import java.sql.DriverManager
import no.nav.helse.utils.WithPostgresql
import org.junit.After
import org.junit.Before

abstract class SeededPostgresqlTest : WithPostgresql() {

    private val seedConfig = config.postgres.copy(seedTestdata = true)

    protected fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use(block)

    protected fun queryStrings(sql: String): List<List<String?>> = withConnection { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add((1..rs.metaData.columnCount).map { rs.getString(it) })
                    }
                }
            }
        }
    }

    protected fun applySeedAgain() {
        listOf("db/testdata/R__testdata.sql", "db/testdata/R__hackathon_testdata.sql").forEach {
            val sql = checkNotNull(javaClass.classLoader.getResource(it)).readText()
            withConnection { connection ->
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

    @Before
    fun migrateWithSeed() {
        getFlyway(seedConfig).clean()
        getFlyway(seedConfig).migrate()
    }

    @After
    fun restoreSchemaWithoutSeed() {
        getFlyway(config.postgres).clean()
        getFlyway(config.postgres).migrate()
    }
}
