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
 *     SKIE/kotlin-compiler/linker-plugin/src/2.1.20/kotlin/co/touchlab/skie/compilerinject/interceptor/SimpleNamedPhaseInterceptorConfigurer.kt
 *     SKIE/kotlin-compiler/linker-plugin/src/main/kotlin/co/touchlab/skie/compilerinject/interceptor/PhaseInterceptorRegistrar.kt
 *     SKIE/kotlin-compiler/linker-plugin/src/main/kotlin/co/touchlab/skie/compilerinject/interceptor/ErasedPhaseInterceptorChain.kt
 *
 * Changes made in this file: reduced to a single type-erased implementation covering two phases, dropping the ServiceLoader
 * SPI, the interceptor chain and the Reflector helper.
 */
package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.lang.reflect.Field

internal typealias ErasedPhaseBody = (Any, Any) -> Unit

internal typealias ErasedPhaseInterceptor = (Any, Any, ErasedPhaseBody) -> Unit

/**
 * Wraps the body of a Kotlin/Native compiler phase.
 *
 * Phases are singletons shared by every compilation running in the same JVM (the Kotlin daemon
 * links several frameworks in a row) and their body lives in a private `$op` field, so it can be
 * replaced by a wrapper delegating to the original one.
 *
 * The phase is global but the work to perform is per-compilation, so the wrapper captures nothing
 * compilation-specific: it reads what to do from the [CompilerConfiguration] of the compilation
 * currently running. The value stored there is a plain Kotlin function type, the only type
 * guaranteed to be loadable from every class loader that could hold a copy of this plugin - and the
 * key identifying it lives in the already installed wrapper, so two copies agree on where to look.
 *
 * Everything is erased to [Any] because the phases wrapped here have unrelated context and input
 * types; each caller casts them back in its own interceptor.
 */
internal object PhaseBodyInterception {
    private const val PHASE_BODY_FIELD_NAME = "\$op"
    private const val INTERCEPTOR_KEY_FIELD_NAME = "interceptorKey"

    fun install(
        phase: Any,
        keyName: String,
        configuration: CompilerConfiguration,
        configurationOf: (context: Any) -> CompilerConfiguration,
        interceptor: ErasedPhaseInterceptor,
    ) {
        val field =
            phase.findPhaseBodyField() ?: error(
                "Could not find the `$PHASE_BODY_FIELD_NAME` field of the Kotlin/Native phase $phase. " +
                    "This Kotlin version is not supported by the KMP Swift code bundling plugin.",
            )

        val key =
            synchronized(phase) {
                val currentBody = field.get(phase)

                if (currentBody.isAlreadyIntercepted()) {
                    currentBody.readInterceptorKey()
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val originalBody = currentBody as ErasedPhaseBody
                    val newKey = CompilerConfigurationKey.create<ErasedPhaseInterceptor>(keyName)

                    field.set(phase, InterceptedPhaseBody(originalBody, newKey, configurationOf))

                    newKey
                }
            }

        configuration.put(key, interceptor)
    }

    private fun Any?.isAlreadyIntercepted(): Boolean = this != null && javaClass.name == InterceptedPhaseBody::class.java.name

    private fun Any.readInterceptorKey(): CompilerConfigurationKey<ErasedPhaseInterceptor> {
        val keyField = javaClass.getDeclaredField(INTERCEPTOR_KEY_FIELD_NAME).also { it.isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        return keyField.get(this) as CompilerConfigurationKey<ErasedPhaseInterceptor>
    }

    private fun Any.findPhaseBodyField(): Field? {
        var current: Class<*>? = javaClass

        while (current != null) {
            current.declaredFields.firstOrNull { it.name == PHASE_BODY_FIELD_NAME }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }

        return null
    }

    private class InterceptedPhaseBody(
        private val originalPhaseBody: ErasedPhaseBody,
        private val interceptorKey: CompilerConfigurationKey<ErasedPhaseInterceptor>,
        private val configurationOf: (context: Any) -> CompilerConfiguration,
    ) : ErasedPhaseBody {
        override fun invoke(
            context: Any,
            input: Any,
        ) {
            val interceptor = configurationOf(context).get(interceptorKey)

            if (interceptor == null) {
                originalPhaseBody(context, input)
            } else {
                interceptor(context, input, originalPhaseBody)
            }
        }
    }
}
