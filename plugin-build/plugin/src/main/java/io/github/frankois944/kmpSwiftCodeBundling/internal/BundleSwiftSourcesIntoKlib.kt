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
 *     SKIE/skie-gradle/plugin-impl/src/main/kotlin/co/touchlab/skie/plugin/switflink/SwiftBundlingConfigurator.kt
 *
 * Changes made in this file: extracted the klib-writing step into a Gradle Action of its own.
 */
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
                val bundled = fileSystem.getPath("/${SwiftBundling.KLIB_SWIFT_DIRECTORY}")

                sources.toPath().syncDirectoryContentIfDifferent(bundled)
            }
        } else {
            sources.toPath().syncDirectoryContentIfDifferent(
                target.toPath().resolve(SwiftBundling.KLIB_SWIFT_DIRECTORY),
            )
        }
    }
}
