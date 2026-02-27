plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.flyway)
}

group = "no.nav.tsm"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.di)
    implementation(libs.ktor.server.pebble)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.routing.openapi)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.logback.classic)
    implementation(libs.logback.encoder)
    implementation(libs.nimbus.oidc.sdk)
    implementation(libs.khealth)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.postgres)
    implementation(libs.flyway.core)
    implementation(libs.postgresql)
    implementation(libs.h2)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

tasks {
    shadowJar {
        mergeServiceFiles {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
}

tasks.register<Exec>("preRunLocal") {
    group = "application"
    commandLine("./scripts/pre-dev.sh")
}

tasks.register<JavaExec>("runLocal") {
    group = "application"
    mainClass.set("io.ktor.server.netty.EngineMain")
    classpath = sourceSets["main"].runtimeClasspath

    args("-config=application-local.yaml")
    jvmArgs("-Dio.ktor.development=true")

    dependsOn("preRunLocal")
}
