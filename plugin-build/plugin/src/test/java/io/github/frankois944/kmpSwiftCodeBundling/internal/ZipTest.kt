package io.github.frankois944.kmpSwiftCodeBundling.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ZipTest {
    @JvmField
    @Rule
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `isKlib recognises klib archives only`() {
        assertTrue(File("example.klib").isKlib)
        assertFalse(File("example.jar").isKlib)
        assertFalse(File("example").isKlib)
    }

    @Test
    fun `writeToZip adds entries to an existing archive`() {
        val archive = temporaryFolder.newFile("example.klib")

        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("default/manifest"))
            zip.write("existing".toByteArray())
            zip.closeEntry()
        }

        archive.writeToZip { fileSystem ->
            val directory = fileSystem.getPath("/default/swift-code-bundling/swift")
            directory.createDirectories()
            directory.resolve("Greeter.swift").writeText("public enum Greeter {}")
        }

        assertEquals(
            "public enum Greeter {}",
            entries(archive)["default/swift-code-bundling/swift/Greeter.swift"],
        )
        assertEquals("existing", entries(archive)["default/manifest"])
    }

    private fun entries(archive: File): Map<String, String> =
        buildMap {
            ZipInputStream(archive.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break

                    if (!entry.isDirectory) {
                        put(entry.name, zip.readBytes().decodeToString())
                    }
                }
            }
        }
}
