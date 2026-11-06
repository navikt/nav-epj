package no.nav.helse.core

import io.ktor.server.config.*
import no.nav.helse.smart.security.SmartClient
import no.nav.helse.smart.security.loadSmartClients

class Environment(
    val postgres: PostgresConfig,
    val smart: SmartConfig,
    val valkey: ValkeyConfig,
    val ehr: EhrConfig,
)

data class PostgresConfig(
    val url: String,
    val username: String,
    val password: String,
    val seedTestdata: Boolean = false,
    val migrationUrl: String = url,
)

data class EhrConfig(val baseUrl: String)

class SmartConfig(
    val issuerBaseUrl: String,
    val fhirServerUrl: String,
    val clients: List<SmartClient>,
    val privateKeyJwk: String,
)

data class ValkeyConfig(
    val host: String,
    val port: Int,
    val useTLS: Boolean,
    val username: String?,
    val password: String?,
)

fun initEnvironment(config: ApplicationConfig): Environment {
    return Environment(
        postgres =
            PostgresConfig(
                url = config.property("database.url").getString(),
                migrationUrl =
                    config.propertyOrNull("database.migrationUrl")?.getString()
                        ?: config.property("database.url").getString(),
                username = config.propertyOrNull("database.user")?.getString().orEmpty(),
                password = config.propertyOrNull("database.password")?.getString().orEmpty(),
                seedTestdata =
                    config.propertyOrNull("database.seedTestdata")?.getString().toBoolean(),
            ),
        smart =
            SmartConfig(
                issuerBaseUrl = config.property("smart.issuerBaseUrl").getString(),
                fhirServerUrl = config.property("smart.fhirServerUrl").getString(),
                clients = loadSmartClients(config),
                privateKeyJwk = config.property("smart.privateKeyJwk").getString(),
            ),
        valkey =
            ValkeyConfig(
                host = config.property("valkey.host").getString(),
                port = config.property("valkey.port").getString().toInt(),
                useTLS = config.property("valkey.useTLS").getString().toBoolean(),
                username = config.propertyOrNull("valkey.username")?.getString(),
                password = config.propertyOrNull("valkey.password")?.getString(),
            ),
        ehr = EhrConfig(baseUrl = config.property("ehr.baseUrl").getString()),
    )
}
