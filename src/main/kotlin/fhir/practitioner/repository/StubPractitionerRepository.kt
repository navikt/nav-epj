package no.nav.helse.fhir.practitioner.repository

import com.google.fhir.model.r4.*
import com.google.fhir.model.r4.terminologies.AdministrativeGender
import kotlin.String

class StubPractitionerRepository : PractitionerRepository {

  private val practitioners = mutableListOf(
    Practitioner(
      id = "practitioner-001",
      meta = Meta(
        profile = listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"))
      ),
      identifier = listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
          value = String(value = "9144889")
        )
      ),
      active = Boolean(value = true),
      name = listOf(
        HumanName(
          family = String(value = "Boom"),
          given = listOf(String(value = "Carl")),
          prefix = listOf(String(value = "Dr."))
        )
      ),
      gender = Enumeration(value = AdministrativeGender.Male),
      birthDate = Date(value = FhirDate.fromString("1975-06-20"))
    ),

    Practitioner(
      id = "practitioner-002",
      meta = Meta(
        profile = listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"))
      ),
      identifier = listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
          value = String(value = "9144890")
        )
      ),
      active = Boolean(value = true),
      name = listOf(
        HumanName(
          family = String(value = "Mudskipper"),
          given = listOf(String(value = "Zev")),
          prefix = listOf(String(value = "Dr."))
        )
      ),
      gender = Enumeration(value = AdministrativeGender.Female),
      birthDate = Date(value = FhirDate.fromString("1982-09-14"))
    ),

    Practitioner(
      id = "practitioner-003",
      meta = Meta(
        profile = listOf(Canonical(value = "http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"))
      ),
      identifier = listOf(
        Identifier(
          system = Uri(value = "urn:oid:2.16.578.1.12.4.1.4.4"),
          value = String(value = "9144891")
        )
      ),
      active = Boolean(value = false),
      name = listOf(
        HumanName(
          family = String(value = "Andrews"),
          given = listOf(String(value = "Chris")),
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

  override fun createPractitioner(practitioner: Practitioner): Practitioner {
    val newPractitioner = if (practitioner.id == null) {
      practitioner.copy(id = "practitioner-${java.util.UUID.randomUUID()}")
    } else {
      practitioner
    }
    practitioners.add(newPractitioner)
    return newPractitioner
  }
}
