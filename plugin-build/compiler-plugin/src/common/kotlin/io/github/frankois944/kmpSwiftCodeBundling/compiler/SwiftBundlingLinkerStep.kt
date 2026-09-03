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
 *     SKIE/kotlin-compiler/linker-plugin/src/main/kotlin/co/touchlab/skie/entrypoint/LinkerPhaseInterceptor.kt
 *     SKIE/kotlin-compiler/core/src/main/kotlin/co/touchlab/skie/phases/swift/CompileSwiftPhase.kt
 *     SKIE/kotlin-compiler/core/src/main/kotlin/co/touchlab/skie/phases/swift/CopySwiftOutputFilesToFrameworkPhase.kt
 *     SKIE/kotlin-compiler/core/src/main/kotlin/co/touchlab/skie/phases/other/GenerateModulemapFilePhase.kt
 *     SKIE .../skie/phases/other/ConfigureSwiftSpecificLinkerArgsPhase.kt (linker-plugin/src/main)
 *     SKIE/kotlin-compiler/core/src/main/kotlin/co/touchlab/skie/phases/other/LinkObjectFilesPhase.kt
 *
 * Changes made in this file: those phases are merged into a single step driven from the linker phase, Swift is compiled
 * whole-module instead of through an incremental output file map, and only bundled Swift sources are
 * compiled - there is no generated Swift API here.
 */
@file:Suppress("invisible_reference", "invisible_member")

package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.backend.konan.driver.phases.LinkerPhaseInput
import org.jetbrains.kotlin.konan.target.AppleConfigurables
import org.jetbrains.kotlin.konan.target.platformName
import java.io.File

/**
 * Compiles the Swift sources bundled in the klibs of this binary and hands the resulting object
 * file to the Kotlin/Native linker, then installs the Swift module artifacts into the produced
 * framework so that `import <Framework>` exposes both the Kotlin and the Swift declarations.
 */
