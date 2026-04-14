val kotlin_version: String by project
val logback_version: String by project
val mockk_version: String by project

plugins {
  kotlin("jvm") version "2.3.0"
  kotlin("plugin.serialization") version "2.3.0"
  id("io.ktor.plugin") version "3.4.2"
}

group = "no.nav.helse"
version = "0.0.1"

application {
  mainClass = "io.ktor.server.netty.EngineMain"
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
  implementation("ch.qos.logback:logback-classic:$logback_version")
  //FHIR
  implementation("com.google.fhir:fhir-model:1.0.0-beta02")
  testImplementation("io.ktor:ktor-server-test-host")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
  testImplementation("io.mockk:mockk:$mockk_version")
}
