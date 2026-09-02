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

        next(context, input.copy(objectFiles = input.objectFiles + compilation.objectFile.absolutePath))

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

        val output =
            SwiftCompilationOutput(
                objectFile = objectDirectory.resolve("${framework.frameworkName}.o"),
                swiftModule = moduleDirectory.resolve("${framework.frameworkName}.swiftmodule"),
                swiftDoc = moduleDirectory.resolve("${framework.frameworkName}.swiftdoc"),
                swiftSourceInfo = moduleDirectory.resolve("${framework.frameworkName}.swiftsourceinfo"),
                abiJson = moduleDirectory.resolve("${framework.frameworkName}.abi.json"),
                swiftInterface = moduleDirectory.resolve("${framework.frameworkName}.swiftinterface"),
                privateSwiftInterface = moduleDirectory.resolve("${framework.frameworkName}.private.swiftinterface"),
                swiftHeader = moduleDirectory.resolve("${framework.frameworkName}-Swift.h"),
            )

        val arguments =
            buildList {
                add(File(configurables.absoluteTargetToolchain, "bin/swiftc").absolutePath)
                addAll(listOf("-module-name", framework.frameworkName))
                add("-import-underlying-module")
                addAll(listOf("-F", kotlinFramework.parentDirectory.absolutePath))
                add("-emit-module")
                addAll(listOf("-emit-module-path", output.swiftModule.absolutePath))
                add("-emit-objc-header")
                addAll(listOf("-emit-objc-header-path", output.swiftHeader.absolutePath))
                add("-emit-object")
                add("-parse-as-library")
                add("-whole-module-optimization")
                add(if (konanConfig.debug) "-Onone" else "-O")
                addAll(listOf("-o", output.objectFile.absolutePath))
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

                // Paths are relative to the working directory set below, so that `-file-compilation-dir`
                // has something relative to anchor the debug symbols to.
                addAll(swiftSources.map { it.relativeTo(options.swiftSourcesDirectory).path })
            }

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
        val copy = FrameworkLayout(root.resolve(framework.frameworkDirectory.name), isMacosFramework = false, isFlat = true)

        copy.headersDirectory.mkdirs()
        copy.modulesDirectory.mkdirs()

        framework.kotlinHeader.copyTo(copy.kotlinHeader, overwrite = true)

        if (framework.apiNotes.exists()) {
            framework.apiNotes.copyTo(copy.apiNotes, overwrite = true)
        }

        copy.modulemapFile.writeText(framework.modulemapFile.readText().withoutBundledSwiftModule())

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

internal class SwiftCompilationOutput(
    val objectFile: File,
    val swiftModule: File,
    val swiftDoc: File,
    val swiftSourceInfo: File,
    val abiJson: File,
    val swiftInterface: File,
    val privateSwiftInterface: File,
    val swiftHeader: File,
)
