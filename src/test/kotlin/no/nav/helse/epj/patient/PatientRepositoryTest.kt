package no.nav.helse.epj.patient

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
import no.nav.helse.core.db.ClinicTable
import no.nav.helse.core.db.FastlegelistePatientTable
import no.nav.helse.core.db.FastlegelisteTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.DuplicatePatientException
import no.nav.helse.core.utils.IngenFastlegelisteException
import no.nav.helse.core.utils.KonsultasjonStatus
import no.nav.helse.epj.konsultasjon.KonsultasjonRepository
import no.nav.helse.epj.konsultasjon.OpprettKonsultasjon
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.WithPostgresql
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Test

@OptIn(ExperimentalUuidApi::class)
class PatientRepositoryTest : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    val patientRepository = PatientRepository()

    @OptIn(ExperimentalUuidApi::class)
    private fun newPatient(
        id: PatientId = PatientId(Uuid.generateV4()),
        personident: String = "personident-${id.value}",
        personidentType: PersonidentType = PersonidentType.FNR,
        birthDate: LocalDate = LocalDate.of(1980, 1, 2),
        gender: AdministrativeGender = AdministrativeGender.FEMALE,
    ) =
        Patient(
            id = id,
            givenName = "givenName",
            familyName = "familyName",
            personident = personident,
            personidentType = personidentType,
            birthDate = birthDate,
            gender = gender,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `findById returns null when patient does not exist`() = runTest {
        assertNull(patientRepository.findById(Uuid.generateV4()))
    }

    @Test
    fun `findById returns patient after insert`() = runTest {
        val patient = newPatient()
        patientRepository.insert(patient)

        val funnet = patientRepository.findById(patient.id.value)

        assertEquals(patient.id, funnet?.id)
        assertEquals(patient.personident, funnet?.personident)
        assertEquals(patient.personidentType, funnet?.personidentType)
        assertEquals(patient.birthDate, funnet?.birthDate)
        assertEquals(patient.gender, funnet?.gender)
        assertEquals(patient.givenName, funnet?.givenName)
        assertEquals(patient.familyName, funnet?.familyName)
    }

    @Test
    fun `findByPersonident returns null when personident does not exist`() = runTest {
        assertNull(patientRepository.findByPersonident("finnes-ikke"))
    }

    @Test
    fun `findByPersonident returns patient with FNR demographics`() = runTest {
        val patient = newPatient(personident = "12345678910")
        patientRepository.insert(patient)

        val funnet = patientRepository.findByPersonident("12345678910")

        assertEquals(patient, funnet)
    }

    @Test
    fun `findByPersonident returns patient with DNR demographics`() = runTest {
        val patient =
            newPatient(
                personident = "45128012345",
                personidentType = PersonidentType.DNR,
                birthDate = LocalDate.of(1980, 12, 5),
                gender = AdministrativeGender.MALE,
            )
        patientRepository.insert(patient)

        val funnet = patientRepository.findByPersonident("45128012345")

        assertEquals(patient, funnet)
    }

    @Test
    fun `legacy patient remains readable without invented demographics`() = runTest {
        patientRepository.insert(
            Patient(
                id = PatientId(Uuid.generateV4()),
                givenName = "givenName",
                familyName = "familyName",
                personident = "21914897936",
            )
        )

        val legacyPatient = patientRepository.findByPersonident("21914897936")

        assertEquals("21914897936", legacyPatient?.personident)
        assertNull(legacyPatient?.personidentType)
        assertNull(legacyPatient?.birthDate)
        assertNull(legacyPatient?.gender)
    }

    @Test
    fun `insert with the same id twice throws DuplicatePatientException and leaves data unchanged`() =
        runTest {
            val patient = newPatient()
            patientRepository.insert(patient)

            assertFailsWith<DuplicatePatientException> {
                patientRepository.insert(patient.copy(givenName = "annet navn"))
            }

            val funnet = patientRepository.findById(patient.id.value)
            assertEquals("givenName", funnet?.givenName)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `insert with the same personident but a different id throws DuplicatePatientException and leaves data unchanged`() =
        runTest {
            val original = newPatient(personident = "15068500017")
            patientRepository.insert(original)

            val duplikat =
                newPatient(id = PatientId(Uuid.generateV4()), personident = "15068500017")

            assertFailsWith<DuplicatePatientException> { patientRepository.insert(duplikat) }

            val funnetOriginal = patientRepository.findById(original.id.value)
            assertEquals(original.givenName, funnetOriginal?.givenName)

            // the never-persisted duplicate id must not exist as a patient or association, and no
            // foreign-key error should have occurred while attempting to link it.
            assertNull(patientRepository.findById(duplikat.id.value))
        }

    private val fjordvik = Uuid.generateV4()
    private val lyngdal = Uuid.generateV4()

    private suspend fun ensureOrgs() = dbQuery {
        listOf(fjordvik to "311111116", lyngdal to "311111124").forEach { (orgId, nr) ->
            ClinicTable.insertIgnore {
                it[ClinicTable.id] = orgId
                it[name] = "Org $nr"
                it[phone] = "22000000"
                it[orgNumber] = nr
            }
        }
    }

    private suspend fun orgId(orgNumber: String) = dbQuery {
        ClinicTable.selectAll()
            .where { ClinicTable.orgNumber eq orgNumber }
            .single()[ClinicTable.id]
    }

    private suspend fun listFor(hpr: String, orgNumber: String): Uuid {
        val clinic = orgId(orgNumber)
        return dbQuery {
            FastlegelisteTable.insert {
                    it[id] = Uuid.generateV4()
                    it[FastlegelisteTable.hpr] = hpr
                    it[clinicId] = clinic
                }
                .let { st -> st[FastlegelisteTable.id] }
        }
    }

    private suspend fun startKonsultasjonAt(patient: Patient, orgNumber: String) =
        KonsultasjonRepository()
            .insert(
                OpprettKonsultasjon(
                    patient.id,
                    listOf(PractitionerHpr("123")),
                    LocalDateTime.now(),
                    KonsultasjonStatus.PAAGAAENDE,
                    orgNumber,
                )
            )

    private suspend fun startKonsultasjon(
        patient: Patient,
        orgNumber: String,
        startet: LocalDateTime,
        status: KonsultasjonStatus,
    ) =
        KonsultasjonRepository()
            .insert(
                OpprettKonsultasjon(
                    patient.id,
                    listOf(PractitionerHpr("123")),
                    startet,
                    status,
                    orgNumber,
                )
            )

    private suspend fun putOnList(patient: Patient, listId: Uuid) = dbQuery {
        FastlegelistePatientTable.insert {
            it[fastlegelisteId] = listId
            it[patientId] = patient.id.value
        }
    }

    @Test
    fun `listAtClinic returns all lists at the org with the own list first`() = runTest {
        ensureOrgs()
        val ownHpr = "own-${Uuid.generateV4()}"
        val otherHpr = "other-${Uuid.generateV4()}"
        val abroad = "abroad-${Uuid.generateV4()}"
        val own = newPatient().copy(familyName = "Zzz", givenName = "Own")
        val other = newPatient().copy(familyName = "Aaa", givenName = "Other")
        val elsewhere = newPatient().copy(familyName = "Bbb", givenName = "Elsewhere")
        listOf(own, other, elsewhere).forEach { patientRepository.insert(it) }
        putOnList(own, listFor(ownHpr, "311111116"))
        putOnList(other, listFor(otherHpr, "311111116"))
        putOnList(elsewhere, listFor(abroad, "311111124"))

        val liste = patientRepository.listAtClinic("311111116", PractitionerHpr(ownHpr))

        val ids = liste.map { it.patient.id }
        assertEquals(listOf(own.id, other.id), ids.filter { it in setOf(own.id, other.id) })
        assertFalse(elsewhere.id in ids)
    }

    @Test
    fun `a patient with konsultasjoner at the org but no list there is listed as not selectable`() =
        runTest {
            ensureOrgs()
            val ownHpr = "own-${Uuid.generateV4()}"
            val listed = newPatient().copy(familyName = "Zzz")
            val lapsed = newPatient().copy(familyName = "Aaa")
            val movedAway = newPatient()
            listOf(listed, lapsed, movedAway).forEach { patientRepository.insert(it) }
            putOnList(listed, listFor(ownHpr, "311111116"))
            putOnList(movedAway, listFor("abroad-${Uuid.generateV4()}", "311111124"))
            listOf(lapsed, movedAway).forEach { startKonsultasjonAt(it, "311111116") }

            val liste =
                patientRepository.listAtClinic("311111116", PractitionerHpr(ownHpr)).filter {
                    it.patient.id in setOf(listed.id, lapsed.id, movedAway.id)
                }

            assertEquals(listOf(listed.id, lapsed.id, movedAway.id), liste.map { it.patient.id })
            assertEquals(listOf(true, false, false), liste.map { it.selectable })
            assertTrue(patientRepository.hasKonsultasjonAt(lapsed.id.value, "311111116"))
            assertFalse(patientRepository.hasKonsultasjonAt(lapsed.id.value, "311111124"))
        }

    @Test
    fun `listAtClinic includes the latest konsultasjon at the org only`() = runTest {
        ensureOrgs()
        val ownHpr = "own-${Uuid.generateV4()}"
        val ongoing = newPatient()
        val completedLast = newPatient()
        val none = newPatient()
        val otherOrgOnly = newPatient()
        val listId = listFor(ownHpr, "311111116")
        listOf(ongoing, completedLast, none, otherOrgOnly).forEach {
            patientRepository.insert(it)
            putOnList(it, listId)
        }
        val now = LocalDateTime.now()
        startKonsultasjon(ongoing, "311111116", now.minusDays(2), KonsultasjonStatus.FULLFOERT)
        startKonsultasjon(ongoing, "311111116", now, KonsultasjonStatus.PAAGAAENDE)
        startKonsultasjon(
            completedLast,
            "311111116",
            now.minusDays(3),
            KonsultasjonStatus.PAAGAAENDE,
        )
        startKonsultasjon(
            completedLast,
            "311111116",
            now.minusDays(1),
            KonsultasjonStatus.FULLFOERT,
        )
        startKonsultasjon(otherOrgOnly, "311111124", now, KonsultasjonStatus.PAAGAAENDE)

        val liste =
            patientRepository.listAtClinic("311111116", PractitionerHpr(ownHpr)).associateBy {
                it.patient.id
            }

        assertEquals(
            KonsultasjonStatus.PAAGAAENDE,
            liste.getValue(ongoing.id).latestConsultation?.status,
        )
        assertEquals(
            KonsultasjonStatus.FULLFOERT,
            liste.getValue(completedLast.id).latestConsultation?.status,
        )
        assertNull(liste.getValue(none.id).latestConsultation)
        assertNull(liste.getValue(otherOrgOnly.id).latestConsultation)
    }

    @Test
    fun `isListedAt is true only at the org whose list holds the patient`() = runTest {
        ensureOrgs()
        val patient = newPatient()
        patientRepository.insert(patient)
        putOnList(patient, listFor("h-${Uuid.generateV4()}", "311111116"))

        assertTrue(patientRepository.isListedAt(patient.id.value, "311111116"))
        assertFalse(patientRepository.isListedAt(patient.id.value, "311111124"))
    }

    @Test
    fun `insertOnList puts the patient on the own list at the org`() = runTest {
        ensureOrgs()
        val hpr = "h-${Uuid.generateV4()}"
        listFor(hpr, "311111116")
        val patient = newPatient()

        patientRepository.insertOnList(patient, PractitionerHpr(hpr), "311111116")

        assertTrue(patientRepository.isListedAt(patient.id.value, "311111116"))
    }

    @Test
    fun `insertOnList without an own list at the org inserts nothing`() = runTest {
        ensureOrgs()
        val patient = newPatient()

        assertFailsWith<IngenFastlegelisteException> {
            patientRepository.insertOnList(patient, PractitionerHpr("no-list"), "311111116")
        }

        assertNull(patientRepository.findById(patient.id.value))
    }

    @Test
    fun `a patient on another doctor's list at the same org is selectable by any doctor there`() =
        runTest {
            ensureOrgs()
            val patient = newPatient()
            patientRepository.insert(patient)
            putOnList(patient, listFor("colleague-${Uuid.generateV4()}", "311111116"))

            val liste = patientRepository.listAtClinic("311111116", PractitionerHpr("me"))

            assertEquals(true, liste.single { it.patient.id == patient.id }.selectable)
            assertTrue(patientRepository.isListedAt(patient.id.value, "311111116"))
        }

    @Test
    fun `moving a patient to a list at another org keeps the clinical data at the first org`() =
        runTest {
            ensureOrgs()
            val patient = newPatient()
            patientRepository.insert(patient)
            val oldList = listFor("a-${Uuid.generateV4()}", "311111116")
            putOnList(patient, oldList)
            val konsultasjonId = startKonsultasjonAt(patient, "311111116")

            dbQuery { FastlegelistePatientTable.deleteWhere { patientId eq patient.id.value } }
            putOnList(patient, listFor("b-${Uuid.generateV4()}", "311111124"))

            val konsultasjoner = KonsultasjonRepository()
            assertEquals(
                konsultasjonId,
                konsultasjoner.findByKonsultasjonId(konsultasjonId, "311111116")?.id,
            )
            assertNull(konsultasjoner.findByKonsultasjonId(konsultasjonId, "311111124"))
            assertTrue(konsultasjoner.listByPatientId(patient.id, "311111124").isEmpty())

            val atFirst =
                patientRepository.listAtClinic("311111116", PractitionerHpr("a")).single {
                    it.patient.id == patient.id
                }
            assertFalse(atFirst.selectable)
            val atSecond =
                patientRepository.listAtClinic("311111124", PractitionerHpr("b")).single {
                    it.patient.id == patient.id
                }
            assertTrue(atSecond.selectable)
        }
}
