pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
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

rootProject.name = "KmpSwiftCodeBundling"

include(":example")

// `plugin-build` contributes both the Gradle plugin and the compiler-plugin artifacts to `:example`.
// No explicit `dependencySubstitution` here on purpose: declaring one turns the automatic
// substitution off, and with it the lookup that lets `:example` apply the plugin by id at all.
// The mapping works because every project of the included build is named after the module it
// publishes - see `plugin-build/settings.gradle.kts`.
includeBuild("plugin-build")
