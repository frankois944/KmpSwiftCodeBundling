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
