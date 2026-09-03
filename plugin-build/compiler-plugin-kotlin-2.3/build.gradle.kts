import org.gradle.api.file.SourceDirectorySet
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
}

/**
 * The Kotlin 2.3 build of the compiler plugin.
 *
 * The sources, the tests and the linter configuration all live in `:compiler-plugin-kotlin-2.4`, which builds
 * the newest supported Kotlin version; this project compiles the very same files against an older
 * compiler and publishes them under their own coordinates.
 *
 * It is a project of its own, rather than another source set of `:compiler-plugin-kotlin-2.4`, because a
 * composite build maps a module to a project through the coordinates of its publication: one
 * published artifact needs one project.
 */
val kotlinCompilerVersion = libs.versions.kotlinCompiler23.get()
val artifactName = "compiler-plugin-kotlin-2.3"
val shared = "../compiler-plugin/src"

/** KGP adds a `kotlin` source directory set to every Java source set, without a typed accessor. */
val SourceSet.kotlinSources: SourceDirectorySet
    get() = (this as ExtensionAware).extensions.getByName("kotlin") as SourceDirectorySet

sourceSets.named("main") {
    kotlinSources.setSrcDirs(
        listOf(
            "$shared/common/kotlin",
            "$shared/konan-2.2/kotlin",
            "$shared/registrar-2.3/kotlin",
        ),
    )
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(listOf("$shared/common/resources"))
}

base.archivesName.set(artifactName)

dependencies {
    // Both are provided by the Kotlin/Native compiler at run time; nothing may be bundled.
    compileOnly(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlin:kotlin-native-compiler-embeddable:$kotlinCompilerVersion")
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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

// These files are linted in `:compiler-plugin-kotlin-2.4`. Linting them here as well would have two projects
// reading - and `ktlintFormat` writing - the same files at the same time.
tasks.matching { it.name.startsWith("ktlint") || it.name.startsWith("detekt") }.configureEach {
    enabled = false
}
