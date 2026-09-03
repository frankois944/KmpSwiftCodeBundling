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
 *     SKIE/common/util/src/main/kotlin/co/touchlab/skie/util/Command.kt
 *
 * Changes made in this file: reduced to a single blocking invocation. The SKIE original is itself
 * derived from the Kotlin compiler, Copyright 2010-2017 JetBrains s.r.o., also under Apache 2.0.
 */
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
                "The Swift compiler returned a non-zero exit code ($exitCode) while compiling " +
                    "the bundled Swift code.\n" +
                    "Command: ${arguments.joinToString(" ")}\n" +
                    "Output:\n$output",
            )
        }
    }
}
