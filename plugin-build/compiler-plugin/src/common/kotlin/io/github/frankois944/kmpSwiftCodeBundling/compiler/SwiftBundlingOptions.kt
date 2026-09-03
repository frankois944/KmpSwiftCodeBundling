package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File

internal object SwiftBundlingConfigurationKeys {
    val SWIFT_SOURCES_DIR: CompilerConfigurationKey<String> =
        CompilerConfigurationKey.create(SwiftBundlingPluginIds.OPTION_SWIFT_SOURCES_DIR)

    val WORK_DIR: CompilerConfigurationKey<String> =
        CompilerConfigurationKey.create(SwiftBundlingPluginIds.OPTION_WORK_DIR)

    val SWIFT_VERSION: CompilerConfigurationKey<String> =
        CompilerConfigurationKey.create(SwiftBundlingPluginIds.OPTION_SWIFT_VERSION)

    val SWIFT_LIBRARY_EVOLUTION: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create(SwiftBundlingPluginIds.OPTION_SWIFT_LIBRARY_EVOLUTION)

    val NO_CLANG_MODULE_BREADCRUMBS: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create(SwiftBundlingPluginIds.OPTION_NO_CLANG_MODULE_BREADCRUMBS)

    val RELATIVE_SOURCE_PATHS: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create(SwiftBundlingPluginIds.OPTION_RELATIVE_SOURCE_PATHS)

    val FREE_COMPILER_ARGS: CompilerConfigurationKey<List<String>> =
        CompilerConfigurationKey.create(SwiftBundlingPluginIds.OPTION_FREE_COMPILER_ARGS)
}

class SwiftBundlingOptions(
    val swiftSourcesDirectory: File,
    val workDirectory: File,
    val swiftVersion: String,
    val swiftLibraryEvolution: Boolean,
    val noClangModuleBreadcrumbsInStaticFrameworks: Boolean,
    val relativeSourcePathsInDebugSymbols: Boolean,
    val freeCompilerArgs: List<String>,
)
