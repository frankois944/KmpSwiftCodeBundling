package io.github.frankois944.kmpSwiftCodeBundling.compiler

/**
 * Ids shared between the Gradle plugin and this compiler plugin.
 *
 * Keep in sync with `io.github.frankois944.kmpSwiftCodeBundling.SwiftBundling` on the Gradle side.
 * The two modules are deliberately not linked together: this one must stay free of any Gradle class
 * because it is loaded inside the Kotlin/Native compiler.
 */
object SwiftBundlingPluginIds {
    const val COMPILER_PLUGIN_ID: String = "io.github.frankois944.kmpSwiftCodeBundling"

    const val OPTION_SWIFT_SOURCES_DIR: String = "swiftSourcesDir"
    const val OPTION_WORK_DIR: String = "workDir"
    const val OPTION_SWIFT_VERSION: String = "swiftVersion"
    const val OPTION_SWIFT_LIBRARY_EVOLUTION: String = "swiftLibraryEvolution"
    const val OPTION_NO_CLANG_MODULE_BREADCRUMBS: String = "noClangModuleBreadcrumbsInStaticFrameworks"
    const val OPTION_RELATIVE_SOURCE_PATHS: String = "relativeSourcePathsInDebugSymbols"
    const val OPTION_FREE_COMPILER_ARGS: String = "freeSwiftCompilerArgs"
}
