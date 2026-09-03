package io.github.frankois944.kmpSwiftCodeBundling.compiler

import java.io.File

/**
 * Writes that leave the file alone when nothing changed.
 *
 * The Swift compiler decides what to recompile from timestamps, so rewriting an identical file
 * would defeat the incremental build on every run.
 */
internal fun File.copyToIfDifferent(destination: File) {
    val identical =
        destination.exists() && destination.length() == length() && destination.readBytes().contentEquals(readBytes())

    if (identical) {
        return
    }

    copyTo(destination, overwrite = true)
}

internal fun File.writeTextIfDifferent(content: String) {
    if (exists() && readText() == content) {
        return
    }

    writeText(content)
}
