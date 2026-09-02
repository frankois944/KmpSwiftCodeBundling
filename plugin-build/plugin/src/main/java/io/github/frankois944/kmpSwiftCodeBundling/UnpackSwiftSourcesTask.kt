/*
 * Derived from SKIE (https://github.com/touchlab/SKIE)
 * Copyright 2023 Touchlab, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Based on:
 *     SKIE/skie-gradle/plugin-impl/src/main/kotlin/co/touchlab/skie/plugin/switflink/UnpackSwiftSourcesTask.kt
 *
 * Changes made in this file: takes a file collection rather than a serialized dependency list, and prunes empty directories.
 */
package io.github.frankois944.kmpSwiftCodeBundling

import io.github.frankois944.kmpSwiftCodeBundling.internal.collisionFreeIdentifier
import io.github.frankois944.kmpSwiftCodeBundling.internal.deleteEmptyDirectoriesRecursively
import io.github.frankois944.kmpSwiftCodeBundling.internal.isKlib
import io.github.frankois944.kmpSwiftCodeBundling.internal.syncDirectoryContentIfDifferent
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * Extracts, from every klib the binary links against, the Swift sources that were bundled into it.
 *
 * Files coming from different klibs are prefixed with the name of their origin, because Swift needs
 * every file of a module to have a unique name.
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class UnpackSwiftSourcesTask : DefaultTask() {
    init {
        description = "Extracts the bundled Swift sources from the klibs linked into this binary."
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val klibs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @TaskAction
    fun execute() {
        val staging = temporaryDir.resolve("extractedSwiftSources")
        staging.deleteRecursively()
        staging.mkdirs()

        val usedNames = mutableSetOf<String>()

        klibs.files
            .filter { it.exists() && (it.isKlib || it.isDirectory) }
            .sortedBy { it.absolutePath }
            .forEach { klib ->
                val uniqueName = klib.nameWithoutExtension.collisionFreeIdentifier(usedNames)
                usedNames.add(uniqueName)

                unpack(klib, uniqueName, staging)
            }

        staging.deleteEmptyDirectoriesRecursively()
        staging.mkdirs()

        staging.toPath().syncDirectoryContentIfDifferent(
            output
                .get()
                .asFile
                .also { it.mkdirs() }
                .toPath(),
        )
    }

    private fun unpack(
        klib: File,
        uniqueName: String,
        staging: File,
    ) {
        fileSystemOperations.copy {
            val source: Any = if (klib.isKlib) archiveOperations.zipTree(klib) else klib

            it.from(source) { spec ->
                spec.include("${SwiftBundling.KLIB_SWIFT_DIRECTORY}/**/*.swift")
            }

            it.eachFile { details ->
                val basePath =
                    details.relativePath.pathString
                        .removePrefix(SwiftBundling.KLIB_SWIFT_DIRECTORY)
                        .substringBeforeLast("/")
                        .removePrefix("/")

                val prefix = if (basePath.isEmpty()) "" else basePath.replace("/", ".") + "."

                details.relativePath = RelativePath(true, "$uniqueName/$basePath/bundled.$uniqueName.$prefix${details.name}")
            }

            it.into(staging)
        }
    }
}
