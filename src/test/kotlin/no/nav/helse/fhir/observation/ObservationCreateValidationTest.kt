@file:OptIn(ExperimentalUuidApi::class)

package no.nav.helse.fhir.observation

import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.DateTime
import com.google.fhir.model.r4.Decimal
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDateTime
import com.google.fhir.model.r4.Observation
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Period
import com.google.fhir.model.r4.Quantity
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.String as FhirString
import com.google.fhir.model.r4.Uri
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.Test

class ObservationCreateValidationTest {

    @OptIn(ExperimentalUuidApi::class)
    private fun validObservation(
        id: String? = null,
        status: Observation.ObservationStatus? = Observation.ObservationStatus.Final,
        coding: List<Coding> =
            listOf(
                Coding(
                    system = Uri(value = "http://loinc.org"),
                    code = Code(value = "8310-5"),
                    display = FhirString(value = "Body temperature"),
                )
            ),
        subjectReference: String? = "Patient/${Uuid.generateV4()}",
        encounterReference: String? = "Encounter/${Uuid.generateV4()}",
        performer: List<Reference> = emptyList(),
        effective: Observation.Effective? =
            Observation.Effective.DateTime(
                DateTime(
                    value =
                        FhirDateTime.DateTime(
                            LocalDateTime.of(2025, 1, 15, 10, 30).toKotlinLocalDateTime(),
                            UtcOffset.ZERO,
                        )
                )
            ),
        value: Observation.Value? =
            Observation.Value.Quantity(
                Quantity(
                    value =
                        Decimal(
                            value =
                                com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString("37.2000")
                        ),
                    unit = FhirString(value = "degree Celsius"),
                    system = Uri(value = "http://unitsofmeasure.org"),
                    code = Code(value = "Cel"),
                )
            ),
        component: List<Observation.Component> = emptyList(),
        dataAbsentReason: CodeableConcept? = null,
    ): Observation =
        Observation(
            id = id,
            status = Enumeration(value = status),
            code = CodeableConcept(coding = coding),
            subject = subjectReference?.let { Reference(reference = FhirString(value = it)) },
            encounter = encounterReference?.let { Reference(reference = FhirString(value = it)) },
            performer = performer,
            effective = effective,
            value = value,
            component = component,
            dataAbsentReason = dataAbsentReason,
        )

