package io.github.frankois944.kmpSwiftCodeBundling.internal

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Mirrors [this] into [destination], leaving untouched every file that is already identical.
 *
 * The Swift compiler decides what to recompile from file timestamps, so rewriting unchanged sources
 * would defeat its incremental build.
 */
internal fun Path.syncDirectoryContentIfDifferent(destination: Path) {
    if (!destination.exists()) {
        destination.createDirectories()
    }

    require(isDirectory()) { "Source $this must be a directory." }
    require(destination.isDirectory()) { "Destination $destination must be a directory." }

    val originEntries = listDirectoryEntries()
    val originNames = originEntries.map { it.name }.toSet()

    destination.listDirectoryEntries()
        .filter { it.name !in originNames }
        .forEach { it.deleteRecursivelyIfExists() }

    originEntries.forEach { entry ->
        val target = destination.resolve(entry.name)

        if (entry.isDirectory()) {
            entry.syncDirectoryContentIfDifferent(target)
        } else {
            entry.copyFileToIfDifferent(target)
        }
    }
}

private fun Path.copyFileToIfDifferent(destination: Path) {
    if (destination.exists() && Files.size(this) == Files.size(destination) && readAllBytesEqual(destination)) {
        return
    }

    destination.parent?.createDirectories()
    Files.copy(this, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
}

private fun Path.readAllBytesEqual(other: Path): Boolean = Files.readAllBytes(this).contentEquals(Files.readAllBytes(other))

private fun Path.deleteRecursivelyIfExists() {
    if (isDirectory()) {
        listDirectoryEntries().forEach { it.deleteRecursivelyIfExists() }
    }

    deleteIfExists()
}

/**
 * Removes the directories left behind by a copy whose include pattern matched nothing, so that the
 * synced output only contains real Swift sources.
 */
internal fun java.io.File.deleteEmptyDirectoriesRecursively() {
    if (!isDirectory) {
        return
    }

    listFiles()?.forEach { it.deleteEmptyDirectoriesRecursively() }

    if (listFiles().isNullOrEmpty()) {
        delete()
    }
}
