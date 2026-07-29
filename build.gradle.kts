plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(ktorLibs.plugins.ktor)
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
  alias(libs.plugins.flyway)
}

group = "no.nav.helse"
version = "0.0.1"

application {
  mainClass = "io.ktor.server.netty.EngineMain"
}

tasks {
  shadowJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles {}
  }
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(ktorLibs.server.core)
  implementation(ktorLibs.server.auth)
  implementation(ktorLibs.server.auth.jwt)
  implementation(ktorLibs.server.contentNegotiation)
  implementation(ktorLibs.serialization.kotlinx.json)
  implementation(ktorLibs.server.netty)
  implementation(ktorLibs.server.config.yaml)
  implementation(ktorLibs.client.core)
  implementation(ktorLibs.client.cio)
  implementation(ktorLibs.server.di)
  implementation(ktorLibs.serialization.jackson)
  implementation(ktorLibs.server.openapi)
  implementation(ktorLibs.server.cors)
  implementation(libs.logback.classic)
  implementation(libs.logback.encoder)
  implementation(libs.exposed.core)
  implementation(libs.exposed.jdbc)
  implementation(libs.exposed.json)
  implementation(libs.exposed.kotlin.datetime)
  implementation(libs.postgresql)
  implementation(libs.flyway.core)
  implementation(libs.flyway.postgresql)
  implementation(libs.jackson.datatype.jsr310)
  implementation(libs.nimbus.oauth2.oidc.sdk)
  implementation(libs.fhir.model)
  implementation(libs.otel.annotations)
  implementation(libs.tsm.diagnoser)
  implementation(libs.valkey.glide)

  testImplementation(ktorLibs.server.testHost)
  testImplementation(ktorLibs.client.mock)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.kotest.assertions)
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin { ktfmt("0.64").googleStyle() }
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply")
}

tasks.register<JavaExec>("runLocal") {
  group = "application"
  mainClass.set("io.ktor.server.netty.EngineMain")
  classpath = sourceSets["main"].runtimeClasspath

  args("-config=application-local.yaml")
  jvmArgs("-Dio.ktor.development=true", "-Dlogback.configurationFile=logback-local.xml")
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true
    dependsOn("spotlessApply")
}

afterEvaluate {
    tasks.named("check") {
        setDependsOn(dependsOn.filter { !it.toString().contains("detekt") })
    }
}
