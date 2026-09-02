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
 *     SKIE/skie-gradle/plugin-impl/src/main/kotlin/co/touchlab/skie/plugin/util/JarUtils.kt
 *
 * Changes made in this file: added the klib extension check alongside it.
 */
package io.github.frankois944.kmpSwiftCodeBundling.internal

import java.io.File
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems

internal val File.isKlib: Boolean
    get() = extension == "klib"

/** Opens the archive as a [FileSystem] so that entries can be added to it in place. */
internal fun File.writeToZip(write: (FileSystem) -> Unit) {
    val fileUri = toURI()

    // Rebuilding the URI component by component keeps paths containing spaces working.
    val uri = URI("jar:file", fileUri.userInfo, fileUri.host, fileUri.port, fileUri.path, fileUri.query, fileUri.fragment)

    val fileSystem =
        try {
            FileSystems.getFileSystem(uri)
        } catch (_: FileSystemNotFoundException) {
            FileSystems.newFileSystem(uri, mapOf("create" to true))
        }

    fileSystem.use(write)
}
