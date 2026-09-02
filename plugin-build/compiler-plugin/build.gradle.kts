import org.gradle.api.file.SourceDirectorySet
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
}

/**
 * The plugin is loaded inside the Kotlin/Native compiler and uses its internal API, so a jar built
 * against one Kotlin version will not run inside another. The same sources are therefore compiled
 * once per supported Kotlin version and published as separate artifacts; the Gradle plugin picks the
 * matching one from the Kotlin version of the consuming build.
 *
 * `src/common` holds everything; `src/compat-*` only the handful of names JetBrains renamed.
 */
data class CompilerVariant(
    val name: String,
    val kotlinVersion: String,
    val compatDirectories: List<String>,
)

// Two independent breaking changes, at two different versions: `pluginId` became an abstract member
// of CompilerPluginRegistrar in 2.3, and the Kotlin/Native config types were renamed in 2.4. Keeping
// them on separate axes avoids repeating either one.
val compilerVariants =
    listOf(
        CompilerVariant("2.2", libs.versions.kotlinCompiler22.get(), listOf("konan-2.2", "registrar-2.2")),
        CompilerVariant("2.3", libs.versions.kotlinCompiler23.get(), listOf("konan-2.2", "registrar-2.3")),
        CompilerVariant("2.4", libs.versions.kotlinCompiler24.get(), listOf("konan-2.4", "registrar-2.3")),
    )

/** KGP adds a `kotlin` source directory set to every Java source set, without a typed accessor. */
val SourceSet.kotlinSources: SourceDirectorySet
    get() = (this as ExtensionAware).extensions.getByName("kotlin") as SourceDirectorySet

// Nothing is built from `main`: every variant is its own source set.
sourceSets.named("main") {
    kotlinSources.setSrcDirs(emptyList<String>())
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
}

// ...so its jar would be an empty artifact sitting next to the real ones.
tasks.named<Jar>("jar") { enabled = false }

compilerVariants.forEach { variant ->
    val sourceSet = sourceSets.create("kotlin${variant.name.replace(".", "")}")

    sourceSet.kotlinSources.setSrcDirs(
        listOf("src/common/kotlin") + variant.compatDirectories.map { "src/$it/kotlin" },
    )
    sourceSet.java.setSrcDirs(emptyList<String>())
    sourceSet.resources.setSrcDirs(listOf("src/common/resources"))

    dependencies {
        // Both are provided by the Kotlin/Native compiler at run time; nothing may be bundled.
        add(sourceSet.compileOnlyConfigurationName, kotlin("stdlib"))
        add(
            sourceSet.compileOnlyConfigurationName,
            "org.jetbrains.kotlin:kotlin-native-compiler-embeddable:${variant.kotlinVersion}",
        )
    }

    val artifact = "compiler-plugin-kotlin-${variant.name}"

    val jarTask =
        tasks.register<Jar>("${sourceSet.name}Jar") {
            description = "Builds the compiler plugin for Kotlin ${variant.name}."
            archiveBaseName.set(artifact)
            from(sourceSet.output)
        }

    tasks.named("assemble") { dependsOn(jarTask) }

    publishing.publications.register<MavenPublication>(sourceSet.name) {
        artifactId = artifact
        artifact(jarTask)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Compiler plugin entry points (CompilerPluginRegistrar, CommandLineProcessor) are all
        // marked experimental; there is no stable alternative to opt out of.
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}
