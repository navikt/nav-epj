package no.nav.helse.hackathon.credentials

import java.net.URI
import java.nio.file.Path
import tools.jackson.databind.JsonNode

private const val MAX_FIELDS = 20
private const val MAX_PATIENTS = 200
private val FIELD_NAME = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")

internal const val DEFAULT_FHIR_BASE_URL = "https://epj.ekstern.dev.nav.no/fhir"
internal const val DEFAULT_AUTH_ISSUER = "https://epj.ekstern.dev.nav.no/oidc"
internal val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

internal class StarterManifest(
    val interactiveScopes: List<String>,
    val systemScopes: List<String>,
    val teams: List<StarterTeamConfig>,
    val fhirBaseUrl: String = DEFAULT_FHIR_BASE_URL,
    val authIssuer: String = DEFAULT_AUTH_ISSUER,
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
                setOf(
                    "schemaVersion",
                    "interactiveScopes",
                    "systemScopes",
                    "teams",
                    "fhirBaseUrl",
                    "authIssuer",
                ),
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
        fhirBaseUrl = root.optionalBaseUrl("fhirBaseUrl", DEFAULT_FHIR_BASE_URL),
        authIssuer = root.optionalBaseUrl("authIssuer", DEFAULT_AUTH_ISSUER),
    )
}

private fun JsonNode.optionalBaseUrl(field: String, default: String): String {
    if (get(field) == null) return default
    val url = requireString("manifest", field)
    val uri = runCatching { URI(url) }.getOrNull()
    ensure(
        uri != null &&
            url.none { it.isWhitespace() || it.isISOControl() } &&
            !url.endsWith("/") &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawUserInfo == null &&
            (uri.scheme == "https" && uri.host != null ||
                uri.scheme == "http" && uri.host in LOOPBACK_HOSTS)
    ) {
        "manifest.$field must be an https URL (or http on localhost) without trailing slash"
    }
    return url
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
