package no.nav.helse.fhir.patient

import com.google.fhir.model.r4.Patient
import java.util.UUID
import no.nav.helse.core.db.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class PatientRepositoryImpl : PatientRepository {

  override suspend fun getById(id: String): Patient? {
    return dbQuery {
      PatientTable.selectAll()
        .where { PatientTable.id eq id }
        .singleOrNull()
        ?.let { it[PatientTable.data] }
    }
  }

  override suspend fun getAll(): List<Patient> {
    return dbQuery { PatientTable.selectAll().map { it[PatientTable.data] }.toList() }
  }

  override suspend fun create(patient: Patient): Patient {
    val id = patient.id ?: "patient-${UUID.randomUUID()}"
    val patientData = if (patient.id == null) patient.copy(id = id) else patient

    dbQuery {
      PatientTable.insert {
        it[PatientTable.id] = id
        it[PatientTable.data] = patientData
      }
    }

    return patientData
  }
}
