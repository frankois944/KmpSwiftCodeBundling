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
 *     SKIE/kotlin-compiler/linker-plugin/src/main/kotlin/co/touchlab/skie/phases/other/ConfigureSwiftSpecificLinkerArgsPhase.kt
 *     SKIE/kotlin-compiler/core/src/main/kotlin/co/touchlab/skie/phases/other/LinkObjectFilesPhase.kt
 *
 * Changes made in this file: those phases are merged into a single step driven from the linker phase, Swift is compiled
 * whole-module instead of through an incremental output file map, and only bundled Swift sources are
 * compiled - there is no generated Swift API here.
 */
@file:Suppress("invisible_reference", "invisible_member")

package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.backend.konan.KonanConfig
import org.jetbrains.kotlin.backend.konan.KonanConfigKeys
import org.jetbrains.kotlin.backend.konan.driver.PhaseContext
import org.jetbrains.kotlin.backend.konan.driver.phases.LinkerPhaseInput
import org.jetbrains.kotlin.konan.target.AppleConfigurables
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
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
        val konanConfig = context.config
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

    private fun KonanConfig.frameworkLayout(): FrameworkLayout? {
        if (produce != CompilerOutputKind.FRAMEWORK) {
            return null
        }

        val outputPath = configuration.get(KonanConfigKeys.OUTPUT) ?: return null
        val directory = if (outputPath.endsWith(FRAMEWORK_SUFFIX)) File(outputPath) else File(outputPath + FRAMEWORK_SUFFIX)
        val isMacos = (platform.configurables as? AppleConfigurables)?.bundlingTargetTriple()?.isMacos == true

        return FrameworkLayout(directory, isMacosFramework = isMacos)
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

        // Paths are relative to the working directory set below. The output file map is keyed by the
        // very same strings, which is what lets the Swift compiler match a source to its outputs.
        val units =
            swiftSources.map { source ->
                SwiftSourceUnit(
                    relativePath = source.relativeTo(options.swiftSourcesDirectory).path,
                    baseName = source.nameWithoutExtension,
                )
            }

        val objectFiles =
            if (isDebug) {
                units.map { objectDirectory.resolve("${it.baseName}.o") }
            } else {
                listOf(objectDirectory.resolve("$moduleName.o"))
            }

        val output =
            SwiftCompilationOutput(
                objectFiles = objectFiles,
                swiftModule = moduleDirectory.resolve("$moduleName.swiftmodule"),
                swiftDoc = moduleDirectory.resolve("$moduleName.swiftdoc"),
                swiftSourceInfo = moduleDirectory.resolve("$moduleName.swiftsourceinfo"),
                abiJson = moduleDirectory.resolve("$moduleName.abi.json"),
                swiftInterface = moduleDirectory.resolve("$moduleName.swiftinterface"),
                privateSwiftInterface = moduleDirectory.resolve("$moduleName.private.swiftinterface"),
                swiftHeader = moduleDirectory.resolve("$moduleName-Swift.h"),
            )

        val outputFileMap = configDirectory.resolve("output-file-map.json")
        outputFileMap.writeText(outputFileMapContent(moduleName, moduleDirectory, objectDirectory, units, isDebug))

        // A response file rather than the command line: a module with many sources would otherwise
        // risk overflowing the argument limit.
        val sourceList = configDirectory.resolve("swift-sources.txt")
        sourceList.writeText(units.joinToString("\n") { "'${it.relativePath}'" })

        deleteStaleIntermediates(objectDirectory, if (isDebug) units.mapTo(mutableSetOf()) { it.baseName } else setOf(moduleName))

        val arguments =
            buildList {
                add(File(configurables.absoluteTargetToolchain, "bin/swiftc").absolutePath)
                addAll(listOf("-module-name", moduleName))
                add("-import-underlying-module")
                addAll(listOf("-F", kotlinFramework.parentDirectory.absolutePath))
                add("-emit-module")
                addAll(listOf("-emit-module-path", output.swiftModule.absolutePath))
                add("-emit-objc-header")
                addAll(listOf("-emit-objc-header-path", output.swiftHeader.absolutePath))
                add("-emit-object")
                add("-parse-as-library")

                if (isDebug) {
                    // Incremental mode: the compiler reads the `.swiftdeps` left by the previous run
                    // and recompiles only what changed. This is why the unpacked sources are synced
                    // rather than rewritten - the Swift compiler decides from timestamps.
                    add("-Onone")
                    add("-incremental")
                    add("-enable-batch-mode")
                    add("-j${Runtime.getRuntime().availableProcessors()}")
                } else {
                    add("-O")
                    add("-whole-module-optimization")
                }

                addAll(listOf("-output-file-map", outputFileMap.absolutePath))
                add("-g")
                addAll(listOf("-module-cache-path", options.workDirectory.resolve("module-cache").absolutePath))
                addAll(listOf("-swift-version", options.swiftVersion))
                addAll(listOf("-sdk", configurables.absoluteTargetSysRoot))
                addAll(listOf("-target", targetTriple.withOsVersion(configurables.osVersionMin).toString()))

                if (options.swiftLibraryEvolution) {
                    add("-enable-library-evolution")
                    add("-verify-emitted-module-interface")
                    addAll(listOf("-emit-module-interface-path", output.swiftInterface.absolutePath))
                    addAll(listOf("-emit-private-module-interface-path", output.privateSwiftInterface.absolutePath))
                }

                if (options.noClangModuleBreadcrumbsInStaticFrameworks && konanConfig.isStaticFramework) {
                    // Keeps DWARF skeleton CUs for imported Clang modules out of a redistributable
                    // static framework, whose module cache does not exist on the consuming machine.
                    addAll(listOf("-Xfrontend", "-no-clang-module-breadcrumbs"))
                }

                if (options.relativeSourcePathsInDebugSymbols) {
                    addAll(listOf("-file-compilation-dir", "."))
                }

                addAll(options.freeCompilerArgs)
                add("@${sourceList.absolutePath}")
            }

        Command(arguments).execute(
            workingDirectory = options.swiftSourcesDirectory,
            logFile = options.workDirectory.resolve("logs/swiftc.log"),
        )

        return output
    }

    /**
     * Tells the Swift compiler where to put the outputs of each source file.
     *
     * In debug this is what makes the build incremental. Two things are needed, and both were found
     * the hard way with `-driver-show-incremental`:
     *
     * - The root entry must declare `swift-dependencies`. The driver uses it as the path of its
     *   build record; without it the whole thing is skipped with "Disabling incremental build: no
     *   build record path".
     * - Per-file entries must declare only outputs the compiler actually writes. The driver checks
     *   every declared output before skipping a file, so a `.d` without `-emit-dependencies`, or the
     *   `~partial.swiftmodule` that modern Swift no longer emits when the module is written
     *   directly, makes it report "Missing an output" and recompile everything, every build.
     *
     * A release build compiles the whole module at once, so the root entry carries a single object
     * instead.
     */
    private fun outputFileMapContent(
        moduleName: String,
        moduleDirectory: File,
        objectDirectory: File,
        units: List<SwiftSourceUnit>,
        isDebug: Boolean,
    ): String {
        val entries =
            buildList {
                if (isDebug) {
                    val buildRecord = moduleDirectory.resolve("$moduleName.swiftdeps")

                    add("""  "": { "swift-dependencies": ${buildRecord.jsonString()} }""")

                    units.forEach { unit ->
                        val fields =
                            listOf(
                                """"object": ${objectDirectory.resolve("${unit.baseName}.o").jsonString()}""",
                                """"swift-dependencies": ${objectDirectory.resolve("${unit.baseName}.swiftdeps").jsonString()}""",
                            )

                        add("""  ${unit.relativePath.jsonString()}: { ${fields.joinToString(", ")} }""")
                    }
                } else {
                    add("""  "": { "object": ${objectDirectory.resolve("$moduleName.o").jsonString()} }""")
                }
            }

        return entries.joinToString(",\n", prefix = "{\n", postfix = "\n}\n")
    }

    /**
     * Removes the outputs of sources that no longer exist.
     *
     * They would otherwise keep being handed to the linker, so a deleted Swift file would stay in
     * the framework until the build directory is wiped.
     *
     * Only the last extension is stripped: unpacked sources are named `bundled.<origin>.<Source>`,
     * so cutting at the first dot would match nothing and wipe every object file on every build -
     * which the Swift driver then reports as "Missing an output", recompiling everything.
     */
    private fun deleteStaleIntermediates(
        objectDirectory: File,
        expectedBaseNames: Set<String>,
    ) {
        objectDirectory
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .filterNot { it.baseNameOfIntermediate() in expectedBaseNames }
            .forEach { it.delete() }
    }

    private fun File.baseNameOfIntermediate(): String = name.substringBeforeLast(".").removeSuffix("~partial")

    private fun File.jsonString(): String = absolutePath.jsonString()

    private fun String.jsonString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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
        val copy = FrameworkLayout(root.resolve(framework.frameworkDirectory.name), isMacosFramework = false, isFlat = true)

        copy.headersDirectory.mkdirs()
        copy.modulesDirectory.mkdirs()

        // Copied only when the content actually differs. Rewriting an identical file would move its
        // timestamp, and the Swift compiler decides what to recompile from timestamps - an
        // unconditional copy would defeat the incremental build on every single run.
        framework.kotlinHeader.copyToIfDifferent(copy.kotlinHeader)

        if (framework.apiNotes.exists()) {
            framework.apiNotes.copyToIfDifferent(copy.apiNotes)
        }

        copy.modulemapFile.writeTextIfDifferent(framework.modulemapFile.readText().withoutBundledSwiftModule())

        return copy
    }

    private fun File.copyToIfDifferent(destination: File) {
        if (destination.exists() && destination.length() == length() && destination.readBytes().contentEquals(readBytes())) {
            return
        }

        copyTo(destination, overwrite = true)
    }

    private fun File.writeTextIfDifferent(content: String) {
        if (exists() && readText() == content) {
            return
        }

        writeText(content)
    }

    private fun installIntoFramework(
        framework: FrameworkLayout,
        targetTriple: TargetTriple,
        compilation: SwiftCompilationOutput,
    ) {
        framework.swiftModuleDirectory.mkdirs()

        compilation.copyToFrameworkArtifacts(framework, targetTriple)
        compilation.swiftHeader.copyTo(framework.swiftHeader, overwrite = true)

        val modulemap = framework.modulemapFile.readText().withoutBundledSwiftModule()

        framework.modulemapFile.writeText(
            modulemap.trimEnd() + "\n\n" + bundledSwiftModuleDeclaration(framework),
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
            modulemapFile.writeText(modulemapFile.readText().withoutBundledSwiftModule())
        }
    }

    private fun bundledSwiftModuleDeclaration(framework: FrameworkLayout): String =
        """
        $MODULEMAP_MARKER_BEGIN
        module ${framework.frameworkName}.Swift {
            header "${framework.swiftHeader.name}"
            requires objc
        }
        $MODULEMAP_MARKER_END
        """.trimIndent() + "\n"

    private fun String.withoutBundledSwiftModule(): String {
        val begin = indexOf(MODULEMAP_MARKER_BEGIN)

        if (begin < 0) {
            return this
        }

        val end = indexOf(MODULEMAP_MARKER_END, startIndex = begin)
        val after = if (end < 0) length else end + MODULEMAP_MARKER_END.length

        return (substring(0, begin) + substring(after)).trimEnd() + "\n"
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
        const val MODULEMAP_MARKER_BEGIN = "// BEGIN kmpSwiftCodeBundling"
        const val MODULEMAP_MARKER_END = "// END kmpSwiftCodeBundling"
    }
}

internal data class SwiftSourceUnit(
    val relativePath: String,
    val baseName: String,
)

internal class SwiftCompilationOutput(
    val objectFiles: List<File>,
    val swiftModule: File,
    val swiftDoc: File,
    val swiftSourceInfo: File,
    val abiJson: File,
    val swiftInterface: File,
    val privateSwiftInterface: File,
    val swiftHeader: File,
)
