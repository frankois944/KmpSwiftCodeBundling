@file:Suppress("invisible_reference", "invisible_member")

package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.backend.konan.NativeGenerationState
import org.jetbrains.kotlin.backend.konan.driver.phases.CodegenPhase
import org.jetbrains.kotlin.backend.konan.driver.phases.LinkerPhase
import org.jetbrains.kotlin.backend.konan.driver.phases.LinkerPhaseInput
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File

/**
 * Entry point of the compiler plugin.
 *
 * The plugin does not touch the Kotlin frontend or IR: it wraps the Kotlin/Native `LinkerPhase` so
 * that the object file produced from the bundled Swift sources is handed to the native linker
 * together with the Kotlin ones, and - only when relative source paths are requested - the
 * `CodegenPhase`, to work around a Kotlin/Native bug described in [RelativeSourcePathsWorkaround].
 */
class SwiftBundlingCompilerPluginRegistrar : BaseSwiftBundlingRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val swiftSourcesDirectory = configuration.get(SwiftBundlingConfigurationKeys.SWIFT_SOURCES_DIR)?.let(::File)
        val workDirectory = configuration.get(SwiftBundlingConfigurationKeys.WORK_DIR)?.let(::File)

        if (swiftSourcesDirectory == null || workDirectory == null) {
            return
        }

        val options =
            SwiftBundlingOptions(
                swiftSourcesDirectory = swiftSourcesDirectory,
                workDirectory = workDirectory,
                swiftVersion = configuration.get(SwiftBundlingConfigurationKeys.SWIFT_VERSION) ?: DEFAULT_SWIFT_VERSION,
                swiftLibraryEvolution = configuration.get(SwiftBundlingConfigurationKeys.SWIFT_LIBRARY_EVOLUTION) ?: false,
                noClangModuleBreadcrumbsInStaticFrameworks =
                    configuration.get(SwiftBundlingConfigurationKeys.NO_CLANG_MODULE_BREADCRUMBS) ?: false,
                relativeSourcePathsInDebugSymbols = configuration.get(SwiftBundlingConfigurationKeys.RELATIVE_SOURCE_PATHS) ?: false,
                freeCompilerArgs = configuration.get(SwiftBundlingConfigurationKeys.FREE_COMPILER_ARGS).orEmpty(),
            )

        val linkerStep = SwiftBundlingLinkerStep(options)

        PhaseBodyInterception.install(
            phase = LinkerPhase,
            keyName = LINKER_PHASE_INTERCEPTOR_KEY,
            configuration = configuration,
            configurationOf = { (it as PhaseContext).config.configuration },
        ) { context, input, next ->
            linkerStep.run(context as PhaseContext, input as LinkerPhaseInput) { nextContext, nextInput ->
                next(nextContext, nextInput)
            }
        }

        if (options.relativeSourcePathsInDebugSymbols) {
            PhaseBodyInterception.install(
                phase = CodegenPhase,
                keyName = CODEGEN_PHASE_INTERCEPTOR_KEY,
                configuration = configuration,
                configurationOf = { (it as NativeGenerationState).config.configuration },
            ) { context, input, next ->
                RelativeSourcePathsWorkaround.apply(context as NativeGenerationState)
                next(context, input)
            }
        }
    }

    private companion object {
        const val DEFAULT_SWIFT_VERSION = "5"
        const val LINKER_PHASE_INTERCEPTOR_KEY = "kmpSwiftCodeBundling.linkerPhaseInterceptor"
        const val CODEGEN_PHASE_INTERCEPTOR_KEY = "kmpSwiftCodeBundling.codegenPhaseInterceptor"
    }
}
