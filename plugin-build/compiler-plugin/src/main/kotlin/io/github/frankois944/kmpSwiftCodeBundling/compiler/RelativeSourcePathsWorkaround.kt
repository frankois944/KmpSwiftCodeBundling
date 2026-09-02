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
