package no.nav.helse.fhir.practitioner.repository

import com.google.fhir.model.r4.*
import com.google.fhir.model.r4.terminologies.AdministrativeGender

class StubPractitionerRepository : PractitionerRepository {

  private val practitioners = listOf(
    Practitioner(
      id = "practitioner-001",
      active = Boolean(value = true),
      name = listOf(
        HumanName(
          family = String(value = "Larsen"),
          given = listOf(String(value = "Erik")),
          prefix = listOf(String(value = "Dr."))
        )
      ),
      gender = Enumeration(value = AdministrativeGender.Male),
      birthDate = Date(value = FhirDate.fromString("1975-06-20"))
    ),

    Practitioner(
      id = "practitioner-002",
      active = Boolean(value = true),
      name = listOf(
        HumanName(
          family = String(value = "Berg"),
          given = listOf(String(value = "Maria")),
          prefix = listOf(String(value = "Dr."))
        )
      ),
      gender = Enumeration(value = AdministrativeGender.Female),
      birthDate = Date(value = FhirDate.fromString("1982-09-14"))
    ),

    Practitioner(
      id = "practitioner-003",
      active = Boolean(value = false),
      name = listOf(
        HumanName(
          family = String(value = "Olsen"),
          given = listOf(String(value = "Anders")),
          prefix = listOf(String(value = "Dr."))
        )
      ),
      gender = Enumeration(value = AdministrativeGender.Male),
      birthDate = Date(value = FhirDate.fromString("1968-02-28"))
    )
  )

  override fun getPractitioner(id: String): Practitioner? {
    return practitioners.find { it.id == id }
  }

  override fun getAllPractitioners(): List<Practitioner> {
    return practitioners
  }
}
