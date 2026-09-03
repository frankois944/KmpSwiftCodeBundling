package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SwiftCompilerCommandTest {
    @JvmField
    @Rule
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val units =
        listOf(
            SwiftSourceUnit("origin/bundled.origin.Greeter.swift", "bundled.origin.Greeter"),
            SwiftSourceUnit("origin/bundled.origin.Other.swift", "bundled.origin.Other"),
        )

    private val output =
        SwiftCompilationOutput.of("ExampleKit", File("/work/module"), File("/work/objects"), units, isDebug = true)

    private fun options(
        swiftLibraryEvolution: Boolean = false,
        noClangModuleBreadcrumbsInStaticFrameworks: Boolean = false,
        relativeSourcePathsInDebugSymbols: Boolean = false,
        freeCompilerArgs: List<String> = emptyList(),
    ) = SwiftBundlingOptions(
        swiftSourcesDirectory = File("/work/swift"),
        workDirectory = File("/work"),
        swiftVersion = "5",
        swiftLibraryEvolution = swiftLibraryEvolution,
        noClangModuleBreadcrumbsInStaticFrameworks = noClangModuleBreadcrumbsInStaticFrameworks,
        relativeSourcePathsInDebugSymbols = relativeSourcePathsInDebugSymbols,
        freeCompilerArgs = freeCompilerArgs,
    )

    private fun arguments(
        options: SwiftBundlingOptions = options(),
        isDebug: Boolean = true,
        isStaticFramework: Boolean = false,
    ): List<String> =
        SwiftCompilerCommand.arguments(
            invocation =
                SwiftCompilerInvocation(
                    swiftcPath = "/toolchain/bin/swiftc",
                    moduleName = "ExampleKit",
                    frameworkSearchPath = "/work/kotlin-framework",
                    sdkPath = "/sdk",
                    target = "arm64-apple-ios14.0-simulator",
                    isDebug = isDebug,
                    isStaticFramework = isStaticFramework,
                ),
            options = options,
            output = output,
            outputFileMap = File("/work/config/output-file-map.json"),
            sourceList = File("/work/config/swift-sources.txt"),
            moduleCache = File("/work/module-cache"),
        )

    private fun List<String>.valueOf(flag: String): String? = getOrNull(indexOf(flag) + 1).takeIf { contains(flag) }

    @Test
    fun `runs the toolchain swiftc and passes the sources as a response file`() {
        val arguments = arguments()

        assertEquals("/toolchain/bin/swiftc", arguments.first())
        assertEquals("@/work/config/swift-sources.txt", arguments.last())
    }

    @Test
    fun `compiles as an overlay of the framework module`() {
        // Without this the bundled Swift cannot see the Kotlin API of its own module.
        val arguments = arguments()

        assertTrue(arguments.contains("-import-underlying-module"))
        assertEquals("/work/kotlin-framework", arguments.valueOf("-F"))
        assertEquals("ExampleKit", arguments.valueOf("-module-name"))
    }

    @Test
    fun `passes the target, sdk and swift version through`() {
        val arguments = arguments()

        assertEquals("arm64-apple-ios14.0-simulator", arguments.valueOf("-target"))
        assertEquals("/sdk", arguments.valueOf("-sdk"))
        assertEquals("5", arguments.valueOf("-swift-version"))
    }

    @Test
    fun `debug builds incrementally, release compiles the whole module`() {
        val debug = arguments(isDebug = true)

        assertTrue(debug.contains("-incremental"))
        assertTrue(debug.contains("-enable-batch-mode"))
        assertTrue(debug.contains("-Onone"))
        assertTrue(debug.any { it.startsWith("-j") })
        assertFalse(debug.contains("-whole-module-optimization"))

        val release = arguments(isDebug = false)

        assertTrue(release.contains("-whole-module-optimization"))
        assertTrue(release.contains("-O"))
        assertFalse(release.contains("-incremental"))
    }

    @Test
    fun `library evolution emits and verifies the interfaces`() {
        val without = arguments()
        assertFalse(without.contains("-enable-library-evolution"))

        val with = arguments(options(swiftLibraryEvolution = true))

        assertTrue(with.contains("-enable-library-evolution"))
        assertTrue(with.contains("-verify-emitted-module-interface"))
        assertTrue(with.contains("-emit-module-interface-path"))
        assertTrue(with.contains("-emit-private-module-interface-path"))
    }

    @Test
    fun `clang module breadcrumbs are only dropped for a static framework`() {
        val options = options(noClangModuleBreadcrumbsInStaticFrameworks = true)

        assertFalse(arguments(options, isStaticFramework = false).contains("-no-clang-module-breadcrumbs"))
        assertTrue(arguments(options, isStaticFramework = true).contains("-no-clang-module-breadcrumbs"))
    }

    @Test
    fun `relative source paths anchor the debug symbols to the working directory`() {
        assertFalse(arguments().contains("-file-compilation-dir"))
        assertEquals(".", arguments(options(relativeSourcePathsInDebugSymbols = true)).valueOf("-file-compilation-dir"))
    }

    @Test
    fun `free compiler arguments come last, before the sources`() {
        val arguments = arguments(options(freeCompilerArgs = listOf("-warnings-as-errors")))

        assertEquals(arguments.size - 2, arguments.indexOf("-warnings-as-errors"))
    }

    @Test
    fun `writes the source list quoted, one per line`() {
        val destination = temporaryFolder.newFile("swift-sources.txt")

        SwiftCompilerCommand.writeSourceList(units, destination)

        assertEquals(
            listOf("'origin/bundled.origin.Greeter.swift'", "'origin/bundled.origin.Other.swift'"),
            destination.readLines(),
        )
    }
}
