package io.github.frankois944.kmpSwiftCodeBundling.compiler

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar

/** Kotlin 2.2 has no `pluginId` member on the registrar; declaring one would not compile. */
abstract class BaseSwiftBundlingRegistrar : CompilerPluginRegistrar()