    @Test
    fun `toCreateMeasurementRequest maps a fully valid Observation`() {
        val patientId = Uuid.generateV4()
        val encounterId = Uuid.generateV4()
        val observation =
            validObservation(
                subjectReference = "Patient/$patientId",
                encounterReference = "Encounter/$encounterId",
                performer = listOf(Reference(reference = FhirString(value = "Practitioner/999"))),
            )

        val request = observation.toCreateMeasurementRequest()

        assertEquals(patientId, request.patientId.value)
        assertEquals(encounterId, request.consultationId.value)
        assertEquals("999", request.hpr?.value)
        assertEquals("8310-5", request.loincCode)
        assertEquals("Body temperature", request.loincDisplay)
        assertEquals(0, request.value.compareTo(BigDecimal("37.2000")))
        assertEquals("Cel", request.unitCode)
        assertEquals("degree Celsius", request.unitDisplay)
        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 30), request.effectiveAt)
    }

    @Test
    fun `toCreateMeasurementRequest ignores a client-supplied id`() {
        val observation = validObservation(id = "client-chosen-id")

        observation.toCreateMeasurementRequest()
    }

    @Test
    fun `toCreateMeasurementRequest rejects a missing status`() {
        val observation = validObservation(status = null)

        val exception =
            assertFailsWith<InvalidObservationException> {
                observation.toCreateMeasurementRequest()
            }
        assertEquals(OperationOutcome.IssueType.Code_Invalid, exception.issueType)
    }

    @Test
    fun `toCreateMeasurementRequest rejects zero codings`() {
        val observation = validObservation(coding = emptyList())

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects multiple codings`() {
        val observation =
            validObservation(
                coding =
                    listOf(
                        Coding(
                            system = Uri(value = "http://loinc.org"),
                            code = Code(value = "8310-5"),
                            display = FhirString(value = "Body temperature"),
                        ),
                        Coding(
                            system = Uri(value = "http://loinc.org"),
                            code = Code(value = "8331-1"),
                            display = FhirString(value = "Oral temperature"),
                        ),
                    )
            )

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a non-LOINC coding system`() {
        val observation =
            validObservation(
                coding =
                    listOf(
                        Coding(
                            system = Uri(value = "http://snomed.info/sct"),
                            code = Code(value = "1234"),
                            display = FhirString(value = "Something"),
                        )
                    )
            )

        val exception =
            assertFailsWith<InvalidObservationException> {
                observation.toCreateMeasurementRequest()
            }
        assertEquals(OperationOutcome.IssueType.Value, exception.issueType)
    }

    @Test
    fun `toCreateMeasurementRequest rejects a missing subject`() {
        val observation = validObservation(subjectReference = null)

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a subject reference to the wrong resource type`() {
        val observation = validObservation(subjectReference = "Group/${Uuid.generateV4()}")

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a subject reference with a malformed UUID`() {
        val observation = validObservation(subjectReference = "Patient/not-a-uuid")

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a missing encounter`() {
        val observation = validObservation(encounterReference = null)

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects more than one performer`() {
        val observation =
            validObservation(
                performer =
                    listOf(
                        Reference(reference = FhirString(value = "Practitioner/111")),
                        Reference(reference = FhirString(value = "Practitioner/222")),
                    )
            )

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a performer reference that is not a Practitioner`() {
        val observation =
            validObservation(
                performer = listOf(Reference(reference = FhirString(value = "Organization/1")))
            )

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a missing effective`() {
        val observation = validObservation(effective = null)

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects an effective Period instead of dateTime`() {
        val observation =
            validObservation(
                effective =
                    Observation.Effective.Period(
                        Period(
                            start =
                                DateTime(
                                    value =
                                        FhirDateTime.DateTime(
                                            LocalDateTime.of(2025, 1, 15, 10, 30)
                                                .toKotlinLocalDateTime(),
                                            UtcOffset.ZERO,
                                        )
                                )
                        )
                    )
            )

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a date-only effectiveDateTime`() {
        val observation =
            validObservation(
                effective =
                    Observation.Effective.DateTime(
                        DateTime(value = FhirDateTime.Date(kotlinx.datetime.LocalDate(2025, 1, 15)))
                    )
            )

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a non-UTC effectiveDateTime offset`() {
        val observation =
            validObservation(
                effective =
                    Observation.Effective.DateTime(
                        DateTime(
                            value =
                                FhirDateTime.DateTime(
                                    LocalDateTime.of(2025, 1, 15, 10, 30).toKotlinLocalDateTime(),
                                    UtcOffset(hours = 1),
                                )
                        )
                    )
            )

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a valueString instead of valueQuantity`() {
        val observation =
            validObservation(value = Observation.Value.String(FhirString(value = "37,2")))

        val exception =
            assertFailsWith<InvalidObservationException> {
                observation.toCreateMeasurementRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    @Test
    fun `toCreateMeasurementRequest rejects a missing value`() {
        val observation = validObservation(value = null)

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    @Test
    fun `toCreateMeasurementRequest rejects a non-UCUM quantity system`() {
        val observation =
            validObservation(
                value =
                    Observation.Value.Quantity(
                        Quantity(
                            value =
                                Decimal(
                                    value =
                                        com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString(
                                            "37.2"
                                        )
                                ),
                            unit = FhirString(value = "degree Celsius"),
                            system = Uri(value = "http://example.org/units"),
                            code = Code(value = "Cel"),
                        )
                    )
            )

        val exception =
            assertFailsWith<InvalidObservationException> {
                observation.toCreateMeasurementRequest()
            }
        assertEquals(OperationOutcome.IssueType.Value, exception.issueType)
    }

    @Test
    fun `toCreateMeasurementRequest rejects a quantity comparator`() {
        val observation =
            validObservation(
                value =
                    Observation.Value.Quantity(
                        Quantity(
                            value =
                                Decimal(
                                    value =
                                        com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString(
                                            "37.2"
                                        )
                                ),
                            comparator =
                                Enumeration(value = Quantity.QuantityComparator.GreaterThan),
                            unit = FhirString(value = "degree Celsius"),
                            system = Uri(value = "http://unitsofmeasure.org"),
                            code = Code(value = "Cel"),
                        )
                    )
            )

        assertFailsWith<InvalidObservationException> { observation.toCreateMeasurementRequest() }
    }

    private fun observationWithValue(value: String) =
        validObservation(
            value =
                Observation.Value.Quantity(
                    Quantity(
                        value =
                            Decimal(
                                value =
                                    com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString(value)
                            ),
                        unit = FhirString(value = "degree Celsius"),
                        system = Uri(value = "http://unitsofmeasure.org"),
                        code = Code(value = "Cel"),
                    )
                )
        )

    @Test
    fun `toCreateMeasurementRequest rejects more than four decimals instead of rounding them`() {
        assertFailsWith<InvalidObservationException> {
            observationWithValue("37.12345").toCreateMeasurementRequest()
        }
    }

    @Test
    fun `toCreateMeasurementRequest rejects more than eight integer digits`() {
        assertFailsWith<InvalidObservationException> {
            observationWithValue("123456789").toCreateMeasurementRequest()
        }
    }

    @Test
    fun `toCreateMeasurementRequest accepts the largest value the column stores exactly`() {
        assertEquals(
            BigDecimal("99999999.9999"),
            observationWithValue("99999999.9999").toCreateMeasurementRequest().value,
        )
    }

    @Test
    fun `toCreateMeasurementRequest accepts trailing zeros beyond four decimals`() {
        assertEquals(
            0,
            BigDecimal("37.2")
                .compareTo(observationWithValue("37.200000").toCreateMeasurementRequest().value),
        )
    }

    @Test
    fun `toCreateMeasurementRequest rejects components`() {
        val observation =
            validObservation(
                component =
                    listOf(
                        Observation.Component(
                            code =
                                CodeableConcept(
                                    coding =
                                        listOf(
                                            Coding(
                                                system = Uri(value = "http://loinc.org"),
                                                code = Code(value = "8480-6"),
                                            )
                                        )
                                )
                        )
                    )
            )

        val exception =
            assertFailsWith<InvalidObservationException> {
                observation.toCreateMeasurementRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }

    @Test
    fun `toCreateMeasurementRequest rejects dataAbsentReason`() {
        val observation =
            validObservation(
                dataAbsentReason =
                    CodeableConcept(
                        coding =
                            listOf(
                                Coding(
                                    system =
                                        Uri(
                                            value =
                                                "http://terminology.hl7.org/CodeSystem/data-absent-reason"
                                        ),
                                    code = Code(value = "unknown"),
                                )
                            )
                    )
            )

        val exception =
            assertFailsWith<InvalidObservationException> {
                observation.toCreateMeasurementRequest()
            }
        assertEquals(OperationOutcome.IssueType.Not_Supported, exception.issueType)
    }
}
