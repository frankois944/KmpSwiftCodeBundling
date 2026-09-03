package io.github.frankois944.kmpSwiftCodeBundling.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compiler plugin is published once per Kotlin version; picking the wrong one fails inside the
 * Kotlin/Native compiler, far from the cause.
 */
class CompilerPluginArtifactTest {
    private fun artifactFor(kotlinVersion: String) =
        CompilerPluginArtifact.dependencyNotation(kotlinVersion).substringBeforeLast(":").substringAfterLast(":")

    @Test
    fun `maps each supported minor to its own artifact`() {
        assertEquals("compiler-plugin-kotlin-2.2", artifactFor("2.2.21"))
        assertEquals("compiler-plugin-kotlin-2.3", artifactFor("2.3.0"))
        assertEquals("compiler-plugin-kotlin-2.4", artifactFor("2.4.10"))
    }

    @Test
    fun `ignores the patch and any qualifier`() {
        assertEquals("compiler-plugin-kotlin-2.3", artifactFor("2.3.21"))
        assertEquals("compiler-plugin-kotlin-2.4", artifactFor("2.4.0-RC2"))
        assertEquals("compiler-plugin-kotlin-2.2", artifactFor("2.2.0-Beta1"))
    }

    @Test
    fun `falls back to the newest known variant for later versions`() {
        // Failing on the day a new Kotlin is released would be worse than trying.
        assertEquals("compiler-plugin-kotlin-2.4", artifactFor("2.5.0"))
        assertEquals("compiler-plugin-kotlin-2.4", artifactFor("3.0.0"))
    }

    @Test
    fun `carries the group and version of the gradle plugin`() {
        val notation = CompilerPluginArtifact.dependencyNotation("2.4.10")

        assertTrue(notation.startsWith("io.github.frankois944.kmpSwiftCodeBundling:"))
        assertEquals(3, notation.split(":").size)
    }

    @Test
    fun `rejects a kotlin version older than the plugin supports`() {
        val error = runCatching { CompilerPluginArtifact.dependencyNotation("2.1.21") }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("2.2 or newer"))
    }

    @Test
    fun `rejects a version it cannot read`() {
        assertTrue(runCatching { CompilerPluginArtifact.dependencyNotation("unknown") }.isFailure)
    }
}
