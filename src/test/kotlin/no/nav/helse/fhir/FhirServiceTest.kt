package no.nav.helse.fhir

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Encounter
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import no.nav.helse.epj.api.Konsultasjon
import no.nav.helse.epj.api.KonsultasjonStatus
import no.nav.helse.epj.api.Pasient
import no.nav.helse.epj.db.HelsepersonellRepository
import no.nav.helse.epj.db.KonsultasjonRepository
import no.nav.helse.epj.db.PasientRepository
import org.junit.Ignore
import org.junit.Test

@Ignore("ikke imeplementert")
class FhirServiceTest {

  private val pasientRepository = mockk<PasientRepository>()
  private val konsultasjonRepository = mockk<KonsultasjonRepository>()
  private val helsepersonellRepository = mockk<HelsepersonellRepository>()

  private val fhirService =
    FhirService(
      pasientRepository,
      helsepersonellRepository,
      konsultasjonRepository,
    )

  private val pasient =
    Pasient(
      id = "pasient-1",
      legekontorId = "legekontor-1",
      fastlegeId = "fastlege-1",
      navn = "Kari Nordmann",
      fnr = "123",
    )

  private val konsultasjon =
    Konsultasjon(
      id = "konsultasjon-1",
      pasientId = "pasient-1",
      hpr = listOf("111"),
      startetTidspunkt = LocalDateTime.parse("2021-01-01T12:00:00"),
      avsluttetTidspunkt = LocalDateTime.parse("2021-01-01T13:00:00"),
      status = KonsultasjonStatus.PÅGÅENDE,
      problemstilling = null,
      journalnotat = null,
    )

  @Test
  fun `should return null when patient not found`() = runTest {
    coEvery { pasientRepository.getPasient("missing-id") } returns null
    val result = fhirService.getPatient("missing-id")
    result.shouldBeNull()
  }

  @Test
  fun `should map pasient to fhir patient`() = runTest {
    coEvery { pasientRepository.getPasient(pasient.id) } returns pasient

    val fhirPatient = fhirService.getPatient(pasient.id)
    fhirPatient.shouldNotBeNull()
    fhirPatient.id shouldBe pasient.id
    fhirPatient.meta?.profile?.first()?.value shouldBe
      "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"
    fhirPatient.identifier.first().system?.value shouldBe "urn:oid:2.16.578.1.12.4.1.4.1"
    fhirPatient.identifier.first().value?.value shouldBe "fødselsnummer"
    fhirPatient.name.first().family?.value shouldBe pasient.navn
    fhirPatient.name.first().given.first().value shouldBe pasient.navn
  }

  @Test
  fun `should return null when konsultasjon not found`() = runTest {
    coEvery { konsultasjonRepository.getKonsultasjon("not-found-id") } returns null

    val encounter = fhirService.getEncounter("not-found-id", "not-found-authorized-patient-id")
    encounter.shouldBeNull()
  }

  @Test
  fun `should return null when encounter belongs to a different patient`() = runTest {
    coEvery { konsultasjonRepository.getKonsultasjon("konsultasjon-1") } returns konsultasjon

    val encounter = fhirService.getEncounter("konsultasjon-1", "someone-else")
    encounter.shouldBeNull()
  }

  @Test
  fun `should map konsultasjon to encounter for authorized patient`() = runTest {
    coEvery { konsultasjonRepository.getKonsultasjon("konsultasjon-1") } returns konsultasjon

    val encounter = fhirService.getEncounter("konsultasjon-1", "pasient-1")
    encounter.shouldNotBeNull()
    encounter.id shouldBe konsultasjon.id
    encounter.status.value shouldBe Encounter.EncounterStatus.In_Progress
    encounter.`class`.code?.value shouldBe "AMB"
    encounter.subject?.reference?.value shouldBe "Patient/pasient-1"
  }

  @Test
  fun `should map pågående status to in-progress encounter status`() = runTest {
    coEvery { konsultasjonRepository.getKonsultasjon("konsultasjon-1") } returns konsultasjon

    val encounter = fhirService.getEncounter("konsultasjon-1", "pasient-1")
    encounter.shouldNotBeNull()
    encounter.status.value shouldBe Encounter.EncounterStatus.In_Progress
  }

  @Test
  fun `should map avsluttet status to finished encounter status`() = runTest {
    coEvery { konsultasjonRepository.getKonsultasjon("konsultasjon-1") } returns
      konsultasjon.copy(status = KonsultasjonStatus.FULLFØRT)

    val encounter = fhirService.getEncounter("konsultasjon-1", "pasient-1")
    encounter.shouldNotBeNull()
    encounter.status.value shouldBe Encounter.EncounterStatus.Finished
  }

  @Test
  fun `should return null active encounter when patient has none`() = runTest {
    coEvery { konsultasjonRepository.getAktivKonsultasjon(pasient.id) } returns null

    val encounter = fhirService.getActiveEncounterForPatient(pasient.id)
    encounter.shouldBeNull()
  }

  @Test
  fun `should return active encounter for patient`() = runTest {
    coEvery { konsultasjonRepository.getAktivKonsultasjon(pasient.id) } returns konsultasjon

    val encounter = fhirService.getActiveEncounterForPatient(pasient.id)
    encounter.shouldNotBeNull()
    encounter.id shouldBe konsultasjon.id
    encounter.subject?.reference?.value shouldBe "Patient/pasient-1"
  }

  @Test
  fun `should return empty bundle when patient has no active encounter`() = runTest {
    coEvery { konsultasjonRepository.getKonsultasjoner(pasient.id) } returns emptyList()

    val bundle = fhirService.searchEncounters(pasient.id)
    bundle.entry.size shouldBe 0
  }

  @Test
  fun `should return bundle containing active encounter when one exists`() = runTest {
    coEvery { konsultasjonRepository.getKonsultasjoner(pasient.id) } returns
      listOf(
        konsultasjon.copy(
          id = "konsultasjon-1",
          status = KonsultasjonStatus.PÅGÅENDE,
          avsluttetTidspunkt = null,
        ),
        konsultasjon.copy(
          id = "konsultasjon-2",
          status = KonsultasjonStatus.FULLFØRT,
          startetTidspunkt = LocalDateTime.parse("2020-06-01T12:00"),
          avsluttetTidspunkt = LocalDateTime.parse("2020-06-01T13:00"),
        ),
        konsultasjon.copy(
          id = "konsultasjon-3",
          status = KonsultasjonStatus.FULLFØRT,
          startetTidspunkt = LocalDateTime.parse("2020-01-01T12:00"),
          avsluttetTidspunkt = LocalDateTime.parse("2020-01-01T13:00"),
        ),
      )

    val bundle = fhirService.searchEncounters(pasient.id)
    bundle.shouldNotBeNull()
    bundle.total?.value shouldBe 3
    bundle.type.value shouldBe Bundle.BundleType.Searchset
    bundle.entry.first().resource?.id shouldBe "konsultasjon-1"
  }
}
