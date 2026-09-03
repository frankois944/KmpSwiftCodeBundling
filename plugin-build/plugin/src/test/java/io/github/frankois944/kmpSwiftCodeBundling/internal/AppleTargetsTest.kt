package io.github.frankois944.kmpSwiftCodeBundling.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A triple that does not match what the compiler plugin derives from the Kotlin/Native configurables
 * would put the Swift module under a name Swift never looks for — with no error anywhere.
 */
class AppleTargetsTest {
    @Test
    fun `covers every apple target the plugin can meet`() {
        val expected =
            setOf(
                "ios_arm64",
                "ios_x64",
                "ios_simulator_arm64",
                "watchos_arm32",
                "watchos_arm64",
                "watchos_device_arm64",
                "watchos_x64",
                "watchos_simulator_arm64",
                "tvos_arm64",
                "tvos_x64",
                "tvos_simulator_arm64",
                "macos_x64",
                "macos_arm64",
            )

        assertEquals(expected, appleTargets.keys)
    }

    @Test
    fun `triples are well formed`() {
        appleTargets.forEach { (name, target) ->
            val components = target.targetTriple.split("-")

            assertTrue("$name: expected 3 or 4 components", components.size in 3..4)
            assertEquals("$name: vendor must be apple", "apple", components[1])
        }
    }

    @Test
    fun `simulator targets are marked as such in the triple`() {
        appleTargets
            .filterKeys { it.contains("simulator") || it.endsWith("_x64") && !it.startsWith("macos") }
            .forEach { (name, target) ->
                assertTrue("$name should be a simulator triple", target.targetTriple.endsWith("-simulator"))
            }
    }

    @Test
    fun `only macOS targets report a versioned bundle`() {
        assertTrue(appleTargets.getValue("macos_arm64").isMacos)
        assertTrue(appleTargets.getValue("macos_x64").isMacos)
        assertFalse(appleTargets.getValue("ios_simulator_arm64").isMacos)
        assertFalse(appleTargets.getValue("watchos_arm64").isMacos)
    }

    @Test
    fun `architecture macros match the architecture in the triple`() {
        assertEquals("__aarch64__", appleTargets.getValue("ios_arm64").architectureClangMacro)
        assertEquals("__x86_64__", appleTargets.getValue("ios_x64").architectureClangMacro)
        assertEquals("__arm__", appleTargets.getValue("watchos_arm32").architectureClangMacro)
    }
}
