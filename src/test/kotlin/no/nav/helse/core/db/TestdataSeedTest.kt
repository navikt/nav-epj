package no.nav.helse.core.db

import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.nav.helse.epj.pasient.PersonidentType
import no.nav.helse.epj.pasient.PersonidentValidator
import no.nav.helse.utils.WithPostgresql
import no.nav.tsm.diagnoser.Diagnose
import org.junit.After
import org.junit.Before
import org.junit.Test

class TestdataSeedTest : WithPostgresql() {

    private val seedConfig = config.postgres.copy(seedTestdata = true)

    private fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection(
                config.postgres.url,
                config.postgres.username,
                config.postgres.password,
            )
            .use(block)

    private fun queryStrings(sql: String): List<List<String?>> = withConnection { connection ->
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

    private fun count(table: String) = queryStrings("SELECT count(*) FROM $table").single().single()

    private fun applySeedAgain() {
        listOf("db/testdata/R__testdata.sql", "db/testdata/R__hackathon_testdata.sql").forEach {
            val sql = checkNotNull(javaClass.classLoader.getResource(it)).readText()
            withConnection { connection ->
                connection.createStatement().use { statement -> statement.execute(sql) }
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

    @Test
    fun `every seeded diagnosis code exists in the diagnosis library`() {
        val koder =
            queryStrings(
                "SELECT DISTINCT diagnosesystem, diagnosekode FROM konsultasjon_diagnosekode"
            )

        assertTrue(koder.isNotEmpty())
        koder.forEach { (system, kode) ->
            assertTrue(Diagnose.from(system!!, kode!!) != null, "$system $kode")
        }
    }

    @Test
    fun `legacy A09 diagnosis rows are rewritten to A09-9 when the seed is applied again`() {
        val ids =
            queryStrings(
                    "SELECT konsultasjon_id FROM konsultasjon_diagnosekode " +
                        "WHERE diagnosesystem = 'ICD10' AND diagnosekode = 'A09.9'"
                )
                .map { it.single() }
        assertTrue(ids.size >= 2)
        val (rewritten, duplicated) = ids
        withConnection { connection ->
            connection.createStatement().use {
                it.execute(
                    "UPDATE konsultasjon_diagnosekode SET diagnosekode = 'A09' " +
                        "WHERE konsultasjon_id = '$rewritten' AND diagnosekode = 'A09.9'"
                )
                it.execute(
                    "INSERT INTO konsultasjon_diagnosekode VALUES ('$duplicated', 'ICD10', 'A09')"
                )
            }
        }

        applySeedAgain()

        assertEquals(
            0,
            queryStrings(
                    "SELECT count(*) FROM konsultasjon_diagnosekode WHERE diagnosekode = 'A09'"
                )
                .single()
                .single()!!
                .toInt(),
        )
        assertEquals(
            listOf(listOf("A09.9")),
            queryStrings(
                "SELECT diagnosekode FROM konsultasjon_diagnosekode " +
                    "WHERE konsultasjon_id = '$rewritten' AND diagnosekode LIKE 'A09%'"
            ),
        )
        assertEquals(
            listOf(listOf("A09.9")),
            queryStrings(
                "SELECT diagnosekode FROM konsultasjon_diagnosekode " +
                    "WHERE konsultasjon_id = '$duplicated' AND diagnosekode LIKE 'A09%'"
            ),
        )
    }

    @Test
    fun `every seeded patient with demographics has a valid synthetic personident`() {
        val patients =
            queryStrings(
                "SELECT personident, personident_type, birth_date FROM pasient WHERE birth_date IS NOT NULL"
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
                        "FROM pasient WHERE fornavn = 'MATEMATISK'"
                )
                .single()

        assertTrue(PersonidentValidator.isSynthetic(row[0]!!))
        assertEquals(listOf(null, null, null), row.drop(1))
    }

    @Test
    fun `V7 patient is moved to a synthetic personident without a duplicate`() {
        val rows =
            queryStrings(
                "SELECT id, personident FROM pasient WHERE fornavn = 'FRISK' AND etternavn = 'REV'"
            )

        assertEquals(1, rows.size)
        assertEquals("279d2ae3-5be5-4f07-a9df-c9b660201e50", rows.single()[0])
        assertTrue(PersonidentValidator.isSynthetic(rows.single()[1]!!))
    }

    @Test
    fun `seeded organisations have valid mod11 orgnummer`() {
        val weights = intArrayOf(3, 2, 7, 6, 5, 4, 3, 2)
        val seeded =
            queryStrings(
                "SELECT orgnummer FROM legekontor WHERE orgnummer ~ '^[0-9]{9}$' AND orgnummer LIKE '3%'"
            )

        assertEquals(6, seeded.size)
        seeded.forEach { (orgnummer) ->
            val digits = orgnummer!!.map { it - '0' }
            val remainder = digits.take(8).indices.sumOf { digits[it] * weights[it] } % 11
            assertEquals((11 - remainder) % 11, digits[8], orgnummer)
        }
    }

    @Test
    fun `fastlegelister resolve to doctor, office and patients`() {
        val lists =
            queryStrings(
                """
                SELECT fl.hpr, lk.orgnummer, count(flp.pasient_id)
                FROM fastlegeliste fl
                         JOIN legekontor lk ON lk.id = fl.legekontor_id
                         LEFT JOIN fastlegeliste_pasient flp ON flp.fastlegeliste_id = fl.id
                GROUP BY fl.hpr, lk.orgnummer
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
                SELECT DISTINCT lk.orgnummer
                FROM pasient p
                         JOIN fastlegeliste_pasient flp ON flp.pasient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                         JOIN legekontor lk ON lk.id = fl.legekontor_id
                WHERE p.etternavn = 'FJORD'
                ORDER BY lk.orgnummer
                """
                    .trimIndent()
            )

        assertEquals(listOf(listOf("311111116"), listOf("311111124")), offices)
    }

    @Test
    fun `applying the seed again is idempotent and leaves clinical data untouched`() {
        val konsultasjonId = UUID.randomUUID()
        withConnection { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    INSERT INTO konsultasjon (id, pasient_id, legekontor_id, startet_tidspunkt, status)
                    SELECT '$konsultasjonId', p.id, fl.legekontor_id, now(), 'planlagt'
                    FROM pasient p
                             JOIN fastlegeliste_pasient flp ON flp.pasient_id = p.id
                             JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                    WHERE p.fornavn = 'TRYGG'
                    """
                        .trimIndent()
                )
            }
        }
        val before =
            listOf(
                    "legekontor",
                    "pasient",
                    "fastlegeliste",
                    "fastlegeliste_pasient",
                    "konsultasjon",
                )
                .map { count(it) }

        applySeedAgain()

        val after =
            listOf(
                    "legekontor",
                    "pasient",
                    "fastlegeliste",
                    "fastlegeliste_pasient",
                    "konsultasjon",
                )
                .map { count(it) }
        assertEquals(before, after)
        assertEquals(
            listOf(listOf(konsultasjonId.toString())),
            queryStrings("SELECT id FROM konsultasjon WHERE id = '$konsultasjonId'"),
        )
    }

    private fun clinicalCounts(orgnummer: String): List<String> =
        queryStrings(
                """
                SELECT (SELECT count(*) FROM konsultasjon k WHERE k.legekontor_id = lk.id),
                       (SELECT count(*) FROM konsultasjon_diagnosekode d
                                 JOIN konsultasjon k ON k.id = d.konsultasjon_id WHERE k.legekontor_id = lk.id),
                       (SELECT count(*) FROM maaling m
                                 JOIN konsultasjon k ON k.id = m.konsultasjon_id WHERE k.legekontor_id = lk.id),
                       (SELECT count(*) FROM journalnotat j
                                 JOIN konsultasjon k ON k.id = j.konsultasjon_id WHERE k.legekontor_id = lk.id)
                FROM legekontor lk
                WHERE lk.orgnummer = '$orgnummer'
                """
                    .trimIndent()
            )
            .single()
            .map { it!! }

    @Test
    fun `everyday test patients get clinical histories at their list office`() {
        assertEquals(listOf("40", "76", "193", "38"), clinicalCounts("311111116"))
        assertEquals(listOf("33", "82", "213", "30"), clinicalCounts("311111124"))
    }

    @Test
    fun `every patient on an everyday list has an active konsultasjon at the list office`() {
        val withoutActive =
            queryStrings(
                """
                SELECT p.fornavn
                FROM pasient p
                         JOIN fastlegeliste_pasient flp ON flp.pasient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                WHERE fl.hpr IN ('565501872', '900000001')
                  AND NOT EXISTS (SELECT 1
                                  FROM konsultasjon k
                                  WHERE k.pasient_id = p.id
                                    AND k.legekontor_id = fl.legekontor_id
                                    AND k.status = 'PÅGÅENDE')
                """
                    .trimIndent()
            )

        assertEquals(emptyList(), withoutActive)
    }

    @Test
    fun `everyday patients have diagnoses, measurements and a dated history`() {
        val everyday =
            """
            SELECT flp.pasient_id
            FROM fastlegeliste_pasient flp
                     JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
            WHERE fl.hpr IN ('565501872', '900000001')
            """
                .trimIndent()

        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM konsultasjon k WHERE k.status = 'FULLFØRT' AND NOT EXISTS " +
                        "(SELECT 1 FROM konsultasjon_diagnosekode d WHERE d.konsultasjon_id = k.id)"
                )
                .single()
                .single(),
        )
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM ($everyday) e WHERE NOT EXISTS " +
                        "(SELECT 1 FROM maaling m WHERE m.pasient_id = e.pasient_id)"
                )
                .single()
                .single(),
        )
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM konsultasjon WHERE status = 'PLANLAGT' " +
                        "AND startet_tidspunkt < '2026-10-21'::timestamptz"
                )
                .single()
                .single(),
        )
        val withSeries =
            queryStrings(
                    "SELECT count(DISTINCT pasient_id) FROM (SELECT pasient_id FROM maaling " +
                        "WHERE pasient_id IN ($everyday) GROUP BY pasient_id, loinc_kode " +
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
                    SELECT m.verdi
                    FROM maaling m
                             JOIN pasient p ON p.id = m.pasient_id
                    WHERE p.fornavn = 'RASK' AND m.loinc_kode = '4548-4'
                    ORDER BY m.effektiv_tidspunkt
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
        val rask = "md5('testdata-konsultasjon-RL1')::uuid"
        withConnection { connection ->
            connection.createStatement().use {
                it.execute(
                    "DELETE FROM konsultasjon_diagnosekode WHERE konsultasjon_id = $rask " +
                        "AND diagnosekode = 'I10'"
                )
                it.execute(
                    "DELETE FROM maaling WHERE konsultasjon_id = $rask AND loinc_kode = '29463-7'"
                )
            }
        }
        val tables =
            listOf(
                "konsultasjon",
                "konsultasjon_helsepersonell",
                "konsultasjon_diagnosekode",
                "journalnotat",
                "maaling",
            )
        val before = tables.map { count(it) }

        applySeedAgain()

        assertEquals(before, tables.map { count(it) })
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM konsultasjon_diagnosekode WHERE konsultasjon_id = $rask " +
                        "AND diagnosekode = 'I10'"
                )
                .single()
                .single(),
        )
        assertEquals(
            "0",
            queryStrings(
                    "SELECT count(*) FROM maaling WHERE konsultasjon_id = $rask AND loinc_kode = '29463-7'"
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
                FROM pasient p
                         JOIN fastlegeliste_pasient flp ON flp.pasient_id = p.id
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
                SELECT lk.orgnummer, string_agg(DISTINCT p.personident_type, ',' ORDER BY p.personident_type)
                FROM pasient p
                         JOIN fastlegeliste_pasient flp ON flp.pasient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                         JOIN legekontor lk ON lk.id = fl.legekontor_id
                WHERE fl.hpr LIKE '9000001%'
                GROUP BY lk.orgnummer
                ORDER BY lk.orgnummer
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
                SELECT p.etternavn, count(DISTINCT fl.legekontor_id)
                FROM pasient p
                         JOIN fastlegeliste_pasient flp ON flp.pasient_id = p.id
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                WHERE fl.hpr LIKE '9000001%' AND p.etternavn IN ('BERGE', 'STRAND')
                GROUP BY p.etternavn
                ORDER BY p.etternavn
                """
                    .trimIndent()
            )

        assertEquals(listOf(listOf("BERGE", "2"), listOf("STRAND", "2")), households)
    }

    @Test
    fun `seed overwrites demographics and restores removed list membership`() {
        withConnection { connection ->
            connection.createStatement().use {
                it.execute("UPDATE pasient SET fornavn = 'ENDRET' WHERE fornavn = 'TRYGG'")
                it.execute("DELETE FROM fastlegeliste_pasient")
            }
        }

        applySeedAgain()

        assertEquals(
            "1",
            queryStrings("SELECT count(*) FROM pasient WHERE fornavn = 'TRYGG'").single().single(),
        )
        assertEquals("40", count("fastlegeliste_pasient"))
    }

    @Test
    fun `migrating again with the seed disabled succeeds without clean`() {
        getFlyway(config.postgres).migrate()

        assertEquals("40", count("fastlegeliste_pasient"))
    }

    @Test
    fun `seed keeps list membership of an app-created patient on a seeded list`() {
        val pasientId = UUID.randomUUID()
        withConnection { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    INSERT INTO pasient (id, fornavn, etternavn, personident)
                    VALUES ('$pasientId', 'APP', 'OPPRETTET', 'app-created-1')
                    """
                        .trimIndent()
                )
                it.execute(
                    """
                    INSERT INTO fastlegeliste_pasient (fastlegeliste_id, pasient_id)
                    SELECT id, '$pasientId' FROM fastlegeliste WHERE hpr = '565501872'
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
                FROM fastlegeliste_pasient flp
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                WHERE flp.pasient_id = '$pasientId'
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
                    UPDATE fastlegeliste_pasient
                    SET fastlegeliste_id = (SELECT id FROM fastlegeliste WHERE hpr = '900000001')
                    WHERE pasient_id = (SELECT id FROM pasient WHERE fornavn = 'TRYGG')
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
                FROM fastlegeliste_pasient flp
                         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
                         JOIN pasient p ON p.id = flp.pasient_id
                WHERE p.fornavn = 'TRYGG'
                """
                    .trimIndent()
            ),
        )
    }
}
