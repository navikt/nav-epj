package no.nav.helse.epj.maaling

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.ConsultationStatus
import no.nav.helse.core.utils.DuplikatMaalingException
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.consultation.ConsultationRepository
import no.nav.helse.epj.consultation.CreateConsultation
import no.nav.helse.epj.patient.Patient
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.patient.PatientRepository
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import no.nav.helse.utils.OTHER_ORG_NUMBER
import no.nav.helse.utils.WithPostgresql
import no.nav.helse.utils.ensureOrg
import org.junit.Test

class MaalingRepositoryTest : WithPostgresql() {
    init {
        runMigrations(true)
        connect()
    }

    val maalingRepository = MaalingRepository()
    val patientRepository = PatientRepository()
    val consultationRepository = ConsultationRepository()

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun createPatientWithConsultation(
        hpr: PractitionerHpr = PractitionerHpr("123")
    ): Pair<PatientId, ConsultationId> {
        val patientId = PatientId(Uuid.generateV4())
        patientRepository.insert(
            Patient(
                id = patientId,
                givenName = "givenName",
                familyName = "familyName",
                personident = "personident-${patientId.value}",
            )
        )
        val consultationId =
            consultationRepository.insert(
                CreateConsultation(
                    patientId,
                    listOf(hpr),
                    LocalDateTime.now(),
                    ConsultationStatus.IN_PROGRESS,
                    orgNumber = DEFAULT_ORG_NUMBER,
                )
            )
        return patientId to consultationId
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun nyMaaling(
        patientId: PatientId,
        consultationId: ConsultationId,
        id: MaalingId = MaalingId(Uuid.generateV4()),
        hpr: PractitionerHpr? = PractitionerHpr("123"),
        loincKode: String = "8310-5",
        loincVisningsnavn: String = "Body temperature",
        verdi: BigDecimal = BigDecimal("37.2000"),
        enhetKode: String = "Cel",
        enhetVisningsnavn: String = "degree Celsius",
        effektivTidspunkt: LocalDateTime = LocalDateTime.of(2026, 9, 1, 10, 15, 30, 500_000),
        status: MaalingStatus = MaalingStatus.FINAL,
    ) =
        Maaling(
            id = id,
            patientId = patientId,
            consultationId = consultationId,
            hpr = hpr,
            loincKode = loincKode,
            loincVisningsnavn = loincVisningsnavn,
            verdi = verdi,
            enhetKode = enhetKode,
            enhetVisningsnavn = enhetVisningsnavn,
            effektivTidspunkt = effektivTidspunkt,
            status = status,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `findById returns null when maaling does not exist`() = runTest {
        assertNull(maalingRepository.findById(MaalingId(Uuid.generateV4()), DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `insert stores a complete round trip of a maaling`() = runTest {
        val (patientId, consultationId) = createPatientWithConsultation()
        val maaling = nyMaaling(patientId, consultationId)

        maalingRepository.insert(maaling, DEFAULT_ORG_NUMBER)
        val funnet = maalingRepository.findById(maaling.id, DEFAULT_ORG_NUMBER)

        assertEquals(maaling.id, funnet?.id)
        assertEquals(maaling.patientId, funnet?.patientId)
        assertEquals(maaling.consultationId, funnet?.consultationId)
        assertEquals(maaling.hpr, funnet?.hpr)
        assertEquals(maaling.loincKode, funnet?.loincKode)
        assertEquals(maaling.loincVisningsnavn, funnet?.loincVisningsnavn)
        assertEquals(0, maaling.verdi.compareTo(funnet?.verdi))
        assertEquals(maaling.enhetKode, funnet?.enhetKode)
        assertEquals(maaling.enhetVisningsnavn, funnet?.enhetVisningsnavn)
        assertEquals(maaling.effektivTidspunkt, funnet?.effektivTidspunkt)
        assertEquals(maaling.status, funnet?.status)
    }

    @Test
    fun `insert preserves decimal precision, unit, code and status values`() = runTest {
        val (patientId, consultationId) = createPatientWithConsultation()
        val maaling =
            nyMaaling(
                patientId,
                consultationId,
                loincKode = "2339-0",
                loincVisningsnavn = "Glucose",
                verdi = BigDecimal("5.6789"),
                enhetKode = "mmol/L",
                enhetVisningsnavn = "millimole per liter",
                status = MaalingStatus.ENTERED_IN_ERROR,
            )

        maalingRepository.insert(maaling, DEFAULT_ORG_NUMBER)
        val funnet = maalingRepository.findById(maaling.id, DEFAULT_ORG_NUMBER)

        assertEquals(0, BigDecimal("5.6789").compareTo(funnet?.verdi))
        assertEquals("mmol/L", funnet?.enhetKode)
        assertEquals("millimole per liter", funnet?.enhetVisningsnavn)
        assertEquals("2339-0", funnet?.loincKode)
        assertEquals(MaalingStatus.ENTERED_IN_ERROR, funnet?.status)
    }

    @Test
    fun `insert without a performer stores a null hpr`() = runTest {
        val (patientId, consultationId) = createPatientWithConsultation()
        val maaling = nyMaaling(patientId, consultationId, hpr = null)

        maalingRepository.insert(maaling, DEFAULT_ORG_NUMBER)
        val funnet = maalingRepository.findById(maaling.id, DEFAULT_ORG_NUMBER)

        assertNull(funnet?.hpr)
    }

    @Test
    fun `listByPatientId returns only maalinger for the correct patient ordered by effektivTidspunkt`() =
        runTest {
            val (patientId, consultationId) = createPatientWithConsultation()
            val (otherPatientId, otherConsultationId) = createPatientWithConsultation()

            val eldst =
                nyMaaling(
                    patientId,
                    consultationId,
                    effektivTidspunkt = LocalDateTime.now().minusDays(2),
                )
            val nyest =
                nyMaaling(patientId, consultationId, effektivTidspunkt = LocalDateTime.now())
            val mellom =
                nyMaaling(
                    patientId,
                    consultationId,
                    effektivTidspunkt = LocalDateTime.now().minusDays(1),
                )
            val otherPatientMaaling = nyMaaling(otherPatientId, otherConsultationId)

            listOf(nyest, eldst, mellom, otherPatientMaaling).forEach {
                maalingRepository.insert(it, DEFAULT_ORG_NUMBER)
            }

            val maalinger = maalingRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER)

            assertEquals(listOf(eldst.id, mellom.id, nyest.id), maalinger.map { it.id })
        }

    @Test
    fun `listByConsultationId returns only maalinger for the correct consultation ordered by effektivTidspunkt`() =
        runTest {
            val (patientId, consultationId) = createPatientWithConsultation()
            val otherConsultationId =
                consultationRepository.insert(
                    CreateConsultation(
                        patientId,
                        emptyList(),
                        LocalDateTime.now(),
                        ConsultationStatus.IN_PROGRESS,
                        orgNumber = DEFAULT_ORG_NUMBER,
                    )
                )

            val eldst =
                nyMaaling(
                    patientId,
                    consultationId,
                    effektivTidspunkt = LocalDateTime.now().minusDays(1),
                )
            val nyest =
                nyMaaling(patientId, consultationId, effektivTidspunkt = LocalDateTime.now())
            val otherConsultationMaaling = nyMaaling(patientId, otherConsultationId)

            listOf(nyest, eldst, otherConsultationMaaling).forEach {
                maalingRepository.insert(it, DEFAULT_ORG_NUMBER)
            }

            val maalinger =
                maalingRepository.listByConsultationId(consultationId, DEFAULT_ORG_NUMBER)

            assertEquals(listOf(eldst.id, nyest.id), maalinger.map { it.id })
        }

    @Test
    fun `insert with the same id twice throws DuplikatMaalingException and leaves data unchanged`() =
        runTest {
            val (patientId, consultationId) = createPatientWithConsultation()
            val maaling = nyMaaling(patientId, consultationId, loincKode = "8310-5")

            maalingRepository.insert(maaling, DEFAULT_ORG_NUMBER)

            assertFailsWith<DuplikatMaalingException> {
                maalingRepository.insert(maaling.copy(loincKode = "annen-kode"), DEFAULT_ORG_NUMBER)
            }

            val funnet = maalingRepository.findById(maaling.id, DEFAULT_ORG_NUMBER)
            assertEquals("8310-5", funnet?.loincKode)
        }

    @Test
    fun `insert rejects a maaling whose consultation belongs to another patient`() = runTest {
        val (_, consultationId) = createPatientWithConsultation()
        val (otherPatientId, _) = createPatientWithConsultation()
        val maaling = nyMaaling(otherPatientId, consultationId)

        assertFailsWith<ConsultationBelongsToOtherPatientException> {
            maalingRepository.insert(maaling, DEFAULT_ORG_NUMBER)
        }

        assertNull(maalingRepository.findById(maaling.id, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `maalinger recorded at one org are invisible at another`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val (patientId, consultationId) = createPatientWithConsultation()
        val maaling = nyMaaling(patientId, consultationId)
        maalingRepository.insert(maaling, DEFAULT_ORG_NUMBER)

        assertNull(maalingRepository.findById(maaling.id, OTHER_ORG_NUMBER))
        assertEquals(0, maalingRepository.listByPatientId(patientId, OTHER_ORG_NUMBER).size)
        assertEquals(
            0,
            maalingRepository.listByConsultationId(consultationId, OTHER_ORG_NUMBER).size,
        )
        assertEquals(1, maalingRepository.listByPatientId(patientId, DEFAULT_ORG_NUMBER).size)
        assertEquals(maaling.id, maalingRepository.findById(maaling.id, null)?.id)
    }

    @Test
    fun `insert rejects a maaling for a consultation at another org`() = runTest {
        ensureOrg(OTHER_ORG_NUMBER)
        val (patientId, consultationId) = createPatientWithConsultation()
        val maaling = nyMaaling(patientId, consultationId)

        assertTrue(runCatching { maalingRepository.insert(maaling, OTHER_ORG_NUMBER) }.isFailure)
        assertNull(maalingRepository.findById(maaling.id, null))
    }
}
