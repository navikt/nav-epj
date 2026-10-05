package no.nav.helse.hackathon.credentials

import no.nav.helse.smart.security.RawClientRegistration

internal const val PACKET_SCHEMA_VERSION = 1
internal const val LAUNCH_KEY_FILE = "launch-key.private.jwk.json"
internal const val BACKEND_KEY_FILE = "backend-key.private.jwk.json"
private const val SIGNING_ALGORITHM = "RS384"

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
        export.writeFile(
            "$dir/packet.json",
            packetJson(team, configs.getValue(team.teamSlot), manifest, inputs),
        )
    }
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
    packet["status"] = if (inputs == null) "CREDENTIALS_ONLY" else "COMPLETE"
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
