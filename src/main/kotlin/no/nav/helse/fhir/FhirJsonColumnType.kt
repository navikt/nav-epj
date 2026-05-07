package no.nav.helse.fhir

import com.google.fhir.model.r4.FhirR4Json
import com.google.fhir.model.r4.Resource
import io.r2dbc.postgresql.codec.Json
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

/**
 * Custom JSONB column type that uses FhirR4Json (Jackson-based) for serialization. This allows
 * storing FHIR R4 Resource types directly in PostgreSQL JSONB columns.
 *
 * Note: This only works with Resource types (Patient, Practitioner, etc.), not with component types
 * (Meta, Identifier, etc.) because FhirR4Json only supports encoding/decoding full Resources.
 */
class FhirResourceColumnType<T : Resource>(private val klass: Class<T>) : ColumnType<T>() {

  companion object {
    private val fhirJson = FhirR4Json()
  }

  override fun sqlType(): String = "JSONB"

  override fun valueFromDB(value: Any): T {
    val jsonString =
      when (value) {
        is Json -> value.asString()
        is String -> value
        is ByteArray -> String(value)
        else -> error("Unexpected value type: ${value::class.qualifiedName}")
      }

    @Suppress("UNCHECKED_CAST")
    return fhirJson.decodeFromString(jsonString) as T
  }

  override fun notNullValueToDB(value: T): Any {
    return Json.of(fhirJson.encodeToString(value))
  }

  override fun nonNullValueToString(value: T): String {
    return "'${fhirJson.encodeToString(value)}'"
  }
}

/**
 * Creates a JSONB column that uses FhirR4Json for serialization. Use this for FHIR Resource types
 * (Patient, Practitioner, Organization, etc.)
 */
inline fun <reified T : Resource> Table.fhirResource(name: String): Column<T> =
  registerColumn(name, FhirResourceColumnType(T::class.java))
