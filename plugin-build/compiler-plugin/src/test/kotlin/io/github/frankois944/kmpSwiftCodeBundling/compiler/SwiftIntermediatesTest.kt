package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SwiftIntermediatesTest {
    @JvmField
    @Rule
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `base name keeps the dots of an unpacked source name`() {
        // Cutting at the first dot instead wipes every object file on every build.
        assertEquals("bundled.origin.Greeter", SwiftIntermediates.baseName("bundled.origin.Greeter.o"))
        assertEquals("bundled.origin.Greeter", SwiftIntermediates.baseName("bundled.origin.Greeter.swiftdeps"))
        assertEquals(
            "bundled.origin.Greeter",
            SwiftIntermediates.baseName("bundled.origin.Greeter~partial.swiftmodule"),
        )
    }

    @Test
    fun `base name handles a plain name`() {
        assertEquals("Greeter", SwiftIntermediates.baseName("Greeter.o"))
    }

    @Test
    fun `keeps the outputs of sources that still exist`() {
        val directory = temporaryFolder.newFolder("objects")
        val kept = directory.resolve("bundled.origin.Greeter.o").apply { writeText("kept") }
        val keptDeps = directory.resolve("bundled.origin.Greeter.swiftdeps").apply { writeText("kept") }

        SwiftIntermediates.deleteStale(directory, setOf("bundled.origin.Greeter"))

        assertTrue(kept.exists())
        assertTrue(keptDeps.exists())
    }

    @Test
    fun `deletes the outputs of sources that disappeared`() {
        val directory = temporaryFolder.newFolder("objects")
        val stale = directory.resolve("bundled.origin.Removed.o").apply { writeText("stale") }
        val kept = directory.resolve("bundled.origin.Greeter.o").apply { writeText("kept") }

        SwiftIntermediates.deleteStale(directory, setOf("bundled.origin.Greeter"))

        assertFalse("a deleted source would stay in the framework", stale.exists())
        assertTrue(kept.exists())
    }

    @Test
    fun `tolerates a missing directory`() {
        SwiftIntermediates.deleteStale(temporaryFolder.root.resolve("absent"), setOf("anything"))
    }
}
