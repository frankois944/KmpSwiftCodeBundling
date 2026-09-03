package io.github.frankois944.kmpSwiftCodeBundling.internal

import java.io.File
import java.io.Serializable

/**
 * The parts of a `.framework` bundle this plugin writes to.
 *
 * A macOS framework is a versioned bundle, every other Apple platform is flat. This mirrors the
 * layout used by the compiler plugin; the two cannot share code because that module must stay free
 * of Gradle classes.
 */
internal class FrameworkPaths(
    val directory: File,
    isMacosFramework: Boolean,
) : Serializable {
    val name: String = directory.name.removeSuffix(".framework")

    private val contentDirectory: File =
        if (isMacosFramework) directory.resolve("Versions/A") else directory

    val headersDirectory: File get() = contentDirectory.resolve("Headers")

    val modulesDirectory: File get() = contentDirectory.resolve("Modules")

    val swiftHeader: File get() = headersDirectory.resolve("$name-Swift.h")

    val modulemapFile: File get() = modulesDirectory.resolve("module.modulemap")

    val swiftModuleDirectory: File get() = modulesDirectory.resolve("$name.swiftmodule")

    fun swiftModuleArtifacts(targetTriple: String): List<File> =
        SWIFT_MODULE_EXTENSIONS.map { swiftModuleDirectory.resolve("$targetTriple.$it") }

    private companion object {
        private const val serialVersionUID: Long = 1L

        val SWIFT_MODULE_EXTENSIONS =
            listOf("swiftmodule", "swiftdoc", "swiftsourceinfo", "abi.json", "swiftinterface", "private.swiftinterface")
    }
}
