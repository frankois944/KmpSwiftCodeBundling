package io.github.frankois944.kmpSwiftCodeBundling

import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.SUPPORTED_KOTLIN_VERSIONS
import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.assumeApplePlatform
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Links a framework with every Kotlin version the plugin claims to support.
 *
 * The compiler plugin is published once per Kotlin version and the Gradle plugin picks one at
 * resolution time; compiling all the variants proves nothing about whether the right one is chosen,
 * or whether it actually runs inside that compiler. This covers both, and it also exercises the
 * Gradle plugin itself against each Kotlin Gradle Plugin — it is compiled against the oldest
 * supported one, so a signature that moved would surface here.
 *
 * Unlike the other integration tests, these projects resolve both plugins from repositories rather
 * than from the classpath TestKit injects, which is the only way to vary the Kotlin version — and
 * happens to be exactly the path a real consumer takes, plugin marker included.
 *
 * Each version downloads its own Kotlin/Native toolchain, so this runs as its own task
 * (`./gradlew -p plugin-build kotlinVersionTest`) and not as part of `integrationTest`.
 */
@RunWith(Parameterized::class)
class KotlinVersionIntegrationTest(
    private val kotlinVersion: String,
) {
    @JvmField
    @Rule
    var projectDir: TemporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        assumeApplePlatform()
    }

    @Test
    fun `bundles swift into a framework`() {
        val project =
            AppleFrameworkProject(
                projectDir.root,
                kotlinVersion = kotlinVersion,
            )

        project.singleModule()
        project.write(
            "src/commonMain/swift/VersionedGreeter.swift",
            """
            import Foundation

            public enum VersionedGreeter {
                public static func greet() -> String { "built with Kotlin $kotlinVersion" }
            }
            """,
        )

        project.link()

        assertTrue(
            "Kotlin $kotlinVersion: the Swift never reached the framework binary",
            project.frameworkBinary.containsSymbol("VersionedGreeter"),
        )
        assertTrue("Kotlin $kotlinVersion: missing ${project.swiftModule.name}", project.swiftModule.exists())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Kotlin {0}")
        fun kotlinVersions(): List<String> =
            SUPPORTED_KOTLIN_VERSIONS.ifEmpty { error("kotlinVersions system property is not set") }
    }
}
