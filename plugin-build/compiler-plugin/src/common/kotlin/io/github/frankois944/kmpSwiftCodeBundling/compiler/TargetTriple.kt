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
 *     SKIE/common/util/src/main/kotlin/co/touchlab/skie/util/TargetTriple.kt
 *
 * Changes made in this file: dropped the string parser, which this plugin does not need.
 */
package io.github.frankois944.kmpSwiftCodeBundling.compiler

data class TargetTriple(
    val architecture: String,
    val vendor: String,
    val os: String,
    val environment: String?,
) {
    val isMacos: Boolean
        get() = os == "macos"

    override fun toString(): String = listOfNotNull(architecture, vendor, os, environment).joinToString("-")

    fun withOsVersion(osVersion: String): TargetTriple = copy(os = "$os$osVersion")
}
