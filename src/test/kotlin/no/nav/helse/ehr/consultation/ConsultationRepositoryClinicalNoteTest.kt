@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.ehr.consultation

import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import no.nav.helse.core.db.ClinicalNoteAttachmentTable
import no.nav.helse.core.db.dbQuery
import no.nav.helse.core.utils.AuthorNotOnConsultationException
import no.nav.helse.core.utils.ClinicalDocumentConflictException
import no.nav.helse.core.utils.ClinicalDocumentWriteNotAllowedException
import no.nav.helse.core.utils.ConsultationBelongsToOtherPatientException
import no.nav.helse.core.utils.ConsultationNotFoundException
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseRepository
import no.nav.helse.fhir.questionnaireresponse.QuestionnaireResponseWrite
import no.nav.helse.utils.DEFAULT_ORG_NUMBER
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.Test

class ConsultationRepositoryClinicalNoteTest : ConsultationRepositoryTestBase() {
    private val questionnaireResponseRepository = QuestionnaireResponseRepository()

    private fun pdf(vararg extra: Byte) = "%PDF-1.4".toByteArray() + extra

    private fun attachment(title: String? = "sykmelding.pdf", data: ByteArray = pdf()) =
        NewAttachment(title = title, language = "no-NO", data = data)

    private suspend fun backdateAttachment(id: ClinicalNoteId, days: Long) = dbQuery {
        ClinicalNoteAttachmentTable.update({
            ClinicalNoteAttachmentTable.clinicalNoteId eq id.value
        }) {
            it[createdAt] = Instant.now().minusSeconds(days * 24 * 3600)
        }
    }

    private suspend fun storedData(id: ClinicalNoteId): ByteArray? = dbQuery {
        ClinicalNoteAttachmentTable.selectAll()
            .where { ClinicalNoteAttachmentTable.clinicalNoteId eq id.value }
            .single()[ClinicalNoteAttachmentTable.data]
    }

    private suspend fun saveQuestionnaireResponse(
        patientId: no.nav.helse.ehr.patient.PatientId,
        consultationId: ConsultationId,
    ): Uuid {
        val id = Uuid.generateV4()
        questionnaireResponseRepository.write(
            id,
            QuestionnaireResponseWrite(patientId, consultationId, emptyList(), "{}"),
            DEFAULT_ORG_NUMBER,
            allowCreate = true,
            allowUpdate = true,
        )
        return id
    }

    @Test
    fun `write stores the ADR metadata and the attachment as a FHIR note`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val questionnaireResponseId = Uuid.generateV4()
        val id = ClinicalNoteId(Uuid.generateV4())

