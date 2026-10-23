package no.nav.helse.hackathon.credentials

import java.net.URI
import no.nav.helse.smart.security.RawClientRegistration

internal const val PACKET_SCHEMA_VERSION = 1
internal const val LAUNCH_KEY_FILE = "launch-key.private.jwk.json"
internal const val BACKEND_KEY_FILE = "backend-key.private.jwk.json"
internal const val STATUS_CREDENTIALS_ONLY = "CREDENTIALS_ONLY"
internal const val STATUS_INPUTS_SUPPLIED = "INPUTS_SUPPLIED_UNVERIFIED"
internal const val VERIFICATION_NOTE =
    "Clinician and roster content was copied verbatim and not validated; " +
        "the operator must verify it before this packet is handed out."
private const val SIGNING_ALGORITHM = "RS384"
private const val SEEDED_PATIENT_IDENTIFIER = "urn:oid:2.16.578.1.12.4.1.4.1|20856210112"

internal class ExternalInputs(val clinicians: ClinicianInput, val roster: RosterInput)

internal fun registryJson(registrations: List<RawClientRegistration>): ByteArray =
    toolMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(registrations.map { it.toJson() })

private fun RawClientRegistration.toJson(): Map<String, Any> =
    linkedMapOf<String, Any>().also { json ->
        json["clientId"] = clientId
        teamSlot?.let { json["teamSlot"] = it }
        grantTypes?.let { json["grantTypes"] = it }
        tokenEndpointAuthMethod?.let { json["tokenEndpointAuthMethod"] = it }
        if (launchUris.isNotEmpty()) json["launchUris"] = launchUris
        if (redirectUris.isNotEmpty()) json["redirectUris"] = redirectUris
        clientSecret?.let { json["clientSecret"] = it }
        jwksUri?.let { json["jwksUri"] = it }
        jwkSet?.let { json["jwkSet"] = it }
        json["scopes"] = scopes
        displayName?.let { json["displayName"] = it }
        navn?.let { json["navn"] = it }
        beskrivelse?.let { json["beskrivelse"] = it }
        ikon?.let { json["ikon"] = it }
        launchMode?.let { json["launchMode"] = it }
    }

internal fun writeTeamFiles(
    export: PrivateExport,
    manifest: StarterManifest,
    generated: GeneratedStarterCredentials,
    inputs: ExternalInputs?,
) {
    export.createDirectory("teams")
    val configs = manifest.teams.associateBy { it.teamSlot }
    generated.teams.forEach { team ->
        val dir = "teams/${team.teamSlot}"
        export.createDirectory(dir)
        export.writeFile("$dir/$LAUNCH_KEY_FILE", keyFile(team.launchKey.privateJwk.value))
        export.writeFile("$dir/$BACKEND_KEY_FILE", keyFile(team.backendKey.privateJwk.value))
        envFiles(team, configs.getValue(team.teamSlot), manifest).forEach { (name, content) ->
            export.writeFile("$dir/$name", content.toByteArray())
        }
        export.writeFile(
            "$dir/packet.json",
            packetJson(team, configs.getValue(team.teamSlot), manifest, inputs),
        )
    }
}

private fun envFiles(
    team: TeamCredentials,
    config: StarterTeamConfig,
    manifest: StarterManifest,
): Map<String, String> {
    val interactive = manifest.interactiveScopes.joinToString(" ")
    val system = manifest.systemScopes.joinToString(" ")
    fun ehr(clientId: String, endpoints: ClientEndpoints, vararg extra: Pair<String, String>) =
        listOf("CLIENT_ID" to clientId, "SCOPES" to interactive) +
            redirectEntries(endpoints.callbackUri) +
            extra
    return mapOf(
        "public.env" to envText(manifest, ehr(team.publicClientId, config.publicClient)),
        "client-secret.env" to
            envText(
                manifest,
                ehr(
                    team.clientSecret.clientId,
                    config.clientSecretClient,
                    "CLIENT_SECRET" to team.clientSecret.secret.value,
                ),
            ),
        "private-key-jwt.env" to
            envText(
                manifest,
                ehr(
                    team.launchKey.clientId,
                    config.privateKeyJwtClient,
                    "PRIVATE_JWK_PATH" to "keys/$LAUNCH_KEY_FILE",
                ),
            ),
        "backend-services.env" to
            envText(
                manifest,
                listOf(
                    "CLIENT_ID" to team.backendKey.clientId,
                    "SCOPES" to system,
                    "PRIVATE_JWK_PATH" to "keys/$BACKEND_KEY_FILE",
                    "PATIENT_IDENTIFIER" to SEEDED_PATIENT_IDENTIFIER,
                ),
            ),
    )
}

