package io.github.frankois944.kmpSwiftCodeBundling.internal

import java.util.Properties

/**
 * Coordinates of the companion compiler plugin.
 *
 * The compiler plugin runs inside the Kotlin/Native compiler and uses its internal API, so it is
 * published once per supported Kotlin version. The right one is picked from the Kotlin version of
 * the consuming build; the group and version are written into this jar at build time so they always
 * match the Gradle plugin.
 */
internal object CompilerPluginArtifact {
    private const val RESOURCE_PATH = "/io/github/frankois944/kmpSwiftCodeBundling/plugin.properties"
    private const val ARTIFACT_PREFIX = "compiler-plugin-kotlin-"

    private val properties: Properties by lazy {
        Properties().apply {
            val stream =
                CompilerPluginArtifact::class.java.getResourceAsStream(RESOURCE_PATH)
                    ?: error("Could not read $RESOURCE_PATH from the KMP Swift code bundling plugin jar.")

            stream.use { load(it) }
        }
    }

    fun dependencyNotation(kotlinVersion: String): String {
        val group = properties.getProperty("group")
        val version = properties.getProperty("version")

        return "$group:$ARTIFACT_PREFIX${variantFor(kotlinVersion)}:$version"
    }

    /**
     * Newer Kotlin versions fall back to the latest known variant: it usually works, and failing
     * loudly on the day a new Kotlin is released would be worse than trying.
     */
    private fun variantFor(kotlinVersion: String): String {
        val (major, minor) = parseVersion(kotlinVersion)

        return when {
            major > 2 || (major == 2 && minor >= 4) -> "2.4"
            major == 2 && minor == 3 -> "2.3"
            major == 2 && minor == 2 -> "2.2"
            else ->
                error(
                    "Kotlin $kotlinVersion is not supported by the KMP Swift code bundling plugin, which needs " +
                        "Kotlin 2.2 or newer.",
                )
        }
    }

    private fun parseVersion(kotlinVersion: String): Pair<Int, Int> {
        val parts = kotlinVersion.substringBefore("-").split(".")
        val major = parts.getOrNull(0)?.toIntOrNull()
        val minor = parts.getOrNull(1)?.toIntOrNull()

        return if (major == null || minor == null) {
            error("Could not read the Kotlin version '$kotlinVersion'.")
        } else {
            major to minor
        }
    }
}
