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
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientRepository
import no.nav.helse.utils.WithPostgresql
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class HackathonClinicalSeedTest : WithPostgresql() {

    private val seedConfig = config.postgres.copy(seedTestdata = true)
    private val konsultasjonRepository = KonsultasjonRepository()
    private val maalingRepository = MaalingRepository()
    private val pasientRepository = PasientRepository()

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
                            "konsultasjon",
                            "konsultasjon_helsepersonell",
                            "konsultasjon_diagnosekode",
                            "journalnotat",
                            "maaling",
                        )
                        .map {
                            statement.executeQuery("SELECT count(*) FROM $it").use { rs ->
                                rs.next()
                                rs.getString(1)
                            }
                        }
                }
            }

    private suspend fun pasientId(personident: String) =
        PasientId(checkNotNull(pasientRepository.findByPersonident(personident)).id.value)

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
        val gunnar = pasientId("23895710156")

        val konsultasjoner = konsultasjonRepository.listByPasientId(gunnar, orgWithGunnar)
        assertEquals(5, konsultasjoner.size)
        assertEquals(1, konsultasjoner.count { it.status == KonsultasjonStatus.PLANLAGT })
        assertEquals(
            setOf("K86", "I10", "T90", "E11.9"),
            konsultasjoner.flatMap { it.diagnoser }.map { it.code }.toSet(),
        )
        assertTrue(konsultasjoner.all { it.hpr == listOf("900000103") })

        val hba1c =
            maalingRepository.listByPasientId(gunnar, orgWithGunnar).filter {
                it.loincKode == "4548-4"
            }
        assertEquals(
            listOf("7.9", "7.6", "7.2", "6.9"),
            hba1c
                .sortedBy { it.effektivTidspunkt }
                .map { it.verdi.stripTrailingZeros().toPlainString() },
        )
        assertTrue(hba1c.all { it.enhetKode == "%" })
    }

    @Test
    fun `seeded clinical data is invisible at another org`() = runTest {
        val gunnar = pasientId("23895710156")

        assertTrue(konsultasjonRepository.listByPasientId(gunnar, orgWithBjarne).isEmpty())
        assertTrue(maalingRepository.listByPasientId(gunnar, orgWithBjarne).isEmpty())
    }

    @Test
    fun `active konsultasjon is seeded for some patients`() = runTest {
        val bjarne = pasientId("20856210112")

        assertNotNull(konsultasjonRepository.findActiveByPasientId(bjarne, orgWithBjarne))
        assertNull(konsultasjonRepository.findActiveByPasientId(bjarne, orgWithGunnar))
    }

    @Test
    fun `applying the seed again duplicates nothing and keeps edits`() = runTest {
        val before = counts()
        assertTrue(before.all { it.toInt() > 0 })
        execute(
            "UPDATE konsultasjon SET problemstilling = 'Endret av bruker' " +
                "WHERE status = 'PÅGÅENDE'"
        )
        val sql =
            checkNotNull(javaClass.classLoader.getResource("db/testdata/R__hackathon_testdata.sql"))
                .readText()

        execute(sql)

        assertEquals(before, counts())
        val bjarne = pasientId("20856210112")
        assertEquals(
            "Endret av bruker",
            konsultasjonRepository.findActiveByPasientId(bjarne, orgWithBjarne)?.problemstilling,
        )
    }
}
