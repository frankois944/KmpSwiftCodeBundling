package io.github.frankois944.kmpSwiftCodeBundling.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DirectorySyncTest {
    @JvmField
    @Rule
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    private fun file(
        root: File,
        path: String,
        content: String,
    ): File =
        root.resolve(path).apply {
            parentFile.mkdirs()
            writeText(content)
        }

    @Test
    fun `copies files and nested directories`() {
        val origin = temporaryFolder.newFolder("origin")
        val destination = temporaryFolder.newFolder("destination")

        file(origin, "a.swift", "a")
        file(origin, "nested/b.swift", "b")

        origin.toPath().syncDirectoryContentIfDifferent(destination.toPath())

        assertEquals("a", destination.resolve("a.swift").readText())
        assertEquals("b", destination.resolve("nested/b.swift").readText())
    }

    @Test
    fun `leaves identical files untouched so the swift compiler stays incremental`() {
        val origin = temporaryFolder.newFolder("origin")
        val destination = temporaryFolder.newFolder("destination")

        file(origin, "a.swift", "a")
        origin.toPath().syncDirectoryContentIfDifferent(destination.toPath())

        val copied = destination.resolve("a.swift")
        copied.setLastModified(0)

        origin.toPath().syncDirectoryContentIfDifferent(destination.toPath())

        assertEquals(0, copied.lastModified())
    }

    @Test
    fun `rewrites a file whose content changed`() {
        val origin = temporaryFolder.newFolder("origin")
        val destination = temporaryFolder.newFolder("destination")

        file(origin, "a.swift", "a")
        origin.toPath().syncDirectoryContentIfDifferent(destination.toPath())

        file(origin, "a.swift", "changed")
        origin.toPath().syncDirectoryContentIfDifferent(destination.toPath())

        assertEquals("changed", destination.resolve("a.swift").readText())
    }

    @Test
    fun `removes files and directories that disappeared from the origin`() {
        val origin = temporaryFolder.newFolder("origin")
        val destination = temporaryFolder.newFolder("destination")

        file(origin, "kept.swift", "kept")
        file(origin, "gone.swift", "gone")
        file(origin, "goneDir/inside.swift", "inside")
        origin.toPath().syncDirectoryContentIfDifferent(destination.toPath())

        origin.resolve("gone.swift").delete()
        origin.resolve("goneDir").deleteRecursively()
        origin.toPath().syncDirectoryContentIfDifferent(destination.toPath())

        assertTrue(destination.resolve("kept.swift").exists())
        assertFalse(destination.resolve("gone.swift").exists())
        assertFalse(destination.resolve("goneDir").exists())
    }

    @Test
    fun `deleteEmptyDirectoriesRecursively keeps only directories holding files`() {
        val root = temporaryFolder.newFolder("root")

        file(root, "full/a.swift", "a")
        root.resolve("empty/nested/deeper").mkdirs()

        root.deleteEmptyDirectoriesRecursively()

        assertTrue(root.resolve("full/a.swift").exists())
        assertFalse(root.resolve("empty").exists())
    }
}