internal class SwiftBundlingLinkerStep(
    private val options: SwiftBundlingOptions,
) {
    fun run(
        context: PhaseContext,
        input: LinkerPhaseInput,
        next: (PhaseContext, LinkerPhaseInput) -> Unit,
    ) {
        val konanConfig = context.konanConfig
        val framework = konanConfig.frameworkLayout()

        if (framework == null) {
            next(context, input)
            return
        }

        val swiftSources = collectSwiftSources()

        if (swiftSources.isEmpty()) {
            framework.removeSwiftModuleArtifacts()
            next(context, input)
            return
        }

        val configurables =
            konanConfig.platform.configurables as? AppleConfigurables
                ?: error("Swift code bundling is only supported for Apple targets.")

        val targetTriple = configurables.bundlingTargetTriple()
        val compilation = compileSwift(konanConfig, configurables, targetTriple, framework, swiftSources)

        konanConfig.addSwiftRuntimeLinkerArguments(configurables)

        next(context, input.copy(objectFiles = input.objectFiles + compilation.objectFiles.map { it.absolutePath }))

        installIntoFramework(framework, targetTriple, compilation)
    }

    private fun collectSwiftSources(): List<File> =
        options.swiftSourcesDirectory
            .walkTopDown()
            .filter { it.isFile && it.extension == "swift" }
            .sortedBy { it.absolutePath }
            .toList()

    /**
     * The framework being produced, or null when this compilation is not producing one.
     *
     * Recognised from the output path rather than from the compiler's output kind: the enum is one
     * more internal API to keep working across Kotlin versions, and an existing `.framework`
     * directory is just as conclusive.
     */
    private fun KonanConfig.frameworkLayout(): FrameworkLayout? {
        val outputPath = configuration.get(frameworkOutputPathKey) ?: return null
        val directory =
            if (outputPath.endsWith(FRAMEWORK_SUFFIX)) File(outputPath) else File(outputPath + FRAMEWORK_SUFFIX)

        val isMacos = (platform.configurables as? AppleConfigurables)?.bundlingTargetTriple()?.isMacos == true

        return directory
            .takeIf { it.isDirectory }
            ?.let { FrameworkLayout(it, isMacosFramework = isMacos) }
    }

    private fun AppleConfigurables.bundlingTargetTriple(): TargetTriple =
        with(targetTriple) {
            TargetTriple(
                architecture = architecture,
                vendor = vendor,
                os = os,
                environment = environment,
            )
        }

    private fun compileSwift(
        konanConfig: KonanConfig,
        configurables: AppleConfigurables,
        targetTriple: TargetTriple,
        framework: FrameworkLayout,
        swiftSources: List<File>,
    ): SwiftCompilationOutput {
        val kotlinFramework = prepareFrameworkForSwiftCompilation(framework)

        val moduleDirectory = options.workDirectory.resolve("module").also { it.mkdirs() }
        val objectDirectory = options.workDirectory.resolve("objects").also { it.mkdirs() }
        val configDirectory = options.workDirectory.resolve("config").also { it.mkdirs() }

        val isDebug = konanConfig.debug
        val moduleName = framework.frameworkName

        val units = SwiftSourceUnit.from(swiftSources, options.swiftSourcesDirectory)
        val output = SwiftCompilationOutput.of(moduleName, moduleDirectory, objectDirectory, units, isDebug)

        val outputFileMap = configDirectory.resolve("output-file-map.json")
        outputFileMap.writeText(
            SwiftOutputFileMap.content(moduleName, moduleDirectory, objectDirectory, units, isDebug),
        )

        val sourceList = configDirectory.resolve("swift-sources.txt")
        SwiftCompilerCommand.writeSourceList(units, sourceList)

        val expectedBaseNames = if (isDebug) units.mapTo(mutableSetOf()) { it.baseName } else setOf(moduleName)
        SwiftIntermediates.deleteStale(objectDirectory, expectedBaseNames)

        val arguments =
            SwiftCompilerCommand.arguments(
                invocation =
                    SwiftCompilerInvocation(
                        swiftcPath = File(configurables.absoluteTargetToolchain, "bin/swiftc").absolutePath,
                        moduleName = moduleName,
                        frameworkSearchPath = kotlinFramework.parentDirectory.absolutePath,
                        sdkPath = configurables.absoluteTargetSysRoot,
                        target = targetTriple.withOsVersion(configurables.osVersionMin).toString(),
                        isDebug = isDebug,
                        isStaticFramework = konanConfig.isStaticFramework,
                    ),
                options = options,
                output = output,
                outputFileMap = outputFileMap,
                sourceList = sourceList,
                moduleCache = options.workDirectory.resolve("module-cache"),
            )

        Command(arguments).execute(
            workingDirectory = options.swiftSourcesDirectory,
            logFile = options.workDirectory.resolve("logs/swiftc.log"),
        )

        return output
    }

    /**
     * swiftc compiles the bundled code as an overlay of the Kotlin framework's Clang module, which
     * means it has to read that framework while it is still being built. It is given a copy holding
     * only the headers and the module map, so that the module map the framework ends up shipping -
     * the one declaring the Swift submodule produced here - can never be read back as an input.
     */
    private fun prepareFrameworkForSwiftCompilation(framework: FrameworkLayout): FrameworkLayout {
        require(framework.kotlinHeader.exists()) {
            "The Kotlin framework header ${framework.kotlinHeader} was not found. " +
                "Swift code bundling expects the Objective-C headers to be generated before the linker runs."
        }
        require(framework.modulemapFile.exists()) {
            "The Kotlin framework module map ${framework.modulemapFile} was not found. " +
                "Swift code bundling expects the Objective-C headers to be generated before the linker runs."
        }

        val root = options.workDirectory.resolve("kotlin-framework")
        val copy =
            FrameworkLayout(root.resolve(framework.frameworkDirectory.name), isMacosFramework = false, isFlat = true)

        copy.headersDirectory.mkdirs()
        copy.modulesDirectory.mkdirs()

        // Copied only when the content actually differs. Rewriting an identical file would move its
        // timestamp, and the Swift compiler decides what to recompile from timestamps - an
        // unconditional copy would defeat the incremental build on every single run.
        framework.kotlinHeader.copyToIfDifferent(copy.kotlinHeader)

        if (framework.apiNotes.exists()) {
            framework.apiNotes.copyToIfDifferent(copy.apiNotes)
        }

        copy.modulemapFile.writeTextIfDifferent(
            BundledSwiftModuleMap.withoutBundledModule(framework.modulemapFile.readText()),
        )

        return copy
    }

    private fun installIntoFramework(
        framework: FrameworkLayout,
        targetTriple: TargetTriple,
        compilation: SwiftCompilationOutput,
    ) {
        framework.swiftModuleDirectory.mkdirs()

        compilation.copyToFrameworkArtifacts(framework, targetTriple)
        compilation.swiftHeader.copyTo(framework.swiftHeader, overwrite = true)

        framework.modulemapFile.writeText(
            BundledSwiftModuleMap.withBundledModule(
                modulemap = framework.modulemapFile.readText(),
                moduleName = framework.frameworkName,
                swiftHeaderName = framework.swiftHeader.name,
            ),
        )
    }

    private fun SwiftCompilationOutput.copyToFrameworkArtifacts(
        framework: FrameworkLayout,
        targetTriple: TargetTriple,
    ) {
        val artifacts =
            listOf(
                swiftModule to "swiftmodule",
                swiftDoc to "swiftdoc",
                swiftSourceInfo to "swiftsourceinfo",
                abiJson to "abi.json",
                swiftInterface to "swiftinterface",
                privateSwiftInterface to "private.swiftinterface",
            )

        artifacts.forEach { (source, extension) ->
            val destination = framework.swiftModuleArtifact(targetTriple, extension)

            if (source.exists()) {
                source.copyTo(destination, overwrite = true)
            } else {
                destination.delete()
            }
        }
    }

    private fun FrameworkLayout.removeSwiftModuleArtifacts() {
        swiftModuleDirectory.deleteRecursively()
        swiftHeader.delete()

        if (modulemapFile.exists()) {
            modulemapFile.writeText(BundledSwiftModuleMap.withoutBundledModule(modulemapFile.readText()))
        }
    }

    private fun KonanConfig.addSwiftRuntimeLinkerArguments(configurables: AppleConfigurables) {
        val searchPaths =
            listOf(
                File(configurables.absoluteTargetToolchain, "lib/swift/${configurables.platformName().lowercase()}"),
                File(configurables.absoluteTargetSysRoot, "usr/lib/swift"),
            ).flatMap { listOf("-L", it.absolutePath) }

        configuration.addAll(KonanConfigKeys.LINKER_ARGS, searchPaths)
        configuration.addAll(KonanConfigKeys.LINKER_ARGS, listOf("-rpath", "/usr/lib/swift"))
    }

    private val KonanConfig.isStaticFramework: Boolean
        get() = configuration.getBoolean(KonanConfigKeys.STATIC_FRAMEWORK)

    private companion object {
        const val FRAMEWORK_SUFFIX = ".framework"
    }
}
