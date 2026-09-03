package io.github.frankois944.kmpSwiftCodeBundling.compiler

import java.io.File

/** The per-source files `swiftc` leaves in the object directory between builds. */
object SwiftIntermediates {
    /**
     * The source a file in the object directory belongs to.
     *
     * Only the last extension is stripped: unpacked sources are named `bundled.<origin>.<Source>`,
     * so cutting at the first dot would match nothing, every object file would be deleted on every
     * build, and the Swift driver would report "Missing an output" and recompile everything.
     */
    fun baseName(fileName: String): String = fileName.substringBeforeLast(".").removeSuffix("~partial")

    /**
     * Removes the outputs of sources that no longer exist.
     *
     * They would otherwise keep being handed to the linker, so a deleted Swift file would stay in
     * the framework until the build directory is wiped.
     */
    fun deleteStale(
        objectDirectory: File,
        expectedBaseNames: Set<String>,
    ) {
        objectDirectory
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .filterNot { baseName(it.name) in expectedBaseNames }
            .forEach { it.delete() }
    }
}