private fun redirectEntries(callbackUri: String): List<Pair<String, String>> {
    val uri = URI(callbackUri)
    val loopbackPort =
        uri.port.takeIf { uri.scheme == "http" && uri.host in LOOPBACK_HOSTS && it != -1 }
    return listOfNotNull(loopbackPort?.let { "PORT" to it.toString() }) +
        ("REDIRECT_URI" to callbackUri)
}

private fun envText(manifest: StarterManifest, entries: List<Pair<String, String>>): String =
    (listOf("FHIR_BASE_URL" to manifest.fhirBaseUrl, "AUTH_ISSUER" to manifest.authIssuer) +
            entries)
        .joinToString("") { (key, value) ->
            check(value.none { it == '\n' || it == '\r' }) { "env value for $key is multiline" }
            "$key=$value\n"
        }

private fun keyFile(jwk: String) = "$jwk\n".toByteArray()

private fun packetJson(
    team: TeamCredentials,
    config: StarterTeamConfig,
    manifest: StarterManifest,
    inputs: ExternalInputs?,
): ByteArray {
    val packet = linkedMapOf<String, Any>()
    packet["schemaVersion"] = PACKET_SCHEMA_VERSION
    packet["status"] = if (inputs == null) STATUS_CREDENTIALS_ONLY else STATUS_INPUTS_SUPPLIED
    packet["verificationNote"] =
        if (inputs == null) {
            "Clinician login and roster were not supplied; this packet is incomplete."
        } else {
            VERIFICATION_NOTE
        }
    packet["missingInputs"] = if (inputs == null) listOf("clinician", "roster") else emptyList()
    packet["teamSlot"] = team.teamSlot
    packet["clients"] =
        linkedMapOf(
            "public" to
                linkedMapOf(
                    "clientId" to team.publicClientId,
                    "tokenEndpointAuthMethod" to "none",
                    "launchUri" to config.publicClient.launchUri,
                    "callbackUri" to config.publicClient.callbackUri,
                ),
            "clientSecret" to
                linkedMapOf(
                    "clientId" to team.clientSecret.clientId,
                    "tokenEndpointAuthMethod" to "client_secret_basic",
                    "clientSecret" to team.clientSecret.secret.value,
                    "launchUri" to config.clientSecretClient.launchUri,
                    "callbackUri" to config.clientSecretClient.callbackUri,
                ),
            "privateKeyJwt" to
                linkedMapOf(
                    "clientId" to team.launchKey.clientId,
                    "tokenEndpointAuthMethod" to "private_key_jwt",
                    "keyId" to team.launchKey.keyId,
                    "signingAlgorithm" to SIGNING_ALGORITHM,
                    "privateKeyFile" to LAUNCH_KEY_FILE,
                    "launchUri" to config.privateKeyJwtClient.launchUri,
                    "callbackUri" to config.privateKeyJwtClient.callbackUri,
                ),
            "backendServices" to
                linkedMapOf(
                    "clientId" to team.backendKey.clientId,
                    "tokenEndpointAuthMethod" to "private_key_jwt",
                    "grantType" to "client_credentials",
                    "keyId" to team.backendKey.keyId,
                    "signingAlgorithm" to SIGNING_ALGORITHM,
                    "privateKeyFile" to BACKEND_KEY_FILE,
                ),
        )
    packet["scopes"] =
        linkedMapOf("interactive" to manifest.interactiveScopes, "system" to manifest.systemScopes)
    if (inputs != null) {
        packet["clinician"] = inputs.clinicians.byTeam.getValue(team.teamSlot)
        packet["roster"] = linkedMapOf("patients" to inputs.roster.patients)
    }
    return toolMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(packet)
}
