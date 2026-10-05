package no.nav.helse.smart.tooling

import java.nio.file.Path
import no.nav.helse.smart.security.ClientEndpoints
import no.nav.helse.smart.security.MAX_STARTER_TEAMS
import no.nav.helse.smart.security.StarterTeamConfig
import tools.jackson.databind.JsonNode

private const val MAX_FIELDS = 20
private const val MAX_PATIENTS = 200
private val FIELD_NAME = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")

internal class StarterManifest(
    val interactiveScopes: List<String>,
    val systemScopes: List<String>,
    val teams: List<StarterTeamConfig>,
)

internal class ClinicianInput(val byTeam: Map<String, Map<String, String>>) {
    override fun toString() = "ClinicianInput(teams=${byTeam.size}, values=redacted)"
}

internal class RosterInput(val patients: List<Map<String, String>>) {
    override fun toString() = "RosterInput(patients=${patients.size}, values=redacted)"
}

internal fun readManifest(path: Path): StarterManifest {
    val root =
        readJsonInput("manifest", path)
            .requireObject(
                "manifest",
                setOf("schemaVersion", "interactiveScopes", "systemScopes", "teams"),
            )
    root.requireSchemaVersion("manifest")
    val teams =
        root.requireArray("manifest", "teams", MAX_STARTER_TEAMS).mapIndexed { index, node ->
            parseTeam(node, "manifest.teams[$index]")
        }
    ensure(teams.map { it.teamSlot.lowercase() }.toSet().size == teams.size) {
        "manifest.teams has duplicate teamSlot values (compared case-insensitively)"
    }
    return StarterManifest(
        interactiveScopes = root.requireStringList("manifest", "interactiveScopes"),
        systemScopes = root.requireStringList("manifest", "systemScopes"),
        teams = teams,
    )
}

private fun parseTeam(node: JsonNode, path: String): StarterTeamConfig {
    node.requireObject(
        path,
        setOf("teamSlot", "publicClient", "clientSecretClient", "privateKeyJwtClient"),
    )
    return StarterTeamConfig(
        teamSlot = node.requireString(path, "teamSlot"),
        publicClient = parseEndpoints(node, path, "publicClient"),
        clientSecretClient = parseEndpoints(node, path, "clientSecretClient"),
        privateKeyJwtClient = parseEndpoints(node, path, "privateKeyJwtClient"),
    )
}

private fun parseEndpoints(team: JsonNode, path: String, field: String): ClientEndpoints {
    val node = team.get(field)
    ensure(node != null) { "$path.$field is required" }
    val clientPath = "$path.$field"
    node!!.requireObject(clientPath, setOf("launchUri", "callbackUri"))
    return ClientEndpoints(
        launchUri = node.requireString(clientPath, "launchUri"),
        callbackUri = node.requireString(clientPath, "callbackUri"),
    )
}

internal fun readClinicians(path: Path): ClinicianInput {
    val root =
        readJsonInput("clinician input", path)
            .requireObject("clinician input", setOf("schemaVersion", "teams"))
    root.requireSchemaVersion("clinician input")
    val byTeam = linkedMapOf<String, Map<String, String>>()
    root.requireArray("clinician input", "teams", MAX_STARTER_TEAMS).forEachIndexed { index, node ->
        val entry = "clinician input.teams[$index]"
        node.requireObject(entry, setOf("teamSlot", "clinician"))
        val slot = node.requireString(entry, "teamSlot")
        ensure(slot !in byTeam) { "clinician input has more than one entry for a team ($entry)" }
        val clinician = node.get("clinician")
        ensure(clinician != null) { "$entry.clinician is required" }
        byTeam[slot] = parseStringMap(clinician!!, "$entry.clinician")
    }
    return ClinicianInput(byTeam)
}

internal fun readRoster(path: Path): RosterInput {
    val root =
        readJsonInput("roster input", path)
            .requireObject("roster input", setOf("schemaVersion", "patients"))
    root.requireSchemaVersion("roster input")
    val patients =
        root.requireArray("roster input", "patients", MAX_PATIENTS).mapIndexed { index, node ->
            parseStringMap(node, "roster input.patients[$index]")
        }
    return RosterInput(patients)
}

private fun parseStringMap(node: JsonNode, path: String): Map<String, String> {
    ensure(node.isObject && node.size() in 1..MAX_FIELDS) {
        "$path must be an object with 1..$MAX_FIELDS string fields"
    }
    return node.properties().associate { (name, value) ->
        ensure(
            FIELD_NAME.matches(name) &&
                value.isString &&
                value.stringValue().length <= MAX_VALUE_LENGTH
        ) {
            "$path has a field with an invalid name or a value that is not a string of up to $MAX_VALUE_LENGTH characters"
        }
        name to value.stringValue()
    }
}
