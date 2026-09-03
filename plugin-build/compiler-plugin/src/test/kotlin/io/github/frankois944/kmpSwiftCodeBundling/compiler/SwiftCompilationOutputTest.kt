package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SwiftCompilationOutputTest {
    private val moduleDirectory = File("/work/module")
    private val objectDirectory = File("/work/objects")
    private val units =
        listOf(
            SwiftSourceUnit("a/bundled.origin.Greeter.swift", "bundled.origin.Greeter"),
            SwiftSourceUnit("b/bundled.origin.Other.swift", "bundled.origin.Other"),
        )

    @Test
    fun `debug produces one object per source, which is what makes it incremental`() {
        val output = SwiftCompilationOutput.of("ExampleKit", moduleDirectory, objectDirectory, units, isDebug = true)

        assertEquals(
            listOf(
                File("/work/objects/bundled.origin.Greeter.o"),
                File("/work/objects/bundled.origin.Other.o"),
            ),
            output.objectFiles,
        )
    }

    @Test
    fun `release produces a single object for the whole module`() {
        val output = SwiftCompilationOutput.of("ExampleKit", moduleDirectory, objectDirectory, units, isDebug = false)

        assertEquals(listOf(File("/work/objects/ExampleKit.o")), output.objectFiles)
    }

    @Test
    fun `module artifacts are named after the module`() {
        val output = SwiftCompilationOutput.of("ExampleKit", moduleDirectory, objectDirectory, units, isDebug = true)

        assertEquals(File("/work/module/ExampleKit.swiftmodule"), output.swiftModule)
        assertEquals(File("/work/module/ExampleKit-Swift.h"), output.swiftHeader)
        assertEquals(File("/work/module/ExampleKit.private.swiftinterface"), output.privateSwiftInterface)
    }

    @Test
    fun `source units are relative to the unpacked sources directory`() {
        val root = File("/work/swift")
        val units = SwiftSourceUnit.from(listOf(File("/work/swift/origin/bundled.origin.Greeter.swift")), root)

        assertEquals("origin/bundled.origin.Greeter.swift", units.single().relativePath)
        assertEquals("bundled.origin.Greeter", units.single().baseName)
    }
}
