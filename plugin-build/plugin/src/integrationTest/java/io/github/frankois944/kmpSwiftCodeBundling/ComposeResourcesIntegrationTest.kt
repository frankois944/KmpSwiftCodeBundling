package io.github.frankois944.kmpSwiftCodeBundling

import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.COMPOSE_VERSION
import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.FRAMEWORK_NAME
import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.KOTLIN_VERSION
import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.assumeApplePlatform
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Links frameworks with the real Compose Multiplatform plugin and its resources applied next to
 * this one.
 *
 * Compose configures every `XCFrameworkTask` with a callback that itself calls
 * `kotlin.targets.configureEach`, and Gradle forbids that from inside another callback on the same
 * collection. Realizing the XCFramework tasks from within this plugin's own
 * `kotlin.targets.configureEach` therefore failed the configuration of any project that used both
 * Compose and an XCFramework - even `./gradlew help`.
 *
 * Both plugins are resolved from repositories, as in [SkieCoexistenceIntegrationTest]: Compose also
 * looks the Kotlin Gradle Plugin up from its own class loader.
 */
class ComposeResourcesIntegrationTest {
    @JvmField
    @Rule
    var projectDir: TemporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        assumeApplePlatform()
    }

    private fun composeProject(): AppleFrameworkProject =
        AppleFrameworkProject(
            projectDir.root,
            kotlinVersion = KOTLIN_VERSION,
            composeVersion = COMPOSE_VERSION,
        ).apply {
            composeModule()
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

    @Test
    fun `links the bundled swift into a framework using compose resources`() {
        val project = composeProject()

        project.link()

        assertTrue("Swift symbol missing from the framework binary", project.frameworkBinary.containsSymbol(GREETER))
        assertTrue(project.swiftModule.exists())
    }

    @Test
    fun `assembles an xcframework using compose resources`() {
        val project = composeProject()

        project.assembleXCFramework()

        val frameworks =
            project.xcFrameworkDirectory
                .walkTopDown()
                .filter { it.name == "$FRAMEWORK_NAME.framework" }
                .toList()
        assertTrue("no framework in ${project.xcFrameworkDirectory}", frameworks.isNotEmpty())

        frameworks.forEach { framework ->
            assertTrue(
                "Swift symbol missing from $framework",
                framework.resolve(FRAMEWORK_NAME).containsSymbol(GREETER),
            )
            // Forced library evolution: the textual interface is what survives the assembly.
            assertTrue(
                "no .swiftinterface in $framework",
                framework
                    .resolve("Modules/$FRAMEWORK_NAME.swiftmodule")
                    .listFiles()
                    .orEmpty()
                    .any { it.extension == "swiftinterface" },
            )
            // And Compose still got to put its resources in, which is what it configures the
            // XCFramework task for.
            assertTrue(
                "Compose resources missing from $framework",
                framework.walkTopDown().any { it.name == "strings.commonMain.cvr" },
            )
        }
    }

    private companion object {
        /** Distinctive on purpose: the assertions look for it as a substring of the binary. */
        const val GREETER = "ComposeResourcesGreeter"
    }
}
