package io.github.frankois944.kmpSwiftCodeBundling

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Collects the Swift sources of every source set of a compilation into a single directory, which is
 * then stored inside the compilation's klib.
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class ProcessSwiftSourcesTask : DefaultTask() {
    init {
        description = "Collects the Swift sources bundled with this compilation."
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val swiftSources: ConfigurableFileCollection

    /** Directories the sources were looked for in. Reported on `--info` to make an empty result diagnosable. */
    @get:Internal
    abstract val searchedDirectories: ListProperty<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun execute() {
        val sources = swiftSources.files

        logger.info(
            "Bundling {} Swift source(s), looked for in: {}",
            sources.size,
            searchedDirectories.getOrElse(emptyList()).joinToString(", "),
        )

        fileSystemOperations.sync {
            it.duplicatesStrategy = DuplicatesStrategy.FAIL
            it.from(swiftSources)
            it.into(output)
        }

        verifyFileNames()
    }

    private fun verifyFileNames() {
        output
            .get()
            .asFile
            .walkTopDown()
            .filter { it.isFile && it.extension == "swift" }
            .groupBy { it.name }
            .forEach { (name, files) ->
                check(files.size == 1) {
                    "Files $files have the same name '$name'. Swift requires every file of a module to have a " +
                        "unique name, regardless of its path."
                }
            }
    }
}
