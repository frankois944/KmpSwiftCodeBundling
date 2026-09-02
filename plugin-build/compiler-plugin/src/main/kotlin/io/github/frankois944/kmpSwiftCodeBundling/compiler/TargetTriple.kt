package io.github.frankois944.kmpSwiftCodeBundling.compiler

internal data class TargetTriple(
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
