package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Swift driver silently falls back to a full rebuild when this map is wrong, so these tests pin
 * down both halves of the rule: the root entry has to be there, and nothing may be declared that the
 * compiler does not write.
 */
class SwiftOutputFileMapTest {
    private val moduleDirectory = File("/work/module")
    private val objectDirectory = File("/work/objects")
    private val units =
        listOf(
            SwiftSourceUnit("origin/bundled.origin.Greeter.swift", "bundled.origin.Greeter"),
            SwiftSourceUnit("origin/bundled.origin.Other.swift", "bundled.origin.Other"),
        )

    private fun debugMap() =
        SwiftOutputFileMap.content("ExampleKit", moduleDirectory, objectDirectory, units, isDebug = true)

    @Test
    fun `debug declares the build record on the root entry`() {
        // Without it: "Disabling incremental build: no build record path".
        assertTrue(debugMap().contains(""""": { "swift-dependencies": "/work/module/ExampleKit.swiftdeps" }"""))
    }

    @Test
    fun `debug gives every source its own object and dependency file`() {
        val map = debugMap()

        assertTrue(map.contains(""""object": "/work/objects/bundled.origin.Greeter.o""""))
        assertTrue(map.contains(""""swift-dependencies": "/work/objects/bundled.origin.Greeter.swiftdeps""""))
        assertTrue(map.contains(""""origin/bundled.origin.Other.swift""""))
    }

    @Test
    fun `debug declares nothing the compiler does not write`() {
        // Declaring these gives "Missing an output" and recompiles everything, every build.
        val map = debugMap()

        assertFalse(map.contains("~partial.swiftmodule"))
        assertFalse(map.contains(""""dependencies""""))
        assertFalse(map.contains("emit-module-dependencies"))
    }

    @Test
    fun `release declares one object and no per-file entry`() {
        val map = SwiftOutputFileMap.content("ExampleKit", moduleDirectory, objectDirectory, units, isDebug = false)

        assertTrue(map.contains(""""": { "object": "/work/objects/ExampleKit.o" }"""))
        assertFalse(map.contains("bundled.origin.Greeter"))
    }

    @Test
    fun `escapes quotes and backslashes in paths`() {
        val awkward = listOf(SwiftSourceUnit("a\"b\\c.swift", "quoted"))
        val map = SwiftOutputFileMap.content("ExampleKit", moduleDirectory, objectDirectory, awkward, isDebug = true)

        assertTrue(map.contains("\"a\\\"b\\\\c.swift\""))
    }

    @Test
    fun `is valid json shaped`() {
        val map = debugMap()

        assertTrue(map.trim().startsWith("{"))
        assertTrue(map.trim().endsWith("}"))
        assertEquals(units.size + 1, map.lines().count { it.trimStart().startsWith("\"") })
    }
}
