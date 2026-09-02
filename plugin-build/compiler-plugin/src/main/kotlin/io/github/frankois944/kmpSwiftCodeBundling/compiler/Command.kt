package io.github.frankois944.kmpSwiftCodeBundling.compiler

import java.io.File

internal class Command(
    private val arguments: List<String>,
) {
    fun execute(
        workingDirectory: File? = null,
        logFile: File? = null,
    ) {
        logFile?.parentFile?.mkdirs()
        logFile?.writeText(arguments.joinToString(" ") + "\n\n")

        val process =
            ProcessBuilder(arguments)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        logFile?.appendText(output)

        if (exitCode != 0) {
            error(
                "The Swift compiler returned a non-zero exit code ($exitCode) while compiling the bundled Swift code.\n" +
                    "Command: ${arguments.joinToString(" ")}\n" +
                    "Output:\n$output",
            )
        }
    }
}
