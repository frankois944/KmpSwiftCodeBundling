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
 *     SKIE/kotlin-compiler/linker-plugin/src/main/kotlin/co/touchlab/skie/entrypoint/CodegenPhaseInterceptor.kt
 *
 * Changes made in this file: extracted the debug-prefix-map workaround on its own and reports a warning instead of a
 * compiler error when the debug info already exists.
 */
@file:Suppress("invisible_reference", "invisible_member")

package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.backend.konan.KonanConfigKeys
import org.jetbrains.kotlin.backend.konan.NativeGenerationState
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys

/**
 * Works around a Kotlin/Native bug that strips the links to the Kotlin sources from the binary when
 * `-Xdebug-prefix-map` is set.
 *
 * The debug info of the compilation unit has to be created while the prefix map is empty; the map is
 * then restored so the individual `.kt` file paths still get remapped. This mirrors SKIE's
 * `CodegenPhaseInterceptor`, and it only runs when the Gradle plugin asked for relative source
 * paths - which is also the only case where it adds `-Xdebug-prefix-map` to the linker task.
 */
internal object RelativeSourcePathsWorkaround {
    fun apply(context: NativeGenerationState) {
        val configuration = context.config.configuration

        if (context.hasDebugInfo()) {
            val messageCollector =
                configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY) ?: MessageCollector.NONE

            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "Debug info was initialized before the relative source paths workaround could run, so the " +
                    "Kotlin sources may not be linked in the produced binary. Disable " +
                    "`swiftCodeBundling.enableRelativeSourcePathsInDebugSymbols` if this is a problem.",
            )

            return
        }

        val debugPrefixMap = configuration.getMap(KonanConfigKeys.DEBUG_PREFIX_MAP)

        configuration.put(KonanConfigKeys.DEBUG_PREFIX_MAP, emptyMap())

        // Reading the property is what creates the debug info, which is the whole point.
        @Suppress("UNUSED_VARIABLE")
        val debugInfo = context.debugInfo

        configuration.put(KonanConfigKeys.DEBUG_PREFIX_MAP, debugPrefixMap)
    }
}
