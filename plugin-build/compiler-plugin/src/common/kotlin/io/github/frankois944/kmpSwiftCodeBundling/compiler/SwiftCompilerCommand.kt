package io.github.frankois944.kmpSwiftCodeBundling.compiler

import java.io.File

/** What the toolchain and the target impose on a `swiftc` invocation. */
data class SwiftCompilerInvocation(
    val swiftcPath: String,
    val moduleName: String,
    /** Directory holding the copy of the Kotlin framework the Swift code is an overlay of. */
    val frameworkSearchPath: String,
    val sdkPath: String,
    /** Target triple, minimum OS version included. */
    val target: String,
    val isDebug: Boolean,
    val isStaticFramework: Boolean,
)

/** Builds the `swiftc` command line. */
object SwiftCompilerCommand {
    /**
     * Writes the response file the sources are passed in.
     *
     * A response file rather than the command line: a module with many sources would otherwise risk
     * overflowing the argument limit.
     */
    fun writeSourceList(
        units: List<SwiftSourceUnit>,
        destination: File,
    ) {
        destination.writeText(units.joinToString("\n") { "'${it.relativePath}'" })
    }

    fun arguments(
        invocation: SwiftCompilerInvocation,
        options: SwiftBundlingOptions,
        output: SwiftCompilationOutput,
        outputFileMap: File,
        sourceList: File,
        moduleCache: File,
    ): List<String> =
        buildList {
            add(invocation.swiftcPath)
            addAll(listOf("-module-name", invocation.moduleName))
            // Compiles the bundled code as an overlay of the framework's Objective-C module, which
            // is what lets it call the Kotlin API without importing anything.
            add("-import-underlying-module")
            addAll(listOf("-F", invocation.frameworkSearchPath))
            add("-emit-module")
            addAll(listOf("-emit-module-path", output.swiftModule.absolutePath))
            add("-emit-objc-header")
            addAll(listOf("-emit-objc-header-path", output.swiftHeader.absolutePath))
            add("-emit-object")
            add("-parse-as-library")

            addAll(optimisationArguments(invocation.isDebug))

            addAll(listOf("-output-file-map", outputFileMap.absolutePath))
            add("-g")
            addAll(listOf("-module-cache-path", moduleCache.absolutePath))
            addAll(listOf("-swift-version", options.swiftVersion))
            addAll(listOf("-sdk", invocation.sdkPath))
            addAll(listOf("-target", invocation.target))

            addAll(distributionArguments(invocation, options, output))

            if (options.relativeSourcePathsInDebugSymbols) {
                addAll(listOf("-file-compilation-dir", "."))
            }

            addAll(options.freeCompilerArgs)
            add("@${sourceList.absolutePath}")
        }

    /**
     * Debug builds compile incrementally: the compiler reads the `.swiftdeps` left by the previous
     * run and recompiles only what changed. Release builds compile the whole module at once, which
     * is what lets the optimiser work across files.
     */
    private fun optimisationArguments(isDebug: Boolean): List<String> =
        if (isDebug) {
            listOf("-Onone", "-incremental", "-enable-batch-mode", "-j${Runtime.getRuntime().availableProcessors()}")
        } else {
            listOf("-O", "-whole-module-optimization")
        }

    private fun distributionArguments(
        invocation: SwiftCompilerInvocation,
        options: SwiftBundlingOptions,
        output: SwiftCompilationOutput,
    ): List<String> =
        buildList {
            if (options.swiftLibraryEvolution) {
                add("-enable-library-evolution")
                add("-verify-emitted-module-interface")
                addAll(listOf("-emit-module-interface-path", output.swiftInterface.absolutePath))
                addAll(listOf("-emit-private-module-interface-path", output.privateSwiftInterface.absolutePath))
            }

            if (options.noClangModuleBreadcrumbsInStaticFrameworks && invocation.isStaticFramework) {
                // Keeps DWARF skeleton CUs for imported Clang modules out of a redistributable
                // static framework, whose module cache does not exist on the consuming machine.
                addAll(listOf("-Xfrontend", "-no-clang-module-breadcrumbs"))
            }
        }
}
