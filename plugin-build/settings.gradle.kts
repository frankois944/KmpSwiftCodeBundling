pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("com.gradle.develocity") version "4.5.0"
}

develocity {
    buildScan.termsOfUseUrl = "https://gradle.com/terms-of-service"
    buildScan.termsOfUseAgree = "yes"
    buildScan.publishing.onlyIf {
        System.getenv("GITHUB_ACTIONS") == "true" &&
            it.buildResult.failures.isNotEmpty()
    }
}

rootProject.name = ("io.github.frankois944.kmpSwiftCodeBundling")

include(":plugin")

// One project per published compiler-plugin artifact, each named exactly after the module it
// publishes. That is what lets a composite build map the module back to the project, and it is how
// `:example` resolves the compiler plugin from here instead of going to look for it on Maven Central.
//
// The sources are shared and belong to no single Kotlin version, so they stay in a neutral
// directory; the project that compiles the newest version simply points at it.
include(":compiler-plugin-kotlin-2.2")
include(":compiler-plugin-kotlin-2.3")
include(":compiler-plugin-kotlin-2.4")

project(":compiler-plugin-kotlin-2.4").projectDir = file("compiler-plugin")