        val result =
            clinicalDocumentRepository.write(
                id,
                ClinicalDocumentWrite(
                    patientId,
                    consultationId,
                    "beskrivelse",
                    "Sykmelding",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 1, 10),
                    questionnaireResponseId,
                    listOf("123"),
                    attachment(data = pdf(1, 2, 3)),
                ),
                DEFAULT_ORG_NUMBER,
                allowCreate = true,
                allowUpdate = false,
            )

        assertTrue(result.created)
        val stored = assertNotNull(clinicalDocumentRepository.find(id, DEFAULT_ORG_NUMBER))
        assertEquals(ClinicalNoteSource.FHIR, stored.note.source)
        assertEquals("beskrivelse", stored.note.clinicalNote)
        assertEquals("Sykmelding", stored.typeText)
        assertEquals(LocalDate.of(2025, 1, 10), stored.periodEnd)
        assertEquals(questionnaireResponseId, stored.relatedQuestionnaireResponseId)
        assertEquals("sykmelding.pdf", stored.attachment?.title)
        assertEquals("no-NO", stored.attachment?.language)
        assertContentEquals(pdf(1, 2, 3), stored.attachment?.data)
    }

    @Test
    fun `a clinician save never overwrites a text-only FHIR note`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val fhirId = writeFhirNote(patientId, consultationId, "fra app")

        consultationRepository.insertClinicalNote(
            ClinicalNote(fhirId, consultationId, patientId, "overskrevet"),
            DEFAULT_ORG_NUMBER,
        )
        consultationRepository.updateClinicalNote(consultationId, patientId, "klinikerens notat")

        val fhirNote =
            assertNotNull(consultationRepository.findClinicalNote(fhirId, DEFAULT_ORG_NUMBER))
        assertEquals("fra app", fhirNote.clinicalNote)
        assertEquals(ClinicalNoteSource.FHIR, fhirNote.source)
        val notes =
            consultationRepository
                .findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)!!
                .clinicalNotes
        assertEquals(
            setOf(
                ClinicalNoteSource.FHIR to "fra app",
                ClinicalNoteSource.EHR to "klinikerens notat",
            ),
            notes.map { it.source to it.clinicalNote }.toSet(),
        )
    }

    @Test
    fun `the clinician note keeps being updated in place next to FHIR notes`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        writeFhirNote(patientId, consultationId, "fra app")

        consultationRepository.updateClinicalNote(consultationId, patientId, "første")
        consultationRepository.updateClinicalNote(consultationId, patientId, "andre")

        val ehrNotes =
            consultationRepository
                .findByConsultationId(consultationId, DEFAULT_ORG_NUMBER)!!
                .clinicalNotes
                .filter { it.source == ClinicalNoteSource.EHR }
        assertEquals(listOf("andre"), ehrNotes.map { it.clinicalNote })
    }

    @Test
    fun `FHIR writes cannot overwrite or delete an EHR note`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val id = ClinicalNoteId(Uuid.generateV4())
        consultationRepository.insertClinicalNote(
            ClinicalNote(id, consultationId, patientId, "journal"),
            DEFAULT_ORG_NUMBER,
        )

        assertFailsWith<ClinicalDocumentConflictException> {
            writeFhirNote(patientId, consultationId, "overskrevet", id)
        }
        assertEquals(
            ClinicalNoteDeleteResult.PROTECTED,
            clinicalDocumentRepository.deleteFhirDocument(id, patientId, DEFAULT_ORG_NUMBER),
        )
        assertEquals(
            ClinicalNoteDeleteResult.PROTECTED,
            clinicalDocumentRepository.deleteFhirDocumentWithQuestionnaireResponse(
                id,
                DEFAULT_ORG_NUMBER,
            ),
        )
        assertEquals(
            "journal",
            consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)?.clinicalNote,
        )
    }

    @Test
    fun `an existing FHIR note cannot be moved to another patient or encounter`() = runTest {
        val owner = createPatient()
        val ownerConsultation = createConsultation(owner)
        val other = createPatient()
        val otherConsultation = createConsultation(other)
        val id = writeFhirNote(owner, ownerConsultation, "original")

        assertFailsWith<ConsultationBelongsToOtherPatientException> {
            writeFhirNote(other, ownerConsultation, "flyttet", id)
        }
        assertFailsWith<ClinicalDocumentConflictException> {
            writeFhirNote(other, otherConsultation, "flyttet", id)
        }
        assertFailsWith<ClinicalDocumentConflictException> {
            writeFhirNote(owner, createConsultation(owner), "flyttet", id)
        }

        val stored = assertNotNull(clinicalDocumentRepository.find(id, DEFAULT_ORG_NUMBER)).note
        assertEquals("original", stored.clinicalNote)
        assertEquals(ownerConsultation, stored.consultationId)
    }

    @Test
    fun `write checks that the author is on the consultation and that create and update are allowed`() =
        runTest {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)
            val unknownAuthor =
                ClinicalDocumentWrite(
                    patientId,
                    consultationId,
                    "n",
                    null,
                    null,
                    null,
                    null,
                    listOf("999"),
                    null,
                )

            assertFailsWith<AuthorNotOnConsultationException> {
                clinicalDocumentRepository.write(
                    ClinicalNoteId(Uuid.generateV4()),
                    unknownAuthor,
                    DEFAULT_ORG_NUMBER,
                    allowCreate = true,
                    allowUpdate = true,
                )
            }
            assertFailsWith<ClinicalDocumentWriteNotAllowedException> {
                clinicalDocumentRepository.write(
                    ClinicalNoteId(Uuid.generateV4()),
                    unknownAuthor,
                    DEFAULT_ORG_NUMBER,
                    allowCreate = false,
                    allowUpdate = true,
                )
            }
            val id = writeFhirNote(patientId, consultationId, "finnes")
            assertFailsWith<ClinicalDocumentWriteNotAllowedException> {
                clinicalDocumentRepository.write(
                    id,
                    unknownAuthor.copyWithAuthors(emptyList()),
                    DEFAULT_ORG_NUMBER,
                    allowCreate = true,
                    allowUpdate = false,
                )
            }
            assertFailsWith<ConsultationNotFoundException> {
                writeFhirNote(patientId, ConsultationId(Uuid.generateV4()), "ukjent")
            }
        }

    @Test
    fun `an update without a PDF removes the stored attachment`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val id = writeFhirNote(patientId, consultationId, "med pdf", attachment = attachment())

        writeFhirNote(patientId, consultationId, "uten pdf", id)

        val note = assertNotNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
        assertFalse(note.hasAttachment)
        assertNull(clinicalDocumentRepository.findAttachment(id, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `expired attachments are hidden on read before any purge but metadata remains`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val fresh =
            writeFhirNote(patientId, consultationId, "ny", attachment = attachment("ny.pdf"))
        val old =
            writeFhirNote(
                patientId,
                consultationId,
                "gammel",
                attachment = attachment("gammel.pdf"),
            )
        backdateAttachment(old, 14)

        val oldNote =
            assertNotNull(consultationRepository.findClinicalNote(old, DEFAULT_ORG_NUMBER))
        assertTrue(oldNote.hasAttachment)
        assertTrue(oldNote.attachmentExpired)
        assertEquals("gammel.pdf", oldNote.attachmentTitle)
        val freshNote =
            assertNotNull(consultationRepository.findClinicalNote(fresh, DEFAULT_ORG_NUMBER))
        assertTrue(freshNote.hasAttachment)
        assertFalse(freshNote.attachmentExpired)

        assertNull(clinicalDocumentRepository.findAttachment(old, DEFAULT_ORG_NUMBER))
        assertNotNull(clinicalDocumentRepository.findAttachment(fresh, DEFAULT_ORG_NUMBER))
        assertNull(clinicalDocumentRepository.find(old, DEFAULT_ORG_NUMBER)?.attachment?.data)
        assertNotNull(clinicalDocumentRepository.find(fresh, DEFAULT_ORG_NUMBER)?.attachment?.data)
        val listed =
            clinicalDocumentRepository.list(patientId, null, null, DEFAULT_ORG_NUMBER).associateBy {
                it.note.id
            }
        assertTrue(listed.getValue(old).attachment!!.expired)
        assertNull(listed.getValue(old).attachment!!.data)
        assertNotNull(listed.getValue(fresh).attachment!!.data)
        assertNotNull(storedData(old))
    }

    @Test
    fun `a PDF write purges expired data in the same transaction and keeps the metadata`() =
        runTest {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)
            val old =
                writeFhirNote(
                    patientId,
                    consultationId,
                    "gammel",
                    attachment = attachment("gammel.pdf"),
                )
            backdateAttachment(old, 15)

            val fresh =
                writeFhirNote(patientId, consultationId, "ny", attachment = attachment("ny.pdf"))

            assertNull(storedData(old))
            assertNotNull(storedData(fresh))
            val oldNote =
                assertNotNull(consultationRepository.findClinicalNote(old, DEFAULT_ORG_NUMBER))
            assertTrue(oldNote.hasAttachment)
            assertTrue(oldNote.attachmentExpired)
            assertEquals("gammel", oldNote.clinicalNote)
        }

    @Test
    fun `a text-only write does not purge`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val old = writeFhirNote(patientId, consultationId, "gammel", attachment = attachment())
        backdateAttachment(old, 15)

        writeFhirNote(patientId, consultationId, "bare tekst")

        assertNotNull(storedData(old))
    }

    @Test
    fun `FHIR delete removes the note and attachment but keeps the questionnaire response`() =
        runTest {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)
            val questionnaireResponseId = saveQuestionnaireResponse(patientId, consultationId)
            val id =
                writeFhirNote(
                    patientId,
                    consultationId,
                    "n",
                    attachment = attachment(),
                    relatedQuestionnaireResponseId = questionnaireResponseId,
                )

            assertEquals(
                ClinicalNoteDeleteResult.DELETED,
                clinicalDocumentRepository.deleteFhirDocument(id, patientId, DEFAULT_ORG_NUMBER),
            )

            assertNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
            assertNotNull(
                questionnaireResponseRepository.find(questionnaireResponseId, DEFAULT_ORG_NUMBER)
            )
            assertEquals(
                ClinicalNoteDeleteResult.NOT_FOUND,
                clinicalDocumentRepository.deleteFhirDocument(id, patientId, DEFAULT_ORG_NUMBER),
            )
        }

    @Test
    fun `FHIR delete for another patient finds nothing`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val id = writeFhirNote(patientId, consultationId, "n")

        assertEquals(
            ClinicalNoteDeleteResult.NOT_FOUND,
            clinicalDocumentRepository.deleteFhirDocument(id, createPatient(), DEFAULT_ORG_NUMBER),
        )
        assertNotNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `UI delete removes the note, the attachment and the linked questionnaire response`() =
        runTest {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)
            val questionnaireResponseId = saveQuestionnaireResponse(patientId, consultationId)
            val id =
                writeFhirNote(
                    patientId,
                    consultationId,
                    "n",
                    attachment = attachment(),
                    relatedQuestionnaireResponseId = questionnaireResponseId,
                )

            assertEquals(
                ClinicalNoteDeleteResult.DELETED,
                clinicalDocumentRepository.deleteFhirDocumentWithQuestionnaireResponse(
                    id,
                    DEFAULT_ORG_NUMBER,
                ),
            )

            assertNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
            assertNull(
                questionnaireResponseRepository.find(questionnaireResponseId, DEFAULT_ORG_NUMBER)
            )
            assertEquals(
                0,
                dbQuery {
                    ClinicalNoteAttachmentTable.selectAll()
                        .where { ClinicalNoteAttachmentTable.clinicalNoteId eq id.value }
                        .count()
                },
            )
        }

    @Test
    fun `UI delete keeps a linked questionnaire response of another encounter`() = runTest {
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val otherConsultationId = createConsultation(patientId)
        val foreignQuestionnaireResponse = saveQuestionnaireResponse(patientId, otherConsultationId)
        val id =
            writeFhirNote(
                patientId,
                consultationId,
                "n",
                relatedQuestionnaireResponseId = foreignQuestionnaireResponse,
            )

        clinicalDocumentRepository.deleteFhirDocumentWithQuestionnaireResponse(
            id,
            DEFAULT_ORG_NUMBER,
        )

        assertNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
        assertNotNull(
            questionnaireResponseRepository.find(foreignQuestionnaireResponse, DEFAULT_ORG_NUMBER)
        )
    }

    @Test
    fun `UI delete at another org finds nothing`() = runTest {
        no.nav.helse.utils.ensureOrg(no.nav.helse.utils.OTHER_ORG_NUMBER)
        val patientId = createPatient()
        val consultationId = createConsultation(patientId)
        val id = writeFhirNote(patientId, consultationId, "n")

        assertEquals(
            ClinicalNoteDeleteResult.NOT_FOUND,
            clinicalDocumentRepository.deleteFhirDocumentWithQuestionnaireResponse(
                id,
                no.nav.helse.utils.OTHER_ORG_NUMBER,
            ),
        )
        assertNotNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `insertClinicalNote rejects an encounter that belongs to another patient`() = runTest {
        val owner = createPatient()
        val consultationId = createConsultation(owner)
        val otherPatient = createPatient()
        val id = ClinicalNoteId(Uuid.generateV4())

        assertFailsWith<ConsultationBelongsToOtherPatientException> {
            consultationRepository.insertClinicalNote(
                ClinicalNote(id, consultationId, otherPatient, "notat"),
                DEFAULT_ORG_NUMBER,
            )
        }
        assertNull(consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER))
    }

    @Test
    fun `insertClinicalNote does not move an existing clinical note to another consultation`() =
        runTest {
            val owner = createPatient()
            val ownerConsultation = createConsultation(owner)
            val id = ClinicalNoteId(Uuid.generateV4())
            consultationRepository.insertClinicalNote(
                ClinicalNote(id, ownerConsultation, owner, "original"),
                DEFAULT_ORG_NUMBER,
            )
            val attacker = createPatient()
            val attackerConsultation = createConsultation(attacker)

            val rows =
                consultationRepository.insertClinicalNote(
                    ClinicalNote(id, attackerConsultation, attacker, "overskrevet"),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(0, rows)
            val saved = consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)
            assertEquals("original", saved?.clinicalNote)
            assertEquals(owner, saved?.patientId)
            assertEquals(ownerConsultation, saved?.consultationId)
        }

    @Test
    fun `insertClinicalNote updates the text of an existing EHR note in the same consultation`() =
        runTest {
            val patientId = createPatient()
            val consultationId = createConsultation(patientId)
            val id = ClinicalNoteId(Uuid.generateV4())
            consultationRepository.insertClinicalNote(
                ClinicalNote(id, consultationId, patientId, "første"),
                DEFAULT_ORG_NUMBER,
            )

            val rows =
                consultationRepository.insertClinicalNote(
                    ClinicalNote(id, consultationId, patientId, "andre"),
                    DEFAULT_ORG_NUMBER,
                )

            assertEquals(1, rows)
            val saved = consultationRepository.findClinicalNote(id, DEFAULT_ORG_NUMBER)
            assertEquals("andre", saved?.clinicalNote)
            assertEquals(ClinicalNoteSource.EHR, saved?.source)
        }

    private fun ClinicalDocumentWrite.copyWithAuthors(authors: List<String>) =
        ClinicalDocumentWrite(
            patientId,
            consultationId,
            description,
            typeText,
            periodStart,
            periodEnd,
            relatedQuestionnaireResponseId,
            authors,
            attachment,
        )
}
