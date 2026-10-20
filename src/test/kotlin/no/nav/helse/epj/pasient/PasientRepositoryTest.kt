package no.nav.helse.epj.pasient

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.FastlegelistePasientTable
import no.nav.helse.core.db.FastlegelisteTable
import no.nav.helse.core.db.LegekontorTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.DuplikatPasientException
import no.nav.helse.core.utils.IngenFastlegelisteException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.konsultasjon.KonsultasjonRepository
import no.nav.helse.epj.konsultasjon.OpprettKonsultasjon
import no.nav.helse.utils.WithPostgresql
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class PasientRepositoryTest : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    val pasientRepository = PasientRepository()

    @OptIn(ExperimentalUuidApi::class)
    private fun nyPasient(
        id: PasientId = PasientId(Uuid.generateV4()),
        personident: String = "personident-${id.value}",
        personidentType: PersonidentType = PersonidentType.FNR,
        birthDate: LocalDate = LocalDate.of(1980, 1, 2),
        gender: AdministrativeGender = AdministrativeGender.FEMALE,
    ) =
        Pasient(
            id = id,
            fornavn = "fornavn",
            etternavn = "etternavn",
            personident = personident,
            personidentType = personidentType,
            birthDate = birthDate,
            gender = gender,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `findById returns null when patient does not exist`() = runTest {
        assertNull(pasientRepository.findById(Uuid.generateV4()))
    }

    @Test
    fun `findById returns patient after insert`() = runTest {
        val pasient = nyPasient()
        pasientRepository.insert(pasient)

        val funnet = pasientRepository.findById(pasient.id.value)

        assertEquals(pasient.id, funnet?.id)
        assertEquals(pasient.personident, funnet?.personident)
        assertEquals(pasient.personidentType, funnet?.personidentType)
        assertEquals(pasient.birthDate, funnet?.birthDate)
        assertEquals(pasient.gender, funnet?.gender)
        assertEquals(pasient.fornavn, funnet?.fornavn)
        assertEquals(pasient.etternavn, funnet?.etternavn)
    }

    @Test
    fun `findByPersonident returns null when personident does not exist`() = runTest {
        assertNull(pasientRepository.findByPersonident("finnes-ikke"))
    }

    @Test
    fun `findByPersonident returns patient with FNR demographics`() = runTest {
        val pasient = nyPasient(personident = "12345678910")
        pasientRepository.insert(pasient)

        val funnet = pasientRepository.findByPersonident("12345678910")

        assertEquals(pasient, funnet)
    }

    @Test
    fun `findByPersonident returns patient with DNR demographics`() = runTest {
        val pasient =
            nyPasient(
                personident = "45128012345",
                personidentType = PersonidentType.DNR,
                birthDate = LocalDate.of(1980, 12, 5),
                gender = AdministrativeGender.MALE,
            )
        pasientRepository.insert(pasient)

        val funnet = pasientRepository.findByPersonident("45128012345")

        assertEquals(pasient, funnet)
    }

    @Test
    fun `legacy patient remains readable without invented demographics`() = runTest {
        val legacyPatient = pasientRepository.findByPersonident("21914897936")

        assertEquals("21914897936", legacyPatient?.personident)
        assertNull(legacyPatient?.personidentType)
        assertNull(legacyPatient?.birthDate)
        assertNull(legacyPatient?.gender)
    }

    @Test
    fun `insert with the same id twice throws DuplikatPasientException and leaves data unchanged`() =
        runTest {
            val pasient = nyPasient()
            pasientRepository.insert(pasient)

            assertFailsWith<DuplikatPasientException> {
                pasientRepository.insert(pasient.copy(fornavn = "annet navn"))
            }

            val funnet = pasientRepository.findById(pasient.id.value)
            assertEquals("fornavn", funnet?.fornavn)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insert with the same personident but a different id throws DuplikatPasientException and leaves data unchanged`() =
        runTest {
            val original = nyPasient(personident = "15068500017")
            pasientRepository.insert(original)

            val duplikat = nyPasient(id = PasientId(Uuid.generateV4()), personident = "15068500017")

            assertFailsWith<DuplikatPasientException> { pasientRepository.insert(duplikat) }

            val funnetOriginal = pasientRepository.findById(original.id.value)
            assertEquals(original.fornavn, funnetOriginal?.fornavn)

            // the never-persisted duplicate id must not exist as a patient or association, and no
            // foreign-key error should have occurred while attempting to link it.
            assertNull(pasientRepository.findById(duplikat.id.value))
        }

    private val fjordvik = Uuid.generateV4()
    private val lyngdal = Uuid.generateV4()

    private suspend fun ensureOrgs() = dbQuery {
        listOf(fjordvik to "311111116", lyngdal to "311111124").forEach { (orgId, nr) ->
            LegekontorTable.insertIgnore {
                it[LegekontorTable.id] = orgId
                it[navn] = "Org $nr"
                it[tlf] = "22000000"
                it[orgnummer] = nr
            }
        }
    }

    private suspend fun orgId(orgnummer: String) = dbQuery {
        LegekontorTable.selectAll()
            .where { LegekontorTable.orgnummer eq orgnummer }
            .single()[LegekontorTable.id]
    }

    private suspend fun listFor(hpr: String, orgnummer: String): Uuid {
        val legekontor = orgId(orgnummer)
        return dbQuery {
            FastlegelisteTable.insert {
                    it[id] = Uuid.generateV4()
                    it[FastlegelisteTable.hpr] = hpr
                    it[legekontorId] = legekontor
                }
                .let { st -> st[FastlegelisteTable.id] }
        }
    }

    private suspend fun startKonsultasjonAt(pasient: Pasient, orgnummer: String) =
        KonsultasjonRepository()
            .insert(
                OpprettKonsultasjon(
                    pasient.id,
                    listOf(HelsepersonellHpr("123")),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                    orgnummer,
                )
            )

    private suspend fun putOnList(pasient: Pasient, listId: Uuid) = dbQuery {
        FastlegelistePasientTable.insert {
            it[fastlegelisteId] = listId
            it[pasientId] = pasient.id.value
        }
    }

    @Test
    fun `listAtLegekontor returns all lists at the org with the own list first`() = runTest {
        ensureOrgs()
        val ownHpr = "own-${Uuid.generateV4()}"
        val otherHpr = "other-${Uuid.generateV4()}"
        val abroad = "abroad-${Uuid.generateV4()}"
        val own = nyPasient().copy(etternavn = "Zzz", fornavn = "Own")
        val other = nyPasient().copy(etternavn = "Aaa", fornavn = "Other")
        val elsewhere = nyPasient().copy(etternavn = "Bbb", fornavn = "Elsewhere")
        listOf(own, other, elsewhere).forEach { pasientRepository.insert(it) }
        putOnList(own, listFor(ownHpr, "311111116"))
        putOnList(other, listFor(otherHpr, "311111116"))
        putOnList(elsewhere, listFor(abroad, "311111124"))

        val liste = pasientRepository.listAtLegekontor("311111116", HelsepersonellHpr(ownHpr))

        val ids = liste.map { it.pasient.id }
        assertEquals(listOf(own.id, other.id), ids.filter { it in setOf(own.id, other.id) })
        assertFalse(elsewhere.id in ids)
    }

    @Test
    fun `a patient with konsultasjoner at the org but no list there is listed as not selectable`() =
        runTest {
            ensureOrgs()
            val ownHpr = "own-${Uuid.generateV4()}"
            val listed = nyPasient().copy(etternavn = "Zzz")
            val lapsed = nyPasient().copy(etternavn = "Aaa")
            val movedAway = nyPasient()
            listOf(listed, lapsed, movedAway).forEach { pasientRepository.insert(it) }
            putOnList(listed, listFor(ownHpr, "311111116"))
            putOnList(movedAway, listFor("abroad-${Uuid.generateV4()}", "311111124"))
            listOf(lapsed, movedAway).forEach { startKonsultasjonAt(it, "311111116") }

            val liste =
                pasientRepository.listAtLegekontor("311111116", HelsepersonellHpr(ownHpr)).filter {
                    it.pasient.id in setOf(listed.id, lapsed.id, movedAway.id)
                }

            assertEquals(listOf(listed.id, lapsed.id, movedAway.id), liste.map { it.pasient.id })
            assertEquals(listOf(true, false, false), liste.map { it.selectable })
            assertTrue(pasientRepository.hasKonsultasjonAt(lapsed.id.value, "311111116"))
            assertFalse(pasientRepository.hasKonsultasjonAt(lapsed.id.value, "311111124"))
        }

    @Test
    fun `isListedAt is true only at the org whose list holds the patient`() = runTest {
        ensureOrgs()
        val pasient = nyPasient()
        pasientRepository.insert(pasient)
        putOnList(pasient, listFor("h-${Uuid.generateV4()}", "311111116"))

        assertTrue(pasientRepository.isListedAt(pasient.id.value, "311111116"))
        assertFalse(pasientRepository.isListedAt(pasient.id.value, "311111124"))
    }

    @Test
    fun `insertOnList puts the patient on the own list at the org`() = runTest {
        ensureOrgs()
        val hpr = "h-${Uuid.generateV4()}"
        listFor(hpr, "311111116")
        val pasient = nyPasient()

        pasientRepository.insertOnList(pasient, HelsepersonellHpr(hpr), "311111116")

        assertTrue(pasientRepository.isListedAt(pasient.id.value, "311111116"))
    }

    @Test
    fun `insertOnList without an own list at the org inserts nothing`() = runTest {
        ensureOrgs()
        val pasient = nyPasient()

        assertFailsWith<IngenFastlegelisteException> {
            pasientRepository.insertOnList(pasient, HelsepersonellHpr("no-list"), "311111116")
        }

        assertNull(pasientRepository.findById(pasient.id.value))
    }

    @Test
    fun `a patient on another doctor's list at the same org is selectable by any doctor there`() =
        runTest {
            ensureOrgs()
            val pasient = nyPasient()
            pasientRepository.insert(pasient)
            putOnList(pasient, listFor("colleague-${Uuid.generateV4()}", "311111116"))

            val liste = pasientRepository.listAtLegekontor("311111116", HelsepersonellHpr("me"))

            assertEquals(true, liste.single { it.pasient.id == pasient.id }.selectable)
            assertTrue(pasientRepository.isListedAt(pasient.id.value, "311111116"))
        }

    @Test
    fun `moving a patient to a list at another org keeps the clinical data at the first org`() =
        runTest {
            ensureOrgs()
            val pasient = nyPasient()
            pasientRepository.insert(pasient)
            val oldList = listFor("a-${Uuid.generateV4()}", "311111116")
            putOnList(pasient, oldList)
            val konsultasjonId = startKonsultasjonAt(pasient, "311111116")

            dbQuery { FastlegelistePasientTable.deleteWhere { pasientId eq pasient.id.value } }
            putOnList(pasient, listFor("b-${Uuid.generateV4()}", "311111124"))

            val konsultasjoner = KonsultasjonRepository()
            assertEquals(
                konsultasjonId,
                konsultasjoner.findByKonsultasjonId(konsultasjonId, "311111116")?.id,
            )
            assertNull(konsultasjoner.findByKonsultasjonId(konsultasjonId, "311111124"))
            assertTrue(konsultasjoner.listByPasientId(pasient.id, "311111124").isEmpty())

            val atFirst =
                pasientRepository.listAtLegekontor("311111116", HelsepersonellHpr("a")).single {
                    it.pasient.id == pasient.id
                }
            assertFalse(atFirst.selectable)
            val atSecond =
                pasientRepository.listAtLegekontor("311111124", HelsepersonellHpr("b")).single {
                    it.pasient.id == pasient.id
                }
            assertTrue(atSecond.selectable)
        }
}
