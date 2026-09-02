package io.github.frankois944.kmpSwiftCodeBundling.internal

import io.github.frankois944.kmpSwiftCodeBundling.ProcessSwiftSourcesTask
import io.github.frankois944.kmpSwiftCodeBundling.SwiftBundling
import io.github.frankois944.kmpSwiftCodeBundling.SwiftCodeBundlingExtension
import io.github.frankois944.kmpSwiftCodeBundling.UnpackSwiftSourcesTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

internal class SwiftCodeBundlingConfigurator(
    private val project: Project,
    private val extension: SwiftCodeBundlingExtension,
) {
    private val compilerPluginConfiguration: Configuration by lazy { createCompilerPluginConfiguration() }

    fun configure() {
        if (!extension.enabled.get()) {
            return
        }

        val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return

        kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
            target.compilations.configureEach { compilation ->
                configureBundling(compilation)
            }

            target.binaries.withType(Framework::class.java).configureEach { framework ->
                configureFramework(framework)
            }
        }
    }

    private fun createCompilerPluginConfiguration(): Configuration =
        project.configurations.maybeCreate(SwiftBundling.COMPILER_PLUGIN_CONFIGURATION).apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false

            project.dependencies.add(name, CompilerPluginArtifact.dependencyNotation)
        }

    /** Collects the Swift sources of [compilation] and stores them inside the klib it produces. */
    private fun configureBundling(compilation: KotlinNativeCompilation) {
        val compilationSuffix = compilation.name.takeIf { !it.equals(MAIN_COMPILATION_NAME, ignoreCase = true) }
        val taskName = lowerCamelCaseName("processSwiftSources", compilation.target.name, compilationSuffix)

        // `allKotlinSourceSets` is filled in as the source set hierarchy is wired up, so reading it
        // now would usually yield nothing. Resolving it through a provider defers the lookup until
        // the file collection is queried, by which point the hierarchy is complete.
        val swiftSourceDirectories =
            project.provider {
                compilation.allKotlinSourceSets.map { project.layout.projectDirectory.dir("src/${it.name}/swift") }
            }

        val swiftSources =
            project.objects
                .fileCollection()
                .from(swiftSourceDirectories)
                .asFileTree
                .matching { it.include("**/*.swift") }

        val outputDirectory =
            project.layout.buildDirectory.dir(
                "${SwiftBundling.BUILD_DIRECTORY}/compilations/${compilation.target.name}/${compilation.name}/swift",
            )

        val processTask =
            project.tasks.register(taskName, ProcessSwiftSourcesTask::class.java) { task ->
                task.swiftSources.from(swiftSources)
                task.searchedDirectories.set(swiftSourceDirectories.map { dirs -> dirs.map { it.asFile.path } })
                task.output.set(outputDirectory)
            }

        val processedSwiftSources: Provider<Directory> = processTask.flatMap { it.output }
        val klib = compilation.compileTaskProvider.flatMap { it.outputFile }

        compilation.compileTaskProvider.configure { compileTask ->
            compileTask.inputs.files(processedSwiftSources).withPropertyName("bundledSwiftSources")
            compileTask.doLast(BundleSwiftSourcesIntoKlib(klib, processedSwiftSources))
        }
    }

    /** Extracts the bundled Swift sources of every linked klib and hands them to the compiler plugin. */
    private fun configureFramework(framework: Framework) {
        val compilation = framework.compilation
        val binaryDirectory =
            project.layout.buildDirectory.dir(
                "${SwiftBundling.BUILD_DIRECTORY}/binaries/${framework.target.targetName}/${framework.name}",
            )

        val swiftSourcesDirectory = binaryDirectory.map { it.dir("swift") }
        val workDirectory = binaryDirectory.map { it.dir("work") }

        val taskName = lowerCamelCaseName("unpackSwiftSources", framework.name, framework.target.targetName)

        val unpackTask =
            project.tasks.register(taskName, UnpackSwiftSourcesTask::class.java) { task ->
                task.klibs.from(project.configurations.getByName(compilation.compileDependencyConfigurationName))
                task.klibs.from(compilation.compileTaskProvider.flatMap { it.outputFile })
                task.output.set(swiftSourcesDirectory)

                // `outputFile` is a Provider<File>, not a Provider<FileSystemLocation>, so Gradle
                // cannot infer which task produces the klib from it.
                task.dependsOn(compilation.compileTaskProvider)
            }

        framework.linkTaskProvider.configure { linkTask ->
            linkTask.inputs.files(unpackTask.flatMap { it.output }).withPropertyName("bundledSwiftSources")

            linkTask.compilerPluginClasspath =
                listOfNotNull(linkTask.compilerPluginClasspath, compilerPluginConfiguration)
                    .reduce(FileCollection::plus)

            linkTask.compilerPluginOptions.addPluginArgument(
                SwiftBundling.COMPILER_PLUGIN_ID,
                SubpluginOption(SwiftBundling.OPTION_SWIFT_SOURCES_DIR, swiftSourcesDirectory.get().asFile.absolutePath),
            )
            linkTask.compilerPluginOptions.addPluginArgument(
                SwiftBundling.COMPILER_PLUGIN_ID,
                SubpluginOption(SwiftBundling.OPTION_WORK_DIR, workDirectory.get().asFile.absolutePath),
            )
            linkTask.compilerPluginOptions.addPluginArgument(
                SwiftBundling.COMPILER_PLUGIN_ID,
                SubpluginOption(SwiftBundling.OPTION_SWIFT_VERSION, extension.swiftVersion.get()),
            )
            linkTask.compilerPluginOptions.addPluginArgument(
                SwiftBundling.COMPILER_PLUGIN_ID,
                SubpluginOption(
                    SwiftBundling.OPTION_SWIFT_LIBRARY_EVOLUTION,
                    extension.enableSwiftLibraryEvolution.get().toString(),
                ),
            )
            linkTask.compilerPluginOptions.addPluginArgument(
                SwiftBundling.COMPILER_PLUGIN_ID,
                SubpluginOption(
                    SwiftBundling.OPTION_NO_CLANG_MODULE_BREADCRUMBS,
                    extension.noClangModuleBreadcrumbsInStaticFrameworks.get().toString(),
                ),
            )
            linkTask.compilerPluginOptions.addPluginArgument(
                SwiftBundling.COMPILER_PLUGIN_ID,
                SubpluginOption(
                    SwiftBundling.OPTION_RELATIVE_SOURCE_PATHS,
                    extension.enableRelativeSourcePathsInDebugSymbols.get().toString(),
                ),
            )

            extension.freeSwiftCompilerArgs.get().forEach { argument ->
                linkTask.compilerPluginOptions.addPluginArgument(
                    SwiftBundling.COMPILER_PLUGIN_ID,
                    SubpluginOption(SwiftBundling.OPTION_FREE_COMPILER_ARGS, argument),
                )
            }

            if (extension.enableRelativeSourcePathsInDebugSymbols.get()) {
                // The Kotlin half of the same feature: without it only the Swift sources would have
                // relative paths in the debug symbols.
                linkTask.toolOptions.freeCompilerArgs.add("-Xdebug-prefix-map=${project.rootDir.absolutePath}=.")
            }
        }
    }

    private companion object {
        const val MAIN_COMPILATION_NAME = "main"
    }
}
