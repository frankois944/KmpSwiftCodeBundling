package io.github.frankois944.kmpSwiftCodeBundling.compiler

import java.io.File

/**
 * Paths inside a `.framework` bundle. macOS frameworks are versioned bundles, every other Apple
 * platform uses a flat one.
 */
internal class FrameworkLayout(
    val frameworkDirectory: File,
    isMacosFramework: Boolean,
    isFlat: Boolean = false,
) {
    val frameworkName: String = frameworkDirectory.name.removeSuffix(".framework")

    val parentDirectory: File = frameworkDirectory.parentFile

    private val contentDirectory: File =
        if (isMacosFramework && !isFlat) frameworkDirectory.resolve("Versions/A") else frameworkDirectory

    val headersDirectory: File get() = contentDirectory.resolve("Headers")

    val modulesDirectory: File get() = contentDirectory.resolve("Modules")

    val kotlinHeader: File get() = headersDirectory.resolve("$frameworkName.h")

    val apiNotes: File get() = headersDirectory.resolve("$frameworkName.apinotes")

    val swiftHeader: File get() = headersDirectory.resolve("$frameworkName-Swift.h")

    val modulemapFile: File get() = modulesDirectory.resolve("module.modulemap")

    val swiftModuleDirectory: File get() = modulesDirectory.resolve("$frameworkName.swiftmodule")

    fun swiftModuleArtifact(
        targetTriple: TargetTriple,
        extension: String,
    ): File = swiftModuleDirectory.resolve("$targetTriple.$extension")
}
