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
 *     SKIE/common/util/src/main/kotlin/co/touchlab/skie/util/CollisionFreeIdentifier.kt
 *     SKIE/skie-gradle/plugin-util/src/main/kotlin/co/touchlab/skie/plugin/util/LowerCamelCaseName.kt
 *
 * Changes made in this file: rewritten as plain loops over the same behaviour.
 */
package io.github.frankois944.kmpSwiftCodeBundling.internal

internal fun lowerCamelCaseName(vararg nameParts: String?): String =
    nameParts
        .filterNotNull()
        .filter { it.isNotEmpty() }
        .mapIndexed { index, part ->
            if (index == 0) {
                part.replaceFirstChar { it.lowercase() }
            } else {
                part.replaceFirstChar { it.uppercase() }
            }
        }.joinToString("")

/** Appends underscores until the identifier no longer collides with an already used one. */
internal fun String.collisionFreeIdentifier(usedIdentifiers: Set<String>): String {
    var candidate = this

    while (candidate in usedIdentifiers) {
        candidate += "_"
    }

    return candidate
}
