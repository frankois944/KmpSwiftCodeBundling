package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

class SwiftBundlingCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = SwiftBundlingPluginIds.COMPILER_PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> =
        listOf(
            CliOption(
                optionName = SwiftBundlingPluginIds.OPTION_SWIFT_SOURCES_DIR,
                valueDescription = "path",
                description = "Directory containing the Swift sources to bundle into the framework",
                required = true,
            ),
            CliOption(
                optionName = SwiftBundlingPluginIds.OPTION_WORK_DIR,
                valueDescription = "path",
                description = "Directory used for the Swift compiler intermediate outputs",
                required = true,
            ),
            CliOption(
                optionName = SwiftBundlingPluginIds.OPTION_SWIFT_VERSION,
                valueDescription = "version",
                description = "Swift language version passed to swiftc (-swift-version)",
                required = false,
            ),
            CliOption(
                optionName = SwiftBundlingPluginIds.OPTION_SWIFT_LIBRARY_EVOLUTION,
                valueDescription = "true|false",
                description = "Compiles the bundled Swift code with library evolution enabled",
                required = false,
            ),
            CliOption(
                optionName = SwiftBundlingPluginIds.OPTION_NO_CLANG_MODULE_BREADCRUMBS,
                valueDescription = "true|false",
                description = "Passes -no-clang-module-breadcrumbs when building a static framework",
                required = false,
            ),
            CliOption(
                optionName = SwiftBundlingPluginIds.OPTION_RELATIVE_SOURCE_PATHS,
                valueDescription = "true|false",
                description = "Makes the source file paths recorded in the debug symbols relative",
                required = false,
            ),
            CliOption(
                optionName = SwiftBundlingPluginIds.OPTION_FREE_COMPILER_ARGS,
                valueDescription = "arg",
                description = "Additional swiftc argument (may be repeated)",
                required = false,
                allowMultipleOccurrences = true,
            ),
        )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            SwiftBundlingPluginIds.OPTION_SWIFT_SOURCES_DIR ->
                configuration.put(SwiftBundlingConfigurationKeys.SWIFT_SOURCES_DIR, value)

            SwiftBundlingPluginIds.OPTION_WORK_DIR ->
                configuration.put(SwiftBundlingConfigurationKeys.WORK_DIR, value)

            SwiftBundlingPluginIds.OPTION_SWIFT_VERSION ->
                configuration.put(SwiftBundlingConfigurationKeys.SWIFT_VERSION, value)

            SwiftBundlingPluginIds.OPTION_SWIFT_LIBRARY_EVOLUTION ->
                configuration.put(SwiftBundlingConfigurationKeys.SWIFT_LIBRARY_EVOLUTION, value.toBoolean())

            SwiftBundlingPluginIds.OPTION_NO_CLANG_MODULE_BREADCRUMBS ->
                configuration.put(SwiftBundlingConfigurationKeys.NO_CLANG_MODULE_BREADCRUMBS, value.toBoolean())

            SwiftBundlingPluginIds.OPTION_RELATIVE_SOURCE_PATHS ->
                configuration.put(SwiftBundlingConfigurationKeys.RELATIVE_SOURCE_PATHS, value.toBoolean())

            SwiftBundlingPluginIds.OPTION_FREE_COMPILER_ARGS ->
                configuration.add(SwiftBundlingConfigurationKeys.FREE_COMPILER_ARGS, value)

            else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
        }
    }
}
