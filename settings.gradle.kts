rootProject.name = "nav-epj"

val ktorVersion = "3.5.1"

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
