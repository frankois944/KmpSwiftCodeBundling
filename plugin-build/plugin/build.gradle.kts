import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
}

// The functional tests build a real Kotlin Multiplatform project, so the Kotlin Gradle Plugin has to
// be on the classpath injected by `withPluginClasspath()` - it is only `compileOnly` for the plugin
// itself, which must never bundle it.
val functionalTestPluginClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())

    // The Kotlin Gradle Plugin is provided by the consuming build, never bundled.
    compileOnly(libs.kotlin.gradle.plugin)
    functionalTestPluginClasspath(libs.kotlin.gradle.plugin)

    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(functionalTestPluginClasspath)
}

// The plugin has to know the coordinates of its companion compiler plugin at runtime, so they are
// generated into a resource rather than hardcoded.
//
// This is a generated file rather than a `processResources` filter on purpose: IntelliJ cannot model
// `filesMatching { expand(...) }` and warns "Cannot resolve resource filtering of MatchingCopyAction"
// on every Gradle sync. A generated resource directory is something it understands.
val generatedPluginResources: Provider<Directory> = layout.buildDirectory.dir("generated/pluginResources")

val generatePluginProperties by tasks.registering(WriteProperties::class) {
    destinationFile.set(
        generatedPluginResources.map { it.file("io/github/frankois944/kmpSwiftCodeBundling/plugin.properties") },
    )
    property("group", project.group.toString())
    property("version", project.version.toString())
}

sourceSets.named("main") {
    // `files(...).builtBy(...)` and not `map`/`flatMap` on the task provider: mapping to a provider
    // that has no producer of its own (`layout.buildDirectory`) silently drops the task dependency,
    // the generator never runs, and the plugin cannot read its own coordinates at runtime.
    resources.srcDir(files(generatedPluginResources).builtBy(generatePluginProperties))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

gradlePlugin {
    plugins {
        create(property("ID").toString()) {
            id = property("ID").toString()
            implementationClass = property("IMPLEMENTATION_CLASS").toString()
            version = property("VERSION").toString()
            description = property("DESCRIPTION").toString()
            displayName = property("DISPLAY_NAME").toString()
            // Note: tags cannot include "plugin" or "gradle" when publishing
            tags.set(listOf("kotlin", "multiplatform", "swift", "ios", "kmp"))
        }
    }
}

gradlePlugin {
    website.set(property("WEBSITE").toString())
    vcsUrl.set(property("VCS_URL").toString())
}

// Use Detekt with type resolution for check
tasks.named("check").configure {
    this.setDependsOn(
        this.dependsOn.filterNot {
            it is TaskProvider<*> && it.name == "detekt"
        } + tasks.named("detektMain"),
    )
}

tasks.register("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_PUBLISH_KEY")
        val secret = System.getenv("GRADLE_PUBLISH_SECRET")

        if (key == null || secret == null) {
            throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}
