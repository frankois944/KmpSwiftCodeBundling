package io.github.frankois944.kmpSwiftCodeBundling.internal

import org.gradle.api.Action
import org.gradle.api.Task
import java.io.File

/**
 * One architecture going into a fat framework. Holds plain values so the merge action stays
 * serializable for the configuration cache.
 */
internal data class FatFrameworkSlice(
    val frameworkDirectory: File,
    val targetTriple: String,
    val architectureClangMacro: String,
)

/**
 * Completes a fat framework with the Swift artifacts of every architecture it was built from.
 *
 * `lipo` merges the binaries, and Kotlin/Native copies the bundle structure of a single input
 * framework, so the fat framework ends up carrying only one architecture's `.swiftmodule`. Swift
 * refuses to import a framework that has no module for the architecture being compiled, so the
 * per-architecture artifacts of the other slices have to be copied in afterwards.
 */
internal class MergeBundledSwiftIntoFatFramework(
    private val fatFramework: File,
    private val isMacosFramework: Boolean,
    private val slices: List<FatFrameworkSlice>,
) : Action<Task> {
    override fun execute(task: Task) {
        val target = FrameworkPaths(fatFramework, isMacosFramework)
        val sources = slices.map { it to FrameworkPaths(it.frameworkDirectory, isMacosFramework) }
        val withSwift = sources.filter { (_, source) -> source.swiftModuleDirectory.isDirectory }

        if (withSwift.isEmpty()) {
            return
        }

        target.swiftModuleDirectory.mkdirs()

        withSwift.forEach { (slice, source) ->
            source.swiftModuleArtifacts(slice.targetTriple).forEach { artifact ->
                if (artifact.exists()) {
                    artifact.copyTo(target.swiftModuleDirectory.resolve(artifact.name), overwrite = true)
                }
            }
        }

        // The module maps only differ by architecture-independent content, so any of them will do.
        withSwift.first().second.modulemapFile.let { modulemap ->
            if (modulemap.exists()) {
                modulemap.copyTo(target.modulemapFile, overwrite = true)
            }
        }

        mergeSwiftHeaders(target, withSwift)
    }

    /**
     * The generated Objective-C header can legitimately differ between architectures - Swift code
     * behind `#if arch(...)` produces different declarations. When it does, the variants are guarded
     * by their Clang architecture macro rather than one being silently picked.
     */
    private fun mergeSwiftHeaders(
        target: FrameworkPaths,
        sources: List<Pair<FatFrameworkSlice, FrameworkPaths>>,
    ) {
        val headers =
            sources
                .filter { (_, source) -> source.swiftHeader.exists() }
                .map { (slice, source) -> slice to source.swiftHeader.readText() }

        if (headers.isEmpty()) {
            return
        }

        target.headersDirectory.mkdirs()

        if (headers.distinctBy { it.second }.size == 1) {
            target.swiftHeader.writeText(headers.first().second)
            return
        }

        target.swiftHeader.writeText(
            buildString {
                headers.forEachIndexed { index, (slice, content) ->
                    val directive = if (index == 0) "#if" else "#elif"

                    appendLine("$directive defined(${slice.architectureClangMacro})")
                    appendLine()
                    appendLine(content)
                }

                appendLine("#else")
                appendLine("#error Unsupported architecture")
                appendLine("#endif")
            },
        )
    }
}
