package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.Canonical
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.Enumeration
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Identifier
import com.google.fhir.model.r4.Meta
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.AdministrativeGender as FhirAdministrativeGender
import io.ktor.server.plugins.BadRequestException
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.core.utils.logger
import no.nav.helse.epj.pasient.AdministrativeGender
import no.nav.helse.epj.pasient.Pasient
import no.nav.helse.epj.pasient.PasientId
import no.nav.helse.epj.pasient.PasientService
import no.nav.helse.epj.pasient.PersonidentType

// Norwegian national identifier series
// (ehelse.no/oid-identifikatorserier-i-helse-og-omsorgstjenesten),
// tildelt av Skatteetaten. Legacy patients with no recorded type predate this distinction and were
// always fødselsnummer, so they default to the FNR system.
private const val FNR_SYSTEM = "urn:oid:2.16.578.1.12.4.1.4.1"
private const val DNR_SYSTEM = "urn:oid:2.16.578.1.12.4.1.4.2"

data class PatientIdentifierSearch(val system: String?, val value: String)

fun parsePatientIdentifierSearch(token: String): PatientIdentifierSearch {
    val system = if ('|' in token) token.substringBefore('|') else null
    val value = token.substringAfter('|')
    if (system != null && system != FNR_SYSTEM && system != DNR_SYSTEM) {
        throw BadRequestException("Parameteren 'identifier' har et ukjent system")
    }
    if (value.isBlank()) {
        throw BadRequestException("Parameteren 'identifier' mangler verdi")
    }
    return PatientIdentifierSearch(system, value)
}

class PatientService(val epjPatientService: PasientService) {

    val log = logger()

    @WithSpan
    suspend fun getPatient(patientInputId: PatientInputId): Patient? {
        val span = Span.current()
        span.setAttribute("patientId", patientInputId.value.toString())
        val epjPatient = epjPatientService.getPasientById(PasientId(patientInputId.value))
        return epjPatient?.toPatient()
    }

    suspend fun findByIdentifier(search: PatientIdentifierSearch): Patient? {
        val pasient = epjPatientService.getPasientByPersonident(search.value) ?: return null
        if (
            search.system != null && search.system != pasient.personidentType.toIdentifierSystem()
        ) {
            return null
        }
        return pasient.toPatient()
    }

    fun searchset(patients: List<Patient>): Bundle =
        Bundle(
            type = Enumeration(value = Bundle.BundleType.Searchset),
            entry =
                patients.map { patient ->
                    Bundle.Entry(fullUrl = Uri(value = "Patient/${patient.id}"), resource = patient)
                },
        )

    fun Pasient.toPatient(): Patient {
        return Patient(
            meta =
                Meta(
                    profile =
                        listOf(
                            Canonical(
                                value = "http://hl7.no/fhir/StructureDefinition/no-basis-Patient"
                            )
                        )
                ),
            id = this.id.value.toString(),
            identifier =
                listOf(
                    Identifier(
                        system = Uri(value = this.personidentType.toIdentifierSystem()),
                        value = com.google.fhir.model.r4.String(value = this.personident),
                    )
                ),
            name =
                listOf(
                    HumanName(
                        family = com.google.fhir.model.r4.String(value = this.etternavn),
                        given = listOf(com.google.fhir.model.r4.String(value = this.fornavn)),
                    )
                ),
            gender = this.gender?.let { Enumeration(value = it.toFhirAdministrativeGender()) },
            birthDate = this.birthDate?.let { Date(value = FhirDate.fromString(it.toString())) },
        )
    }

    private fun PersonidentType?.toIdentifierSystem(): String =
        when (this) {
            PersonidentType.DNR -> DNR_SYSTEM
            PersonidentType.FNR,
            null -> FNR_SYSTEM
        }

    private fun AdministrativeGender.toFhirAdministrativeGender(): FhirAdministrativeGender =
        when (this) {
            AdministrativeGender.MALE -> FhirAdministrativeGender.Male
            AdministrativeGender.FEMALE -> FhirAdministrativeGender.Female
            AdministrativeGender.OTHER -> FhirAdministrativeGender.Other
            AdministrativeGender.UNKNOWN -> FhirAdministrativeGender.Unknown
        }
}
