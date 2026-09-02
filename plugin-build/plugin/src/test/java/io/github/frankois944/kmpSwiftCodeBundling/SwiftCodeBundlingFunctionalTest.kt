package io.github.frankois944.kmpSwiftCodeBundling

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Runs real Gradle builds against a generated Kotlin Multiplatform project.
 *
 * Only tasks that do not need the Apple toolchain are executed, so these run on any host: the Swift
 * compilation and the linking are covered by building the `example` module on macOS.
 */
class SwiftCodeBundlingFunctionalTest {
    @JvmField
    @Rule
    var projectDir: TemporaryFolder = TemporaryFolder()

    private val bundledSwift: File
        get() = projectDir.root.resolve("build/swift-code-bundling/compilations/iosSimulatorArm64/main/swift")

    @Before
    fun setUp() {
        write(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = "functional-test"
            """.trimIndent(),
        )

        write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx2g
            # No native task is ever executed here, so the Kotlin/Native distribution must not be
            # provisioned - it would download roughly a gigabyte per test run.
            kotlin.native.toolchain.enabled=false
            kotlin.native.ignoreDisabledTargets=true
            """.trimIndent(),
        )

        buildFileWith("")
    }

    private fun buildFileWith(extraConfiguration: String) {
        write(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("io.github.frankois944.kmpSwiftCodeBundling")
            }

            kotlin {
                iosSimulatorArm64 {
                    binaries.framework {
                        baseName = "TestKit"
                    }
                }
            }

            $extraConfiguration
            """.trimIndent(),
        )
    }

    private fun write(
        path: String,
        content: String,
    ): File =
        projectDir.root.resolve(path).apply {
            parentFile.mkdirs()
            writeText(content)
        }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir.root)
            .withArguments(*arguments, "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()

    private fun build(vararg arguments: String): BuildResult = runner(*arguments).build()

    private fun buildAndFail(vararg arguments: String): BuildResult = runner(*arguments).buildAndFail()

    private fun bundledFileNames(): Set<String> =
        bundledSwift
            .walkTopDown()
            .filter { it.isFile }
            .map { it.name }
            .toSet()

    @Test
    fun `collects the swift of every source set of the compilation`() {
        // The regression this guards against: reading the source set hierarchy too early yields an
        // empty set, and the task silently bundles nothing.
        write("src/commonMain/swift/Common.swift", "public enum Common {}")
        write("src/iosMain/swift/Ios.swift", "public enum Ios {}")
        write("src/iosSimulatorArm64Main/swift/Simulator.swift", "public enum Simulator {}")

        val result = build("processSwiftSourcesIosSimulatorArm64")

        assertEquals(TaskOutcome.SUCCESS, result.task(":processSwiftSourcesIosSimulatorArm64")?.outcome)
        assertEquals(setOf("Common.swift", "Ios.swift", "Simulator.swift"), bundledFileNames())
    }

    @Test
    fun `keeps the directory structure of a source set`() {
        write("src/commonMain/swift/nested/Deep.swift", "public enum Deep {}")

        build("processSwiftSourcesIosSimulatorArm64")

        assertTrue(bundledSwift.resolve("nested/Deep.swift").exists())
    }

    @Test
    fun `ignores files that are not swift`() {
        write("src/commonMain/swift/Common.swift", "public enum Common {}")
        write("src/commonMain/swift/notes.txt", "not swift")
        write("src/commonMain/swift/Stray.kt", "// not swift either")

        build("processSwiftSourcesIosSimulatorArm64")

        assertEquals(setOf("Common.swift"), bundledFileNames())
    }

    @Test
    fun `fails when two swift files share a name`() {
        write("src/commonMain/swift/one/Duplicate.swift", "public enum One {}")
        write("src/commonMain/swift/two/Duplicate.swift", "public enum Two {}")

        val result = buildAndFail("processSwiftSourcesIosSimulatorArm64")

        assertTrue(result.output.contains("have the same name 'Duplicate.swift'"))
    }

    @Test
    fun `fails when two source sets contribute the same file name`() {
        write("src/commonMain/swift/Duplicate.swift", "public enum One {}")
        write("src/iosMain/swift/Duplicate.swift", "public enum Two {}")

        val result = buildAndFail("processSwiftSourcesIosSimulatorArm64")

        assertTrue(result.output.contains("have the same name 'Duplicate.swift'"))
    }

    @Test
    fun `is up to date until a swift source changes`() {
        val source = write("src/commonMain/swift/Common.swift", "public enum Common {}")

        build("processSwiftSourcesIosSimulatorArm64")

        val secondRun = build("processSwiftSourcesIosSimulatorArm64")
        assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":processSwiftSourcesIosSimulatorArm64")?.outcome)

        source.writeText("public enum Common { public static let version = 2 }")

        val thirdRun = build("processSwiftSourcesIosSimulatorArm64")
        assertEquals(TaskOutcome.SUCCESS, thirdRun.task(":processSwiftSourcesIosSimulatorArm64")?.outcome)
    }

    @Test
    fun `removes sources that were deleted from the source set`() {
        write("src/commonMain/swift/Kept.swift", "public enum Kept {}")
        val removed = write("src/commonMain/swift/Removed.swift", "public enum Removed {}")

        build("processSwiftSourcesIosSimulatorArm64")
        removed.delete()
        build("processSwiftSourcesIosSimulatorArm64")

        assertEquals(setOf("Kept.swift"), bundledFileNames())
    }

    @Test
    fun `registers a task for each framework binary`() {
        val result = build("tasks", "--all")

        assertTrue(result.output.contains("processSwiftSourcesIosSimulatorArm64"))
        assertTrue(result.output.contains("unpackSwiftSourcesDebugFrameworkIosSimulatorArm64"))
        assertTrue(result.output.contains("unpackSwiftSourcesReleaseFrameworkIosSimulatorArm64"))
    }

    @Test
    fun `registers no task when bundling is disabled`() {
        buildFileWith(
            """
            swiftCodeBundling {
                enabled.set(false)
            }
            """.trimIndent(),
        )
        write("src/commonMain/swift/Common.swift", "public enum Common {}")

        val result = build("tasks", "--all")

        assertFalse(result.output.contains("processSwiftSourcesIosSimulatorArm64"))
    }
}
