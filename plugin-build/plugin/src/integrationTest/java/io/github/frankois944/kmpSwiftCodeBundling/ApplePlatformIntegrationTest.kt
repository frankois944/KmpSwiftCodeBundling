package io.github.frankois944.kmpSwiftCodeBundling

import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.assumeApplePlatform
import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.assumeSdkInstalled
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The same bundling, on every Apple platform.
 *
 * Simulator targets are used for watchOS and tvOS so that nothing needs code signing, and each test
 * is skipped when its SDK is not installed in the current Xcode.
 */
class ApplePlatformIntegrationTest {
    @JvmField
    @Rule
    var projectDir: TemporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        assumeApplePlatform()
    }

    private fun bundleSwiftInto(project: AppleFrameworkProject) {
        project.singleModule()
        project.write(
            "src/commonMain/swift/PlatformGreeter.swift",
            """
            import Foundation

            public enum PlatformGreeter {
                public static func greet() -> String { "hello" }
            }
            """,
        )

        project.link()

        assertTrue("Swift symbol missing from the framework binary", project.frameworkBinary.containsSymbol("PlatformGreeter"))
        assertTrue("missing ${project.swiftModule.name}", project.swiftModule.exists())
        assertTrue(project.swiftHeader.exists())
        assertTrue(
            project.modulemapFile.readText().contains("module ${AppleFrameworkProject.FRAMEWORK_NAME}.Swift"),
        )
    }

    @Test
    fun `bundles swift into a macOS framework`() {
        assumeSdkInstalled("macosx")

        val project =
            AppleFrameworkProject(
                projectDir.root,
                target = "macosArm64",
                targetTriple = "arm64-apple-macos",
                // The one platform whose framework is a versioned bundle, so the only test covering
                // the Versions/A branch of the framework layout.
                isVersionedBundle = true,
            )

        bundleSwiftInto(project)

        assertTrue(
            "a macOS framework must be a versioned bundle",
            project.frameworkDirectory.resolve("Versions/A").isDirectory,
        )
    }

    @Test
    fun `bundles swift into a watchOS framework`() {
        assumeSdkInstalled("watchsimulator")

        bundleSwiftInto(
            AppleFrameworkProject(
                projectDir.root,
                target = "watchosSimulatorArm64",
                targetTriple = "arm64-apple-watchos-simulator",
            ),
        )
    }

    @Test
    fun `bundles swift into a tvOS framework`() {
        assumeSdkInstalled("appletvsimulator")

        bundleSwiftInto(
            AppleFrameworkProject(
                projectDir.root,
                target = "tvosSimulatorArm64",
                targetTriple = "arm64-apple-tvos-simulator",
            ),
        )
    }
}
