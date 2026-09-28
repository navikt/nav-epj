package no.nav.helse.epj.pasient

import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.legekontor.Legekontor
import no.nav.helse.utils.WithPostgresql
import org.junit.Test

class PasientRepositoryTest : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    val pasientRepository = PasientRepository()

    @OptIn(ExperimentalUuidApi::class)
    private fun nyPasient(
        id: PasientId = PasientId(Uuid.generateV4()),
        hpr: HelsepersonellHpr = HelsepersonellHpr("123"),
        personident: String = "personident-${id.value}",
        personidentType: PersonidentType = PersonidentType.FNR,
        birthDate: LocalDate = LocalDate.of(1980, 1, 2),
        gender: AdministrativeGender = AdministrativeGender.FEMALE,
    ) =
        Pasient(
            id = id,
            legekontorId = Legekontor.DEFAULT.id,
            hprNumbers = listOf(hpr),
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
    fun `insert with the same id twice does not create a duplicate`() = runTest {
        val pasient = nyPasient()
        pasientRepository.insert(pasient)
        pasientRepository.insert(pasient.copy(fornavn = "annet navn"))

        val funnet = pasientRepository.findById(pasient.id.value)

        assertEquals("fornavn", funnet?.fornavn)
    }
}
