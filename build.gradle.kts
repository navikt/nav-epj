val kotlin_version: String by project
val logback_version: String by project
val mockk_version: String by project
val exposed_version: String by project
val kotest_version: String by project


plugins {
  kotlin("jvm") version "2.3.0"
  kotlin("plugin.serialization") version "2.3.0"
  id("io.ktor.plugin") version "3.4.2"
  id("com.diffplug.spotless") version "8.4.0"
  id("dev.detekt") version "2.0.0-alpha.3"
}

group = "no.nav.helse"
version = "0.0.1"

application {
  mainClass = "io.ktor.server.netty.EngineMain"
}

// this attempts to fix an issue: FlywayException: Unknown prefix for location (should be one of ): classpath:db/callback
//tasks.withType<ShadowJar> {
//  mergeServiceFiles()
//  isZip64 = true
//  duplicatesStrategy = DuplicatesStrategy.INCLUDE
//}

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
  implementation("io.ktor:ktor-server-core")
  implementation("io.ktor:ktor-server-auth")
  implementation("io.ktor:ktor-server-auth-jwt")
  implementation("io.ktor:ktor-server-content-negotiation")
  implementation("io.ktor:ktor-server-html-builder")
  implementation("io.ktor:ktor-serialization-kotlinx-json")
  implementation("io.ktor:ktor-server-netty")
  implementation("io.ktor:ktor-server-config-yaml")
  implementation("io.ktor:ktor-client-core")
  implementation("io.ktor:ktor-client-cio")
  implementation("io.ktor:ktor-server-di")
  implementation("ch.qos.logback:logback-classic:$logback_version")
  implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
  implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
  implementation("org.jetbrains.exposed:exposed-json:$exposed_version")
  implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed_version")
  implementation("org.postgresql:postgresql:42.7.10")
  implementation("org.flywaydb:flyway-core:12.4.0")
  implementation("org.flywaydb:flyway-database-postgresql:11.8.2")
  implementation("io.ktor:ktor-serialization-jackson")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.0")


  //FHIR
  implementation("com.google.fhir:fhir-model:1.0.0-beta02")

  // Test
  testImplementation("io.ktor:ktor-server-test-host")
  testImplementation("io.kotest:kotest-assertions-core:${kotest_version}")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
  testImplementation("io.mockk:mockk:$mockk_version")
  testImplementation("org.testcontainers:testcontainers:1.21.0")
  testImplementation("org.testcontainers:postgresql:1.21.0")
  testImplementation("org.testcontainers:junit-jupiter:1.21.0")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin { ktfmt("0.62").googleStyle() }
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply")
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
