package io.github.frankois944.kmpSwiftCodeBundling.internal

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `lipo` merges binaries only, and the fat framework's bundle is copied from a single input, so
 * without this step Swift refuses to import the framework for every architecture but one.
 */
class MergeBundledSwiftIntoFatFrameworkTest {
    @JvmField
    @Rule
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val task by lazy {
        ProjectBuilder
            .builder()
            .build()
            .tasks
            .register("merge")
            .get()
    }

    private val armTriple = "arm64-apple-ios-simulator"
    private val intelTriple = "x86_64-apple-ios-simulator"

    private fun framework(
        name: String,
        triple: String,
        swiftHeader: String = "// generated header",
    ): File {
        val directory = temporaryFolder.newFolder(name).resolve("ExampleKit.framework")
        val paths = FrameworkPaths(directory, isMacosFramework = false)

        paths.swiftModuleDirectory.mkdirs()
        paths.headersDirectory.mkdirs()

        listOf("swiftmodule", "swiftdoc", "swiftinterface").forEach {
            paths.swiftModuleDirectory.resolve("$triple.$it").writeText("$triple $it")
        }
        paths.swiftHeader.writeText(swiftHeader)
        paths.modulemapFile.writeText("framework module ExampleKit {}\n// BEGIN kmpSwiftCodeBundling\n")

        return directory
    }

    private fun merge(
        fat: File,
        vararg slices: Pair<File, Pair<String, String>>,
    ) {
        MergeBundledSwiftIntoFatFramework(
            fatFramework = fat,
            isMacosFramework = false,
            slices = slices.map { (dir, target) -> FatFrameworkSlice(dir, target.first, target.second) },
        ).execute(task)
    }

    @Test
    fun `brings in the swift module of every architecture`() {
        val arm = framework("arm", armTriple)
        val intel = framework("intel", intelTriple)
        val fat = temporaryFolder.newFolder("fat").resolve("ExampleKit.framework")

        merge(fat, arm to (armTriple to "__aarch64__"), intel to (intelTriple to "__x86_64__"))

        val modules = FrameworkPaths(fat, isMacosFramework = false).swiftModuleDirectory

        assertTrue(modules.resolve("$armTriple.swiftmodule").exists())
        assertTrue(modules.resolve("$intelTriple.swiftmodule").exists())
        assertTrue(modules.resolve("$armTriple.swiftinterface").exists())
        assertTrue(modules.resolve("$intelTriple.swiftdoc").exists())
    }

    @Test
    fun `copies a single swift header when the architectures agree`() {
        val arm = framework("arm", armTriple, swiftHeader = "same")
        val intel = framework("intel", intelTriple, swiftHeader = "same")
        val fat = temporaryFolder.newFolder("fat").resolve("ExampleKit.framework")

        merge(fat, arm to (armTriple to "__aarch64__"), intel to (intelTriple to "__x86_64__"))

        assertEquals("same", FrameworkPaths(fat, isMacosFramework = false).swiftHeader.readText())
    }

    @Test
    fun `guards the swift header by architecture when they differ`() {
        // `#if arch(...)` in the bundled Swift produces different declarations per architecture.
        val arm = framework("arm", armTriple, swiftHeader = "arm only")
        val intel = framework("intel", intelTriple, swiftHeader = "intel only")
        val fat = temporaryFolder.newFolder("fat").resolve("ExampleKit.framework")

        merge(fat, arm to (armTriple to "__aarch64__"), intel to (intelTriple to "__x86_64__"))

        val header = FrameworkPaths(fat, isMacosFramework = false).swiftHeader.readText()

        assertTrue(header.contains("#if defined(__aarch64__)"))
        assertTrue(header.contains("#elif defined(__x86_64__)"))
        assertTrue(header.contains("arm only"))
        assertTrue(header.contains("intel only"))
        assertTrue(header.contains("#error Unsupported architecture"))
    }

    @Test
    fun `does nothing when no slice carries bundled swift`() {
        val plain = temporaryFolder.newFolder("plain").resolve("ExampleKit.framework").apply { mkdirs() }
        val fat = temporaryFolder.newFolder("fat").resolve("ExampleKit.framework")

        merge(fat, plain to (armTriple to "__aarch64__"))

        assertFalse(FrameworkPaths(fat, isMacosFramework = false).swiftModuleDirectory.exists())
    }
}
