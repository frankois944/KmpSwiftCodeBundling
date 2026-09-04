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
 *     SKIE/skie-gradle/plugin-api/src/main/kotlin/co/touchlab/skie/plugin/configuration/SkieExtension.kt
 *     SKIE .../plugin/configuration/SkieSwiftBundlingConfiguration.kt (skie-gradle/plugin-api)
 *
 * Changes made in this file: kept only the two options `SkieDetection` reads, and the extension name
 * they are reached through; everything else SKIE declares is left out.
 */
package co.touchlab.skie

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Stands in for SKIE in tests: its package is what identifies it, and it registers an extension
 * with the shape `SkieDetection` reflects over. Depending on the real plugin would pull SKIE's whole
 * toolchain into the test runtime.
 */
class StubSkiePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create(EXTENSION_NAME, StubSkieExtension::class.java)
    }

    companion object {
        const val EXTENSION_NAME: String = "skie"
    }
}

/** Mirrors the members of SKIE's `SkieExtension` that this plugin looks at. */
open class StubSkieExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val isEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

        val swiftBundling: StubSkieSwiftBundlingConfiguration =
            objects.newInstance(StubSkieSwiftBundlingConfiguration::class.java)

        fun swiftBundling(action: Action<in StubSkieSwiftBundlingConfiguration>) {
            action.execute(swiftBundling)
        }
    }

/** Mirrors SKIE's `SkieSwiftBundlingConfiguration`. */
abstract class StubSkieSwiftBundlingConfiguration
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    }
