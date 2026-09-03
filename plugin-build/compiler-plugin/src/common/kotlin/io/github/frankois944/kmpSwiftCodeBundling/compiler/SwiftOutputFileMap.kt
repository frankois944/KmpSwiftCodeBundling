package io.github.frankois944.kmpSwiftCodeBundling.compiler

import java.io.File

/**
 * The output file map handed to `swiftc`: where each source file's outputs go.
 *
 * In debug this is what makes the build incremental. Two rules, both found with
 * `-driver-show-incremental` and both easy to break:
 *
 * - The root entry must declare `swift-dependencies`. The driver uses it as the path of its build
 *   record; without it the whole thing is skipped with "Disabling incremental build: no build record
 *   path".
 * - Per-file entries must declare only outputs the compiler actually writes. The driver checks every
 *   declared output before skipping a file, so a `.d` without `-emit-dependencies`, or the
 *   `~partial.swiftmodule` that modern Swift no longer emits when the module is written directly,
 *   makes it report "Missing an output" and recompile everything, every build.
 *
 * A release build compiles the whole module at once, so the root entry carries a single object.
 */
object SwiftOutputFileMap {
    fun content(
        moduleName: String,
        moduleDirectory: File,
        objectDirectory: File,
        units: List<SwiftSourceUnit>,
        isDebug: Boolean,
    ): String {
        val entries =
            buildList {
                if (isDebug) {
                    val buildRecord = moduleDirectory.resolve("$moduleName.swiftdeps")

                    add("""  "": { "swift-dependencies": ${buildRecord.jsonString()} }""")

                    units.forEach { unit ->
                        val fields =
                            listOf(
                                """"object": ${objectDirectory.resolve("${unit.baseName}.o").jsonString()}""",
                                """"swift-dependencies": ${
                                    objectDirectory.resolve("${unit.baseName}.swiftdeps").jsonString()
                                }""",
                            )

                        add("""  ${unit.relativePath.jsonString()}: { ${fields.joinToString(", ")} }""")
                    }
                } else {
                    add("""  "": { "object": ${objectDirectory.resolve("$moduleName.o").jsonString()} }""")
                }
            }

        return entries.joinToString(",\n", prefix = "{\n", postfix = "\n}\n")
    }

    private fun File.jsonString(): String = absolutePath.jsonString()

    private fun String.jsonString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
