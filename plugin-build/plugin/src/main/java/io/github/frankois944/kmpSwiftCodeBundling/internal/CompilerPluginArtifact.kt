package io.github.frankois944.kmpSwiftCodeBundling.internal

import java.util.Properties

/**
 * Coordinates of the companion compiler plugin, written into the jar at build time so that the
 * Gradle plugin always resolves the matching version.
 */
internal object CompilerPluginArtifact {
    private const val RESOURCE_PATH = "/io/github/frankois944/kmpSwiftCodeBundling/plugin.properties"
    private const val ARTIFACT_ID = "compiler-plugin"

    val dependencyNotation: String by lazy {
        val properties =
            Properties().apply {
                val stream =
                    CompilerPluginArtifact::class.java.getResourceAsStream(RESOURCE_PATH)
                        ?: error("Could not read $RESOURCE_PATH from the KMP Swift code bundling plugin jar.")

                stream.use { load(it) }
            }

        "${properties.getProperty("group")}:$ARTIFACT_ID:${properties.getProperty("version")}"
    }
}
