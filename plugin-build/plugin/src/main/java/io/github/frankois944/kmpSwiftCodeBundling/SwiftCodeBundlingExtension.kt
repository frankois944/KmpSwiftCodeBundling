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
 *     SKIE/skie-gradle/plugin-api/src/main/kotlin/co/touchlab/skie/plugin/configuration/SkieBuildConfiguration.kt
 *     SKIE .../plugin/configuration/SkieSwiftBundlingConfiguration.kt (skie-gradle/plugin-api)
 *
 * Changes made in this file: flattened into a single extension, keeping only the options that
 * apply to Swift code bundling; the documentation of each option is adapted from the SKIE originals.
 */
package io.github.frankois944.kmpSwiftCodeBundling

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@Suppress("AbstractClassCanBeConcreteClass")
abstract class SwiftCodeBundlingExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /** Enables bundling of the Swift sources found in `src/<sourceSet>/swift`. */
        val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

        /** Swift language version passed to `swiftc` via `-swift-version`. */
        val swiftVersion: Property<String> = objects.property(String::class.java).convention("5")

        /**
         * Enables Swift library evolution.
         *
         * Building with library evolution increases compilation time, so use it only when the
         * framework is compiled against on another machine.
         *
         * Frameworks assembled into an XCFramework always get library evolution, whatever this is
         * set to, because an XCFramework is consumed from another machine by definition.
         */
        val enableSwiftLibraryEvolution: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

        /**
         * Passes `-no-clang-module-breadcrumbs` to the Swift compiler front end when building a
         * static framework.
         *
         * Without it a redistributable static framework carries DWARF skeleton CUs pointing at a
         * module cache that does not exist on the consuming machine, which degrades debugging and
         * produces warnings of the form `..../xyz.pcm: No such file or directory`.
         */
        val noClangModuleBreadcrumbsInStaticFrameworks: Property<Boolean> =
            objects.property(Boolean::class.java).convention(false)

        /**
         * Records source file paths in the debug symbols relative to the root project rather than
         * absolute, so that a framework built on one machine can be debugged on another.
         *
         * Adds `-Xdebug-prefix-map=<rootDir>=.` to the link task and `-file-compilation-dir .` to
         * the Swift compilation, and works around a Kotlin/Native bug that would otherwise drop the
         * links to the Kotlin sources.
         */
        val enableRelativeSourcePathsInDebugSymbols: Property<Boolean> =
            objects.property(Boolean::class.java).convention(false)

        /** Extra arguments appended to the `swiftc` invocation. */
        val freeSwiftCompilerArgs: ListProperty<String> =
            objects.listProperty(String::class.java).convention(emptyList())

        /**
         * Configures the plugin to produce a framework that can be compiled against on another
         * machine - when publishing an XCFramework, for instance.
         *
         * It is unnecessary when the framework is only consumed locally, including as a dependency
         * of a binary that is itself distributed.
         */
        fun produceDistributableFramework() {
            enableSwiftLibraryEvolution.set(true)
            noClangModuleBreadcrumbsInStaticFrameworks.set(true)
            enableRelativeSourcePathsInDebugSymbols.set(true)
        }
    }
