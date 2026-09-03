import dev.detekt.gradle.Detekt
import org.gradle.api.file.SourceDirectorySet
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
}

/**
 * The compiler plugin is loaded inside the Kotlin/Native compiler and uses its internal API, so a
 * jar built against one Kotlin version will not run inside another. The same sources are therefore
 * compiled once per supported Kotlin version and published as separate artifacts; the Gradle plugin
 * picks the matching one from the Kotlin version of the consuming build.
 *
 * This project - `:compiler-plugin-kotlin-2.4`, whose directory is deliberately named after no
 * Kotlin version - owns the sources, the tests and the linter configuration, and builds the newest
 * supported version. The older ones are `:compiler-plugin-kotlin-2.2` and
 * `:compiler-plugin-kotlin-2.3`, which point their source directories back here.
 *
 * One project per published artifact, each named after the module it publishes, is not a matter of
 * taste: that name is how a composite build maps the module back to the project, and it is how
 * `:example` resolves the compiler plugin from the included build instead of from a repository.
 */
val kotlinCompilerVersion = libs.versions.kotlinCompiler24.get()
val artifactName = "compiler-plugin-kotlin-2.4"

/** KGP adds a `kotlin` source directory set to every Java source set, without a typed accessor. */
val SourceSet.kotlinSources: SourceDirectorySet
    get() = (this as ExtensionAware).extensions.getByName("kotlin") as SourceDirectorySet

// `src/common` holds everything; the compat directories only the handful of names JetBrains renamed.
// Two independent axes: the registrar changed in 2.3, the Kotlin/Native config types in 2.4.
sourceSets.named("main") {
    kotlinSources.setSrcDirs(
        listOf(
            "src/common/kotlin",
            "src/konan-2.4/kotlin",
            "src/registrar-2.3/kotlin",
        ),
    )
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(listOf("src/common/resources"))
}

base.archivesName.set(artifactName)

dependencies {
    // Both are provided by the Kotlin/Native compiler at run time; nothing may be bundled.
    compileOnly(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlin:kotlin-native-compiler-embeddable:$kotlinCompilerVersion")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-native-compiler-embeddable:$kotlinCompilerVersion")
}

// Eager `create`, so the publication exists as soon as the project is configured rather than
// when the task graph first asks for it.
publishing.publications.create<MavenPublication>("compilerPlugin") {
    artifactId = artifactName
    from(components["java"])
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// The compat directories of the older variants belong to no source set here, so detekt would never
// look at them - which is how seventeen over-long lines got in unnoticed. This task has no type
// resolution, so analysing sources written against three different Kotlin versions is fine.
tasks.withType<Detekt>().configureEach {
    setSource(
        files(
            "src/common/kotlin",
            "src/konan-2.2/kotlin",
            "src/konan-2.4/kotlin",
            "src/registrar-2.2/kotlin",
            "src/registrar-2.3/kotlin",
            "src/test/kotlin",
        ),
    )
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Compiler plugin entry points (CompilerPluginRegistrar, CommandLineProcessor) are all
        // marked experimental; there is no stable alternative to opt out of.
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}
