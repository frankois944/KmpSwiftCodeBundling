package io.github.frankois944.kmpSwiftCodeBundling.compiler

import java.io.File

/**
 * One Swift source as `swiftc` sees it.
 *
 * The path is relative to the working directory the compiler is run in, and the output file map is
 * keyed by the very same string — that is what lets the compiler match a source to its outputs.
 */
data class SwiftSourceUnit(
    val relativePath: String,
    val baseName: String,
) {
    companion object {
        fun from(
            sources: List<File>,
            root: File,
        ): List<SwiftSourceUnit> =
            sources.map { source ->
                SwiftSourceUnit(
                    relativePath = source.relativeTo(root).path,
                    baseName = source.nameWithoutExtension,
                )
            }
    }
}

/** Everything `swiftc` produces, before any of it is installed into the framework. */
class SwiftCompilationOutput(
    val objectFiles: List<File>,
    val swiftModule: File,
    val swiftDoc: File,
    val swiftSourceInfo: File,
    val abiJson: File,
    val swiftInterface: File,
    val privateSwiftInterface: File,
    val swiftHeader: File,
) {
    companion object {
        /**
         * A debug build produces one object file per source, which is what makes it incremental; a
         * release build compiles the whole module into a single one.
         */
        fun of(
            moduleName: String,
            moduleDirectory: File,
            objectDirectory: File,
            units: List<SwiftSourceUnit>,
            isDebug: Boolean,
        ): SwiftCompilationOutput =
            SwiftCompilationOutput(
                objectFiles =
                    if (isDebug) {
                        units.map { objectDirectory.resolve("${it.baseName}.o") }
                    } else {
                        listOf(objectDirectory.resolve("$moduleName.o"))
                    },
                swiftModule = moduleDirectory.resolve("$moduleName.swiftmodule"),
                swiftDoc = moduleDirectory.resolve("$moduleName.swiftdoc"),
                swiftSourceInfo = moduleDirectory.resolve("$moduleName.swiftsourceinfo"),
                abiJson = moduleDirectory.resolve("$moduleName.abi.json"),
                swiftInterface = moduleDirectory.resolve("$moduleName.swiftinterface"),
                privateSwiftInterface = moduleDirectory.resolve("$moduleName.private.swiftinterface"),
                swiftHeader = moduleDirectory.resolve("$moduleName-Swift.h"),
            )
    }
}
