package no.nav.helse.epj.maaling

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.epj.consultation.ConsultationId
import no.nav.helse.epj.patient.PatientId
import no.nav.helse.epj.practitioner.PractitionerHpr
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.junit.Test

class MaalingServiceTest {
    private val repository = mockk<MaalingRepository>()
    private val service = MaalingService(repository)

    @OptIn(ExperimentalUuidApi::class)
    private fun request(
        patientId: PatientId = PatientId(Uuid.generateV4()),
        consultationId: ConsultationId = ConsultationId(Uuid.generateV4()),
    ) =
        OpprettMaalingRequest(
            patientId = patientId,
            consultationId = consultationId,
            hpr = PractitionerHpr("123"),
            loincKode = "8310-5",
            loincVisningsnavn = "Body temperature",
            verdi = BigDecimal("37.2000"),
            enhetKode = "Cel",
            enhetVisningsnavn = "degree Celsius",
            effektivTidspunkt = LocalDateTime.now(),
            status = MaalingStatus.FINAL,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `opprettMaaling persists a maaling with a generated id`() = runTest {
        val inserted = slot<Maaling>()
        val opprettRequest = request()
        coEvery { repository.insert(capture(inserted), DEFAULT_ORG_NUMBER) } returns Unit

        val opprettet = service.opprettMaaling(opprettRequest, DEFAULT_ORG_NUMBER)

        assertEquals(opprettRequest.patientId, opprettet.patientId)
        assertEquals(opprettRequest.consultationId, opprettet.consultationId)
        assertEquals(opprettRequest.loincKode, opprettet.loincKode)
        assertEquals(opprettRequest.verdi, opprettet.verdi)
        assertEquals(opprettet, inserted.captured)
        coVerify(exactly = 1) { repository.insert(any(), DEFAULT_ORG_NUMBER) }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMaaling returns the maaling from the repository`() = runTest {
        val id = MaalingId(Uuid.generateV4())
        val opprettRequest = request()
        val maaling =
            Maaling(
                id = id,
                patientId = opprettRequest.patientId,
                consultationId = opprettRequest.consultationId,
                hpr = opprettRequest.hpr,
                loincKode = opprettRequest.loincKode,
                loincVisningsnavn = opprettRequest.loincVisningsnavn,
                verdi = opprettRequest.verdi,
                enhetKode = opprettRequest.enhetKode,
                enhetVisningsnavn = opprettRequest.enhetVisningsnavn,
                effektivTidspunkt = opprettRequest.effektivTidspunkt,
                status = opprettRequest.status,
            )
        coEvery { repository.findById(id, DEFAULT_ORG_NUMBER) } returns maaling

        val resultat = service.getMaaling(id, DEFAULT_ORG_NUMBER)

        assertEquals(maaling, resultat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMaalingerForPatient returns maalinger from the repository`() = runTest {
        val patientId = PatientId(Uuid.generateV4())
        val opprettRequest = request(patientId = patientId)
        val maaling =
            Maaling(
                id = MaalingId(Uuid.generateV4()),
                patientId = patientId,
                consultationId = opprettRequest.consultationId,
                hpr = opprettRequest.hpr,
                loincKode = opprettRequest.loincKode,
                loincVisningsnavn = opprettRequest.loincVisningsnavn,
                verdi = opprettRequest.verdi,
                enhetKode = opprettRequest.enhetKode,
                enhetVisningsnavn = opprettRequest.enhetVisningsnavn,
                effektivTidspunkt = opprettRequest.effektivTidspunkt,
                status = opprettRequest.status,
            )
        coEvery { repository.listByPatientId(patientId, DEFAULT_ORG_NUMBER) } returns
            listOf(maaling)

        val resultat = service.getMaalingerForPatient(patientId, DEFAULT_ORG_NUMBER)

        assertEquals(listOf(maaling), resultat)
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getMaalingerForConsultation returns maalinger from the repository`() = runTest {
        val consultationId = ConsultationId(Uuid.generateV4())
        val opprettRequest = request(consultationId = consultationId)
        val maaling =
            Maaling(
                id = MaalingId(Uuid.generateV4()),
                patientId = opprettRequest.patientId,
                consultationId = consultationId,
                hpr = opprettRequest.hpr,
                loincKode = opprettRequest.loincKode,
                loincVisningsnavn = opprettRequest.loincVisningsnavn,
                verdi = opprettRequest.verdi,
                enhetKode = opprettRequest.enhetKode,
                enhetVisningsnavn = opprettRequest.enhetVisningsnavn,
                effektivTidspunkt = opprettRequest.effektivTidspunkt,
                status = opprettRequest.status,
            )
        coEvery { repository.listByConsultationId(consultationId, DEFAULT_ORG_NUMBER) } returns
            listOf(maaling)

        val resultat = service.getMaalingerForConsultation(consultationId, DEFAULT_ORG_NUMBER)

        assertEquals(listOf(maaling), resultat)
    }
}
