package io.github.frankois944.kmpSwiftCodeBundling.internal

import io.github.frankois944.kmpSwiftCodeBundling.SwiftBundling
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File

/**
 * Stores the Swift sources of a compilation inside the klib it produces, so that they travel with
 * the module and can be compiled again by whichever binary links against it.
 *
 * This runs as the last action of the Kotlin compile task rather than as a task of its own: the
 * klib is the compile task's output, and a separate task rewriting it would make that output stale.
 */
internal class BundleSwiftSourcesIntoKlib(
    private val klib: Provider<File>,
    private val swiftSources: Provider<Directory>,
) : Action<Task> {
    override fun execute(task: Task) {
        val sources = swiftSources.get().asFile

        if (!sources.isDirectory) {
            return
        }

        val target = klib.get()

        if (target.isKlib) {
            target.writeToZip { fileSystem ->
                sources.toPath().syncDirectoryContentIfDifferent(fileSystem.getPath("/${SwiftBundling.KLIB_SWIFT_DIRECTORY}"))
            }
        } else {
            sources.toPath().syncDirectoryContentIfDifferent(target.toPath().resolve(SwiftBundling.KLIB_SWIFT_DIRECTORY))
        }
    }
}
