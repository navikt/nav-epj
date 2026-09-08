rootProject.name = "nav-epj"

val ktorVersion = "3.5.2"

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
  }

  versionCatalogs {
    create("ktorLibs").from("io.ktor:ktor-version-catalog:${ktorVersion}")
  }
}

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://jitpack.io")
  }
}

plugins {
  id("io.github.ben-manes.versions.settings") version "0.61.0"
}
