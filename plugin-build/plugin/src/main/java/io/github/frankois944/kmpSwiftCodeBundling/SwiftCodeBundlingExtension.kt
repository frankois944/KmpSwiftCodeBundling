package io.github.frankois944.kmpSwiftCodeBundling

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@Suppress("AbstractClassCanBeConcreteClass")
abstract class SwiftCodeBundlingExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /** Enables bundling of the Swift sources found in `src/<sourceSet>/swift`. */
        val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

        /** Swift language version passed to `swiftc` via `-swift-version`. */
        val swiftVersion: Property<String> = objects.property(String::class.java).convention("5")

        /**
         * Enables Swift library evolution.
         *
         * Building with library evolution increases compilation time, so use it only when the
         * framework is compiled against on another machine - notably when it is distributed inside
         * an XCFramework.
         */
        val enableSwiftLibraryEvolution: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

        /**
         * Passes `-no-clang-module-breadcrumbs` to the Swift compiler front end when building a
         * static framework.
         *
         * Without it a redistributable static framework carries DWARF skeleton CUs pointing at a
         * module cache that does not exist on the consuming machine, which degrades debugging and
         * produces warnings of the form `..../xyz.pcm: No such file or directory`.
         */
        val noClangModuleBreadcrumbsInStaticFrameworks: Property<Boolean> =
            objects.property(Boolean::class.java).convention(false)

        /**
         * Records source file paths in the debug symbols relative to the root project rather than
         * absolute, so that a framework built on one machine can be debugged on another.
         *
         * Adds `-Xdebug-prefix-map=<rootDir>=.` to the link task and `-file-compilation-dir .` to
         * the Swift compilation, and works around a Kotlin/Native bug that would otherwise drop the
         * links to the Kotlin sources.
         */
        val enableRelativeSourcePathsInDebugSymbols: Property<Boolean> =
            objects.property(Boolean::class.java).convention(false)

        /** Extra arguments appended to the `swiftc` invocation. */
        val freeSwiftCompilerArgs: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

        /**
         * Configures the plugin to produce a framework that can be compiled against on another
         * machine - when publishing an XCFramework, for instance.
         *
         * It is unnecessary when the framework is only consumed locally, including as a dependency
         * of a binary that is itself distributed.
         */
        fun produceDistributableFramework() {
            enableSwiftLibraryEvolution.set(true)
            noClangModuleBreadcrumbsInStaticFrameworks.set(true)
            enableRelativeSourcePathsInDebugSymbols.set(true)
        }
    }
