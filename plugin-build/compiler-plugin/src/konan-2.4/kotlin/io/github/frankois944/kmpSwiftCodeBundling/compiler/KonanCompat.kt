@file:Suppress("invisible_reference", "invisible_member")

package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Kotlin/Native internals as named from Kotlin 2.4 onwards.
 *
 * `KonanConfig` became `NativeSecondStageCompilationConfig`, `PhaseContext` became
 * `NativePhaseContext`, and `KonanConfigKeys` moved to `NativeConfigurationKeys` with the output
 * path key renamed. The keys used elsewhere (`LINKER_ARGS`, `STATIC_FRAMEWORK`, `DEBUG_PREFIX_MAP`)
 * kept their names, so the typealias covers them.
 */
internal typealias KonanConfig = org.jetbrains.kotlin.backend.konan.NativeSecondStageCompilationConfig

internal typealias KonanConfigKeys = org.jetbrains.kotlin.konan.config.NativeConfigurationKeys

internal typealias PhaseContext = org.jetbrains.kotlin.backend.konan.driver.NativePhaseContext

internal val frameworkOutputPathKey: CompilerConfigurationKey<String>
    get() = KonanConfigKeys.KONAN_OUTPUT_PATH

internal val PhaseContext.konanConfig: KonanConfig
    get() = config as KonanConfig
