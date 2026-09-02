package io.github.frankois944.kmpSwiftCodeBundling

/**
 * Constants shared with the compiler plugin.
 *
 * Keep in sync with `io.github.frankois944.kmpSwiftCodeBundling.compiler.SwiftBundlingPluginIds`.
 * The compiler plugin cannot be a dependency of this module: it is loaded inside the Kotlin/Native
 * compiler and must never reach the Gradle classpath.
 */
object SwiftBundling {
    const val EXTENSION_NAME: String = "swiftCodeBundling"

    const val COMPILER_PLUGIN_ID: String = "io.github.frankois944.kmpSwiftCodeBundling"

    /** Directory, inside a klib, holding the Swift sources bundled with that klib. */
    const val KLIB_SWIFT_DIRECTORY: String = "default/swift-code-bundling/swift"

    internal const val OPTION_SWIFT_SOURCES_DIR: String = "swiftSourcesDir"
    internal const val OPTION_WORK_DIR: String = "workDir"
    internal const val OPTION_SWIFT_VERSION: String = "swiftVersion"
    internal const val OPTION_SWIFT_LIBRARY_EVOLUTION: String = "swiftLibraryEvolution"
    internal const val OPTION_NO_CLANG_MODULE_BREADCRUMBS: String = "noClangModuleBreadcrumbsInStaticFrameworks"
    internal const val OPTION_RELATIVE_SOURCE_PATHS: String = "relativeSourcePathsInDebugSymbols"
    internal const val OPTION_FREE_COMPILER_ARGS: String = "freeSwiftCompilerArgs"

    internal const val BUILD_DIRECTORY: String = "swift-code-bundling"
    internal const val COMPILER_PLUGIN_CONFIGURATION: String = "kmpSwiftCodeBundlingCompilerPlugin"
}
