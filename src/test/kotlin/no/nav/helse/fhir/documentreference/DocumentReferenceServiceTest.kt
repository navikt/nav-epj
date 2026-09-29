package no.nav.helse.fhir.documentreference

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.utils.KonsultasjonNotFoundException
import no.nav.helse.epj.helsepersonell.HelsepersonellHpr
import no.nav.helse.epj.helsepersonell.HelsepersonellService
import no.nav.helse.epj.konsultasjon.Journalnotat
import no.nav.helse.epj.konsultasjon.JournalnotatId
import no.nav.helse.epj.konsultasjon.KonsultasjonId
import no.nav.helse.epj.konsultasjon.KonsultasjonService
import no.nav.helse.epj.konsultasjon.OpprettJournalnotatRequest
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.fhir.encounter.EncounterId
import no.nav.helse.fhir.patient.PatientInputId
import org.junit.Test

class DocumentReferenceServiceTest {

    private val konsultasjonService = mockk<KonsultasjonService>()
    private val helsepersonellService = mockk<HelsepersonellService>()
    private val documentReferenceService =
        DocumentReferenceService(konsultasjonService, helsepersonellService)

    @OptIn(ExperimentalUuidApi::class)
    private fun journalnotat(
        id: JournalnotatId = JournalnotatId(Uuid.generateV4()),
        pasientId: PasientId = PasientId(Uuid.generateV4()),
        konsultasjonId: KonsultasjonId = KonsultasjonId(Uuid.generateV4()),
        journalnotat: String? = "notat",
    ) =
        Journalnotat(
            id = id,
            konsultasjonId = konsultasjonId,
            pasientId = pasientId,
            journalnotat = journalnotat,
        )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createDocumentReference persists through KonsultasjonService and maps the server-generated id back`() =
        runTest {
            val request =
                OpprettJournalnotatRequest(
                    pasientId = PasientId(Uuid.generateV4()),
                    konsultasjonId = KonsultasjonId(Uuid.generateV4()),
                    journalnotat = "notat",
                )
            val persisted =
                journalnotat(
                    pasientId = request.pasientId,
                    konsultasjonId = request.konsultasjonId,
                    journalnotat = request.journalnotat,
                )
            coEvery { konsultasjonService.opprettJournalnotat(request) } returns persisted
            coEvery { helsepersonellService.getHelsepersonell(persisted.pasientId) } returns
                emptyList()

            val documentReference = documentReferenceService.createDocumentReference(request)

            assertEquals(persisted.id.value.toString(), documentReference.id)
            assertEquals(
                "Patient/${request.pasientId.value}",
                documentReference.subject?.reference?.value,
            )
            assertEquals(
                "Encounter/${request.konsultasjonId.value}",
                documentReference.context?.encounter?.single()?.reference?.value,
            )
            assertEquals("notat", documentReference.description?.value)
            coVerify(exactly = 1) { konsultasjonService.opprettJournalnotat(request) }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createDocumentReference propagates encounter-not-found without swallowing the error`() =
        runTest {
            val request =
                OpprettJournalnotatRequest(
                    pasientId = PasientId(Uuid.generateV4()),
                    konsultasjonId = KonsultasjonId(Uuid.generateV4()),
                    journalnotat = "notat",
                )
            coEvery { konsultasjonService.opprettJournalnotat(request) } throws
                KonsultasjonNotFoundException(request.konsultasjonId)

            assertFailsWith<KonsultasjonNotFoundException> {
                documentReferenceService.createDocumentReference(request)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `createDocumentReference maps author from the helsepersonell tied to the patient`() =
        runTest {
            val request =
                OpprettJournalnotatRequest(
                    pasientId = PasientId(Uuid.generateV4()),
                    konsultasjonId = KonsultasjonId(Uuid.generateV4()),
                    journalnotat = "notat",
                )
            val persisted =
                journalnotat(pasientId = request.pasientId, konsultasjonId = request.konsultasjonId)
            coEvery { konsultasjonService.opprettJournalnotat(request) } returns persisted
            coEvery { helsepersonellService.getHelsepersonell(persisted.pasientId) } returns
                listOf(HelsepersonellHpr("999"))

            val documentReference = documentReferenceService.createDocumentReference(request)

            assertEquals("Practitioner/999", documentReference.author.single().reference?.value)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `getDocumentReferences returns null when the journalnotat does not exist`() = runTest {
        val id = DocumentReferenceId(Uuid.generateV4())
        coEvery { konsultasjonService.getJournalnotat(JournalnotatId(id.value)) } returns null

        assertNull(documentReferenceService.getDocumentReferences(id))
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchDocumentReferences returns an empty searchset bundle when the patient has no notater`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            coEvery { konsultasjonService.getJournalnotater(pasientId, null) } returns emptyList()
            coEvery { helsepersonellService.getHelsepersonell(pasientId) } returns emptyList()

            val bundle =
                documentReferenceService.searchDocumentReferences(
                    patientId = PatientInputId(pasientId.value),
                    encounterId = null,
                )

            assertEquals(com.google.fhir.model.r4.Bundle.BundleType.Searchset, bundle.type.value)
            assertTrue(bundle.entry.isEmpty())
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchDocumentReferences returns every notat for the patient without an encounter filter`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            val forste = journalnotat(pasientId = pasientId)
            val andre = journalnotat(pasientId = pasientId)
            coEvery { konsultasjonService.getJournalnotater(pasientId, null) } returns
                listOf(forste, andre)
            coEvery { helsepersonellService.getHelsepersonell(pasientId) } returns emptyList()

            val bundle =
                documentReferenceService.searchDocumentReferences(
                    patientId = PatientInputId(pasientId.value),
                    encounterId = null,
                )

            assertEquals(
                setOf(forste.id.value.toString(), andre.id.value.toString()),
                bundle.entry
                    .map { (it.resource as com.google.fhir.model.r4.DocumentReference).id }
                    .toSet(),
            )
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `searchDocumentReferences passes the encounter filter through to KonsultasjonService`() =
        runTest {
            val pasientId = PasientId(Uuid.generateV4())
            val konsultasjonId = KonsultasjonId(Uuid.generateV4())
            val notat = journalnotat(pasientId = pasientId, konsultasjonId = konsultasjonId)
            coEvery { konsultasjonService.getJournalnotater(pasientId, konsultasjonId) } returns
                listOf(notat)
            coEvery { helsepersonellService.getHelsepersonell(pasientId) } returns emptyList()

            val bundle =
                documentReferenceService.searchDocumentReferences(
                    patientId = PatientInputId(pasientId.value),
                    encounterId = EncounterId(konsultasjonId.value),
                )

            assertEquals(
                notat.id.value.toString(),
                (bundle.entry.single().resource as com.google.fhir.model.r4.DocumentReference).id,
            )
            coVerify(exactly = 1) {
                konsultasjonService.getJournalnotater(pasientId, konsultasjonId)
            }
        }
}
