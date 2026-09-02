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
 *     SKIE/skie-gradle/plugin-impl/src/main/kotlin/co/touchlab/skie/plugin/switflink/ProcessSwiftSourcesTask.kt
 *
 * Changes made in this file: uses a DirectoryProperty output and logs the directories it searched.
 */
package io.github.frankois944.kmpSwiftCodeBundling

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Collects the Swift sources of every source set of a compilation into a single directory, which is
 * then stored inside the compilation's klib.
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class ProcessSwiftSourcesTask : DefaultTask() {
    init {
        description = "Collects the Swift sources bundled with this compilation."
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val swiftSources: ConfigurableFileCollection

    /** Directories the sources were looked for in. Reported on `--info` to make an empty result diagnosable. */
    @get:Internal
    abstract val searchedDirectories: ListProperty<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun execute() {
        val sources = swiftSources.files

        logger.info(
            "Bundling {} Swift source(s), looked for in: {}",
            sources.size,
            searchedDirectories.getOrElse(emptyList()).joinToString(", "),
        )

        fileSystemOperations.sync {
            it.duplicatesStrategy = DuplicatesStrategy.FAIL
            it.from(swiftSources)
            it.into(output)
        }

        verifyFileNames()
    }

    private fun verifyFileNames() {
        output
            .get()
            .asFile
            .walkTopDown()
            .filter { it.isFile && it.extension == "swift" }
            .groupBy { it.name }
            .forEach { (name, files) ->
                check(files.size == 1) {
                    "Files $files have the same name '$name'. Swift requires every file of a module to have a " +
                        "unique name, regardless of its path."
                }
            }
    }
}
