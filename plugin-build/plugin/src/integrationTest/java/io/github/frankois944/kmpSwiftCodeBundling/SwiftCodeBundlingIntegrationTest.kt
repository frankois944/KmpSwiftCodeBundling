package io.github.frankois944.kmpSwiftCodeBundling

import io.github.frankois944.kmpSwiftCodeBundling.AppleFrameworkProject.Companion.assumeApplePlatform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * End-to-end tests: they link real Apple frameworks and inspect what came out.
 *
 * Skipped on hosts without macOS and Xcode. Run them with `./gradlew -p plugin-build integrationTest`.
 */
class SwiftCodeBundlingIntegrationTest {
    @JvmField
    @Rule
    var projectDir: TemporaryFolder = TemporaryFolder()

    private lateinit var project: AppleFrameworkProject

    @Before
    fun setUp() {
        assumeApplePlatform()
        project = AppleFrameworkProject(projectDir.root)
    }

    private fun writeGreeter(name: String = "Greeter") =
        project.write(
            "src/commonMain/swift/$name.swift",
            """
            import Foundation

            public enum $name {
                public static func greet() -> String { "hello from $name" }
            }
            """,
        )

    @Test
    fun `links the bundled swift into the framework`() {
        project.singleModule()
        writeGreeter()

        project.link()

        assertTrue("Swift symbol missing from the framework binary", project.frameworkBinary.containsSymbol("Greeter"))
        assertTrue(project.swiftModuleDirectory.resolve("arm64-apple-ios-simulator.swiftmodule").exists())
        assertTrue(
            project.frameworkDirectory
                .resolve("Modules/module.modulemap")
                .readText()
                .contains("module ${AppleFrameworkProject.FRAMEWORK_NAME}.Swift"),
        )
    }

    @Test
    fun `swift can call the kotlin api of its own module`() {
        project.singleModule()
        project.write(
            "src/commonMain/kotlin/Greeting.kt",
            """
            class Greeting {
                fun greet(name: String): String = "Hello, ${'$'}name!"
            }
            """,
        )
        project.write(
            "src/commonMain/swift/KotlinCaller.swift",
            """
            import Foundation

            public enum KotlinCaller {
                public static func greet(_ name: String) -> String {
                    Greeting().greet(name: name)
                }
            }
            """,
        )

        project.link()

        assertTrue(project.frameworkBinary.containsSymbol("KotlinCaller"))
    }

    @Test
    fun `bundles swift that travelled through a dependency klib`() {
        project.withLibraryModule()
        project.write(
            "library/src/commonMain/swift/LibraryHelper.swift",
            """
            import Foundation

            public enum LibraryHelper {
                public static func help() -> String { "from the library module" }
            }
            """,
        )
        project.write("library/src/commonMain/kotlin/Library.kt", "class Library")

        project.link()

        assertTrue(
            "Swift bundled in a dependency module never reached the framework",
            project.frameworkBinary.containsSymbol("LibraryHelper"),
        )
    }

    @Test
    fun `links the bundled swift into a static framework`() {
        project.singleModule(isStatic = true)
        writeGreeter()

        project.link()

        assertTrue("expected a static archive", project.frameworkBinary.readBytes().take(7).toByteArray().decodeToString() == "!<arch>")
        assertTrue(project.frameworkBinary.containsSymbol("Greeter"))
    }

    @Test
    fun `emits swift interfaces when library evolution is enabled`() {
        project.singleModule(pluginConfiguration = "enableSwiftLibraryEvolution.set(true)")
        writeGreeter()

        project.link()

        val swiftInterface = project.swiftModuleDirectory.resolve("arm64-apple-ios-simulator.swiftinterface")

        assertTrue(swiftInterface.exists())
        assertTrue(swiftInterface.readText().contains("public static func greet()"))
    }

    @Test
    fun `recompiles only the swift file that changed`() {
        // Asserted on the Swift driver's own decisions rather than on file timestamps: with batch
        // mode several files share one frontend job, so an object file can be rewritten without its
        // source having been considered out of date.
        project.singleModule(pluginConfiguration = """freeSwiftCompilerArgs.set(listOf("-driver-show-incremental"))""")
        val changed = writeGreeter("Changing")
        project.write(
            "src/commonMain/swift/Untouched.swift",
            """
            import Foundation

            public enum Untouched {
                public static func value() -> Int { 1 }
            }
            """,
        )

        project.link()

        // A body-only change: nothing else in the module can depend on it.
        changed.writeText(changed.readText().replace("hello from Changing", "bonjour from Changing"))

        project.link()

        val remarks = project.swiftcLog.readLines().filter { it.contains("Incremental compilation") }
        val report = remarks.joinToString("\n").ifEmpty { "the driver reported nothing about incremental compilation" }

        assertTrue(
            "the incremental build did not run at all:\n$report",
            remarks.isNotEmpty(),
        )
        assertFalse(
            "an output was declared in the output file map that the compiler never writes, " +
                "so every file was requeued:\n$report",
            report.contains("Missing an output"),
        )
        assertTrue(
            "the changed file should have been recompiled:\n$report",
            remarks.any { it.contains("Queuing") && it.contains("Changing") },
        )
        assertTrue(
            "an unrelated file was recompiled, so the build is not incremental:\n$report",
            remarks.none { it.contains("Queuing") && it.contains("Untouched") },
        )
    }
}
