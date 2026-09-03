package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FrameworkLayoutTest {
    private val directory = File("/build/bin/ExampleKit.framework")

    @Test
    fun `reads the framework name from the directory`() {
        assertEquals("ExampleKit", FrameworkLayout(directory, isMacosFramework = false).frameworkName)
    }

    @Test
    fun `a non macOS framework is flat`() {
        val layout = FrameworkLayout(directory, isMacosFramework = false)

        assertEquals(File("/build/bin/ExampleKit.framework/Headers"), layout.headersDirectory)
        assertEquals(File("/build/bin/ExampleKit.framework/Modules/module.modulemap"), layout.modulemapFile)
    }

    @Test
    fun `a macOS framework is a versioned bundle`() {
        val layout = FrameworkLayout(directory, isMacosFramework = true)

        assertEquals(File("/build/bin/ExampleKit.framework/Versions/A/Headers"), layout.headersDirectory)
        assertEquals(File("/build/bin/ExampleKit.framework/Versions/A/Modules/module.modulemap"), layout.modulemapFile)
    }

    @Test
    fun `the copy given to swiftc stays flat even for macOS`() {
        // It is a private copy, not a bundle anyone loads.
        val layout = FrameworkLayout(directory, isMacosFramework = true, isFlat = true)

        assertEquals(File("/build/bin/ExampleKit.framework/Headers"), layout.headersDirectory)
    }

    @Test
    fun `swift module artifacts are named after the target triple`() {
        val layout = FrameworkLayout(directory, isMacosFramework = false)
        val triple = TargetTriple("arm64", "apple", "ios", "simulator")

        val expected =
            "/build/bin/ExampleKit.framework/Modules/ExampleKit.swiftmodule/" +
                "arm64-apple-ios-simulator.swiftmodule"

        assertEquals(File(expected), layout.swiftModuleArtifact(triple, "swiftmodule"))
    }
}
