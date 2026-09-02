import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    // The plugin runs inside the Kotlin/Native compiler, which already provides the stdlib
    // and the whole `org.jetbrains.kotlin.backend.konan` API. Nothing must be bundled.
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.kotlin.native.compiler.embeddable)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Compiler plugin entry points (CompilerPluginRegistrar, CommandLineProcessor) are all
        // marked experimental; there is no stable alternative to opt out of.
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "compiler-plugin"
        }
    }
}
