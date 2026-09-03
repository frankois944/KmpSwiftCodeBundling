package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledSwiftModuleMapTest {
    private val kotlinModulemap =
        """
        framework module "ExampleKit" {
            umbrella header "ExampleKit.h"

            export *
            module * { export * }
        }
        """.trimIndent()

    private fun withModule(modulemap: String) =
        BundledSwiftModuleMap.withBundledModule(modulemap, "ExampleKit", "ExampleKit-Swift.h")

    @Test
    fun `declares the swift submodule`() {
        val result = withModule(kotlinModulemap)

        assertTrue(result.contains("module ExampleKit.Swift {"))
        assertTrue(result.contains("""header "ExampleKit-Swift.h""""))
        assertTrue(result.contains("requires objc"))
    }

    @Test
    fun `keeps the kotlin declaration untouched`() {
        assertTrue(withModule(kotlinModulemap).startsWith(kotlinModulemap))
    }

    @Test
    fun `applying it twice does not stack declarations`() {
        // The framework is relinked on every build, so this runs again over its own output.
        val once = withModule(kotlinModulemap)
        val twice = withModule(once)

        assertEquals(once, twice)
        assertEquals(1, twice.split(BundledSwiftModuleMap.MARKER_BEGIN).size - 1)
    }

    @Test
    fun `removing gives back the original module map`() {
        assertEquals(kotlinModulemap + "\n", BundledSwiftModuleMap.withoutBundledModule(withModule(kotlinModulemap)))
    }

    @Test
    fun `removing leaves a module map that never had the declaration alone`() {
        assertEquals(kotlinModulemap, BundledSwiftModuleMap.withoutBundledModule(kotlinModulemap))
    }

    @Test
    fun `removing survives a truncated declaration`() {
        val truncated = kotlinModulemap + "\n\n" + BundledSwiftModuleMap.MARKER_BEGIN + "\nmodule ExampleKit.Swift {"

        assertFalse(BundledSwiftModuleMap.withoutBundledModule(truncated).contains(BundledSwiftModuleMap.MARKER_BEGIN))
    }
}
