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
 *     SKIE/common/util/src/main/kotlin/co/touchlab/skie/util/directory/FrameworkLayout.kt
 *
 * Changes made in this file: trimmed to the paths this plugin reads and writes; directories are
 * no longer created on access.
 */
package io.github.frankois944.kmpSwiftCodeBundling.compiler

import java.io.File

/**
 * Paths inside a `.framework` bundle. macOS frameworks are versioned bundles, every other Apple
 * platform uses a flat one.
 */
class FrameworkLayout(
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
