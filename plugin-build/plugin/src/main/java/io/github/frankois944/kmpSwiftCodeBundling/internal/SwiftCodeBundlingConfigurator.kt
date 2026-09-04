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
 *     SKIE/skie-gradle/plugin-impl/src/main/kotlin/co/touchlab/skie/plugin/switflink/SwiftBundlingConfigurator.kt
 *     SKIE/skie-gradle/plugin-impl/src/main/kotlin/co/touchlab/skie/plugin/switflink/SwiftUnpackingConfigurator.kt
 *
 * Changes made in this file: merged into one configurator that talks to the Kotlin Gradle Plugin
 * directly instead of through a version shim layer, and handles framework binaries only.
 */
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
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkTask
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

internal class SwiftCodeBundlingConfigurator(
    private val project: Project,
    private val extension: SwiftCodeBundlingExtension,
) {
    private val compilerPluginConfiguration: Configuration by lazy { createCompilerPluginConfiguration() }

    /**
     * Output paths of every framework an XCFramework is assembled from.
     *
     * Resolved once, over all XCFramework tasks rather than only those in the task graph, so that a
     * framework is built identically whether it is linked on its own or as part of the XCFramework.
     * Matching on the task's input files avoids `taskDependencies.getDependencies()`, which Gradle
     * forbids under the configuration cache.
     */
    private val xcFrameworkInputPaths: Set<String> by lazy {
        project.tasks
            .withType(XCFrameworkTask::class.java)
            .toList()
            .flatMap { it.inputs.files.files }
            .mapTo(mutableSetOf()) { it.canonicalPath }
    }

    fun configure() {
        if (!extension.enabled.get() || standsDownForSkie()) {
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

        configureFatFrameworks()
    }

    /**
     * Whether SKIE owns the Swift of this project's frameworks, in which case this plugin does
     * nothing at all.
     *
     * It warns on the way out rather than stepping aside quietly: bundling was asked for, and a
     * framework silently built without its Swift is the harder thing to diagnose.
     */
    private fun standsDownForSkie(): Boolean {
        val status = SkieDetection.statusOf(project)
        val standsDown = status != SkieDetection.Status.INACTIVE

        if (standsDown) {
            project.logger.warn(SkieDetection.conflictWarning(project.path, status))
        }

        return standsDown
    }

    /**
     * A fat framework is produced by `lipo`, which merges binaries only: the bundle structure is
     * taken from a single input framework, so the Swift modules of the other architectures have to
     * be copied in afterwards.
     */
    private fun configureFatFrameworks() {
        // Deferred until the task graph is ready: the frameworks a fat framework task merges are
        // only known once the Kotlin Gradle Plugin has finished configuring the XCFramework.
        project.gradle.taskGraph.whenReady {
            project.tasks.withType(FatFrameworkTask::class.java).configureEach { task ->
                val slices =
                    task.frameworks.mapNotNull { descriptor ->
                        appleTargets[descriptor.target.name]?.let { appleTarget ->
                            FatFrameworkSlice(
                                frameworkDirectory = descriptor.file,
                                targetTriple = appleTarget.targetTriple,
                                architectureClangMacro = appleTarget.architectureClangMacro,
                            )
                        }
                    }

                val isMacosFramework =
                    task.frameworks.firstOrNull()?.let { appleTargets[it.target.name]?.isMacos } ?: false

                task.doLast(MergeBundledSwiftIntoFatFramework(task.fatFramework, isMacosFramework, slices))
            }
        }
    }

    /**
     * A resolvable configuration of its own for the compiler plugin, so that resolving it cannot
     * disturb anything the consumer declared.
     *
     * `isVisible` is deliberately not set: it only ever controlled legacy `uploadArchives`
     * publishing, and Gradle deprecated it for removal.
     */
    private fun createCompilerPluginConfiguration(): Configuration =
        project.configurations.maybeCreate(SwiftBundling.COMPILER_PLUGIN_CONFIGURATION).apply {
            isCanBeConsumed = false
            isCanBeResolved = true

            project.dependencies.add(name, CompilerPluginArtifact.dependencyNotation(project.getKotlinPluginVersion()))
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

        // An XCFramework is consumed from another machine and possibly another Swift compiler, so
        // its frameworks need a stable ABI whether or not the build asked for one.
        val isForXCFramework = framework.outputFile.canonicalPath in xcFrameworkInputPaths
        val libraryEvolution = extension.enableSwiftLibraryEvolution.get() || isForXCFramework

        if (isForXCFramework && !extension.enableSwiftLibraryEvolution.get()) {
            project.logger.info(
                "Enabling Swift library evolution for {} because it is assembled into an XCFramework.",
                framework.name,
            )
        }

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

            linkTask.addSwiftBundlingOptions(
                swiftSourcesDirectory = swiftSourcesDirectory.get().asFile.absolutePath,
                workDirectory = workDirectory.get().asFile.absolutePath,
                libraryEvolution = libraryEvolution,
            )

            if (extension.enableRelativeSourcePathsInDebugSymbols.get()) {
                // The Kotlin half of the same feature: without it only the Swift sources would have
                // relative paths in the debug symbols.
                linkTask.toolOptions.freeCompilerArgs.add("-Xdebug-prefix-map=${project.rootDir.absolutePath}=.")
            }
        }
    }

    /** Hands the compiler plugin everything it needs, as `-P` options on the link task. */
    private fun KotlinNativeLink.addSwiftBundlingOptions(
        swiftSourcesDirectory: String,
        workDirectory: String,
        libraryEvolution: Boolean,
    ) {
        fun option(
            key: String,
            value: String,
        ) = compilerPluginOptions.addPluginArgument(SwiftBundling.COMPILER_PLUGIN_ID, SubpluginOption(key, value))

        option(SwiftBundling.OPTION_SWIFT_SOURCES_DIR, swiftSourcesDirectory)
        option(SwiftBundling.OPTION_WORK_DIR, workDirectory)
        option(SwiftBundling.OPTION_SWIFT_VERSION, extension.swiftVersion.get())
        option(SwiftBundling.OPTION_SWIFT_LIBRARY_EVOLUTION, libraryEvolution.toString())
        option(
            SwiftBundling.OPTION_NO_CLANG_MODULE_BREADCRUMBS,
            extension.noClangModuleBreadcrumbsInStaticFrameworks.get().toString(),
        )
        option(
            SwiftBundling.OPTION_RELATIVE_SOURCE_PATHS,
            extension.enableRelativeSourcePathsInDebugSymbols.get().toString(),
        )

        extension.freeSwiftCompilerArgs.get().forEach { option(SwiftBundling.OPTION_FREE_COMPILER_ARGS, it) }
    }

    private companion object {
        const val MAIN_COMPILATION_NAME = "main"
    }
}
