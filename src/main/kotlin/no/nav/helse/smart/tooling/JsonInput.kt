package no.nav.helse.smart.tooling

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private const val MAX_INPUT_BYTES = 1_048_576L
internal const val INPUT_SCHEMA_VERSION = 1
internal const val MAX_VALUE_LENGTH = 2048

internal val toolMapper: JsonMapper =
    JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

/**
 * Reads a JSON input file. Parse failures report only the label and position, never the offending
 * source text, because the file may hold secrets.
 */
internal fun readJsonInput(label: String, path: Path): JsonNode {
    val bytes =
        try {
            ensure(Files.isRegularFile(path) && Files.size(path) <= MAX_INPUT_BYTES) {
                "$label must be a regular file of at most $MAX_INPUT_BYTES bytes"
            }
            Files.readAllBytes(path)
        } catch (e: IOException) {
            throw CredentialToolException("$label could not be read (${e.javaClass.simpleName})", e)
        }
    return try {
        toolMapper.readTree(bytes)
    } catch (e: JacksonException) {
        val position = e.location?.let { " at line ${it.lineNr}, column ${it.columnNr}" }.orEmpty()
        throw CredentialToolException("$label is not valid JSON$position", e)
    }
}

internal fun JsonNode.requireObject(path: String, allowed: Set<String>): JsonNode {
    ensure(isObject) { "$path must be a JSON object" }
    ensure(propertyNames().all { it in allowed }) {
        "$path has an unknown field; allowed fields: ${allowed.sorted().joinToString()}"
    }
    return this
}

internal fun JsonNode.requireString(
    path: String,
    field: String,
    maxLength: Int = MAX_VALUE_LENGTH,
): String {
    val value = get(field)
    ensure(value != null && value.isString) { "$path.$field must be a string" }
    val text = value!!.stringValue()
    ensure(text.isNotBlank() && text.length <= maxLength) {
        "$path.$field must be 1..$maxLength characters"
    }
    return text
}

internal fun JsonNode.requireArray(path: String, field: String, max: Int): List<JsonNode> {
    val value = get(field)
    ensure(value != null && value.isArray && value.size() in 1..max) {
        "$path.$field must be an array of 1..$max items"
    }
    return value!!.toList()
}

internal fun JsonNode.requireSchemaVersion(label: String) {
    ensure(
        get("schemaVersion")?.let { it.isInt && it.intValue() == INPUT_SCHEMA_VERSION } == true
    ) {
        "$label.schemaVersion must be $INPUT_SCHEMA_VERSION"
    }
}

internal fun JsonNode.requireStringList(path: String, field: String): List<String> =
    requireArray(path, field, MAX_LIST_ITEMS).mapIndexed { index, item ->
        ensure(item.isString && item.stringValue().isNotBlank()) {
            "$path.$field[$index] must be a non-empty string"
        }
        item.stringValue()
    }

private const val MAX_LIST_ITEMS = 100
