package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar

/** Kotlin 2.3 made `pluginId` an abstract member of the registrar. */
abstract class BaseSwiftBundlingRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = SwiftBundlingPluginIds.COMPILER_PLUGIN_ID
}
