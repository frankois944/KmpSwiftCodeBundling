@file:Suppress("invisible_reference", "invisible_member", "USELESS_CAST")

package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Kotlin/Native internals as named in Kotlin 2.2 and 2.3. See the 2.4 variant for the renames.
 */
internal typealias KonanConfig = org.jetbrains.kotlin.backend.konan.KonanConfig

internal typealias KonanConfigKeys = org.jetbrains.kotlin.backend.konan.KonanConfigKeys

internal typealias PhaseContext = org.jetbrains.kotlin.backend.konan.driver.PhaseContext

internal val frameworkOutputPathKey: CompilerConfigurationKey<String>
    get() = KonanConfigKeys.OUTPUT

/**
 * From 2.3 on, `config` is declared as a wider compilation config; at the linker phase the instance
 * is always the native one, so the cast holds. It is redundant on 2.2 and harmless there.
 */
internal val PhaseContext.konanConfig: KonanConfig
    get() = config as KonanConfig
