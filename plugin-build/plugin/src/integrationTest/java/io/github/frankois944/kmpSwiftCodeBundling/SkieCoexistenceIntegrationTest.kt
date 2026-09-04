package io.github.frankois944.kmpSwiftCodeBundling

import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.KOTLIN_VERSION
import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.SKIE_VERSION
import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.assumeApplePlatform
import org.gradle.testkit.runner.BuildResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Links frameworks with the real SKIE plugin applied next to this one.
 *
 * The unit and functional tests drive the detection with a stub carrying SKIE's id and the shape of
 * its extension. That proves the detection reads what it is given, not that SKIE still looks like
 * that: this applies SKIE for real, from the Gradle Plugin Portal, and then inspects the framework
 * both plugins were pointed at.
 *
 * SKIE only does anything on macOS, so these are skipped off it like the other integration tests.
 * Both plugins are resolved from repositories rather than from the classpath TestKit injects: SKIE
 * cannot recognise an injected Kotlin Gradle Plugin, and would silently do nothing.
 */
class SkieCoexistenceIntegrationTest {
    @JvmField
    @Rule
    var projectDir: TemporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        assumeApplePlatform()
    }

    /**
     * A project with Swift in `src/commonMain/swift` - the directory *both* plugins read - and SKIE
     * applied on top of this plugin.
     */
    private fun projectWithSkie(skieConfiguration: String = ""): AppleFrameworkProject =
        AppleFrameworkProject(
            projectDir.root,
            kotlinVersion = KOTLIN_VERSION,
            skieVersion = SKIE_VERSION,
        ).apply {
            singleModule(skieConfiguration = skieConfiguration)
            write(
                "src/commonMain/swift/$GREETER.swift",
                """
                import Foundation

                public enum $GREETER {
                    public static func greet() -> String { "hello from $GREETER" }
                }
                """,
            )
        }

    /**
     * SKIE logs and gives up rather than failing the build when it cannot load, and a SKIE that did
     * nothing would make every assertion here pass for the wrong reason. Its own task in the graph
     * is the proof that it did something.
     */
    private fun BuildResult.assertSkieRan() {
        // Any task of its own will do - SKIE names them `skie<Something><Target>`, and which ones
        // it registers is its business.
        assertTrue("SKIE never configured itself:\n$output", output.contains("> Task :skie"))
    }

    @Test
    fun `stands down and lets skie bundle the swift`() {
        val project = projectWithSkie()

        val result = project.link()

        result.assertSkieRan()
        assertTrue("no warning about SKIE", result.output.contains("Swift code bundling is disabled in"))
        assertFalse("this plugin compiled Swift anyway", project.swiftcLog.exists())
        // Nothing is lost by standing down here: SKIE reads src/<sourceSet>/swift itself.
        assertTrue("SKIE did not bundle the Swift", project.frameworkBinary.containsSymbol(GREETER))
    }

    @Test
    fun `stands down even when skie bundles nothing, and then nobody does`() {
        // The documented trap: turning SKIE's own bundling off does not hand the framework over,
        // because SKIE still compiles the Swift it generates into the same overlay module. Nobody
        // bundles the Swift sources, and that is the outcome to be sure of - it is the reason the
        // warning tells the user to disable SKIE itself rather than its bundling.
        val project = projectWithSkie("swiftBundling { enabled.set(false) }")

        val result = project.link()

        result.assertSkieRan()
        assertTrue("no warning about SKIE", result.output.contains("Its own Swift code bundling is off"))
        assertFalse("this plugin compiled Swift anyway", project.swiftcLog.exists())
        assertFalse("the Swift reached the framework after all", project.frameworkBinary.containsSymbol(GREETER))
    }

    @Test
    fun `takes over when skie is disabled`() {
        val project = projectWithSkie("isEnabled.set(false)")

        val result = project.link()

        // SKIE registers nothing when disabled, so the only thing to check on its side is that it
        // got far enough to read its own configuration.
        assertFalse("SKIE failed to load:\n${result.output}", result.output.contains("could not infer Kotlin"))
        assertFalse("stood down from an inactive SKIE", result.output.contains("Swift code bundling is disabled in"))
        assertTrue("this plugin did not compile the Swift", project.swiftcLog.exists())
        assertTrue("Swift symbol missing from the framework binary", project.frameworkBinary.containsSymbol(GREETER))
        assertTrue(project.swiftModule.exists())
    }

    private companion object {
        /** Distinctive on purpose: the assertions look for it as a substring of the binary. */
        const val GREETER = "SkieCoexistenceGreeter"
    }
}
