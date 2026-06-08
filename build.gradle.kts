plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ktor)
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

repositories {
  mavenCentral()
  google()
}

dependencies {
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.server.auth.jwt)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.html.builder)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.config.yaml)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.cio)
  implementation(libs.ktor.server.di)
  implementation(libs.ktor.serialization.jackson)
  implementation(libs.ktor.server.routing.openapi)
  implementation(libs.ktor.server.cors)
  implementation(libs.logback.classic)
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

  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.kotest.assertions)
  testImplementation(kotlin("test-junit"))
  testImplementation(libs.mockk)
  testImplementation(libs.testcontainers.core)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.junit)
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin { ktfmt("0.62").googleStyle() }
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

// TODO: remove this
tasks.withType<Test> {
  enabled = false

}

afterEvaluate {
    tasks.named("check") {
        setDependsOn(dependsOn.filter { !it.toString().contains("detekt") })
    }
}
