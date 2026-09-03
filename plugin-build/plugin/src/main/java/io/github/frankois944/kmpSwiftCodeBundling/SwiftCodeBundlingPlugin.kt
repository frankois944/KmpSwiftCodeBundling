package io.github.frankois944.kmpSwiftCodeBundling

import io.github.frankois944.kmpSwiftCodeBundling.internal.SwiftCodeBundlingConfigurator
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Bundles handwritten Swift code into the Kotlin/Native frameworks produced by this module.
 *
 * Swift files are picked up from `src/<sourceSet>/swift`, mirroring the `src/<sourceSet>/kotlin`
 * convention. They are stored inside the klib of their compilation, extracted again by every binary
 * linking against that klib, compiled as an overlay of the framework's Objective-C module, and
 * linked into the framework binary.
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class SwiftCodeBundlingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension =
            project.extensions.create(
                SwiftBundling.EXTENSION_NAME,
                SwiftCodeBundlingExtension::class.java,
            )

        project.plugins.withId(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
            project.afterEvaluate { evaluatedProject ->
                SwiftCodeBundlingConfigurator(evaluatedProject, extension).configure()
            }
        }
    }

    private companion object {
        const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
    }
}
