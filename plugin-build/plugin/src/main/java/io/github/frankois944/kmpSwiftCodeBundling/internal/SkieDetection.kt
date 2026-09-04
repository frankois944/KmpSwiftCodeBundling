package io.github.frankois944.kmpSwiftCodeBundling.internal

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Reads what SKIE is doing to the frameworks of a project.
 *
 * SKIE compiles Swift into the framework the same way this plugin does - it intercepts the
 * Kotlin/Native `LinkerPhase`, runs `swiftc`, and installs the result as the framework's
 * `<Name>.Swift` overlay module. Two plugins doing that to one framework means two `swiftc`
 * invocations writing the same `Modules/<Name>.swiftmodule` and a module map claiming the same
 * overlay module twice, so this plugin steps aside whenever SKIE is active.
 *
 * SKIE is read through reflection: it is not on the classpath here, and must not be.
 */
internal object SkieDetection {
    /** The id SKIE is applied with: `plugins { id("co.touchlab.skie") }`. */
    const val SKIE_PLUGIN_ID: String = "co.touchlab.skie"

    private const val SKIE_PACKAGE_PREFIX = "co.touchlab.skie."
    private const val SKIE_EXTENSION_NAME = "skie"

    /** What SKIE does to the frameworks of a project. */
    enum class Status {
        /** SKIE is not applied, or is applied and turned off with `skie { isEnabled = false }`. */
        INACTIVE,

        /** SKIE is active and reads `src/<sourceSet>/swift` itself, exactly like this plugin. */
        BUNDLING_SWIFT,

        /**
         * SKIE is active with `skie { swiftBundling { enabled = false } }`.
         *
         * It leaves `src/<sourceSet>/swift` alone, but still compiles the Swift it generates into
         * the framework and still owns its `<Name>.Swift` overlay module - so this plugin cannot
         * take that slot either. Turning SKIE's bundling off is not a way to run both.
         */
        GENERATING_SWIFT_ONLY,
    }

    /**
     * What SKIE is set to do in [project].
     *
     * Its configuration is read after the project has been evaluated, when SKIE reads it too.
     * Anything that cannot be read is treated as SKIE's default - active, bundling Swift - so an
     * unknown SKIE version makes this plugin step aside rather than fight it.
     */
    fun statusOf(project: Project): Status {
        if (!isApplied(project)) {
            return Status.INACTIVE
        }

        val extension = project.extensions.findByName(SKIE_EXTENSION_NAME)

        return when {
            extension == null -> Status.BUNDLING_SWIFT
            readBoolean(extension, "isEnabled") == false -> Status.INACTIVE
            isSwiftBundlingDisabled(extension) -> Status.GENERATING_SWIFT_ONLY
            else -> Status.BUNDLING_SWIFT
        }
    }

    /** The warning logged when this plugin disables itself because SKIE owns the framework. */
    fun conflictWarning(
        projectPath: String,
        status: Status,
    ): String {
        val cause =
            when (status) {
                Status.GENERATING_SWIFT_ONLY ->
                    "Its own Swift code bundling is off, but SKIE still compiles the Swift it " +
                        "generates into the framework and still owns the '<Name>.Swift' overlay " +
                        "module this plugin would write, so the two cannot share a framework."

                else ->
                    "Both collect 'src/<sourceSet>/swift' and compile it into the framework's " +
                        "'<Name>.Swift' overlay module by intercepting the Kotlin/Native linker " +
                        "phase, and running them together produces a broken framework."
            }

        return "Swift code bundling is disabled in '$projectPath': the SKIE Gradle plugin " +
            "('$SKIE_PLUGIN_ID') is applied to the same project. $cause Use SKIE's own Swift code " +
            "bundling here, or drop SKIE - removing the plugin, or 'skie { isEnabled = false }' - " +
            "to use this one. Setting 'swiftCodeBundling { enabled.set(false) }' silences this " +
            "warning."
    }

    /** Whether SKIE was told to leave `src/<sourceSet>/swift` alone. */
    private fun isSwiftBundlingDisabled(extension: Any): Boolean {
        val swiftBundling = readMember(extension, "swiftBundling") ?: return false

        return readBoolean(swiftBundling, "enabled") == false
    }

    /**
     * Whether SKIE is applied to [project].
     *
     * The id covers the documented way of applying it; the package check also catches SKIE applied
     * by class and any other plugin published under the same namespace.
     */
    private fun isApplied(project: Project): Boolean =
        project.plugins.hasPlugin(SKIE_PLUGIN_ID) ||
            project.plugins.any { it.javaClass.name.startsWith(SKIE_PACKAGE_PREFIX) }

    /**
     * Reads a Kotlin property of [owner] by name, or null if it is not there.
     *
     * A `val foo` compiles to `getFoo()`, but a `val isFoo` keeps its name, so both spellings are
     * tried. Gradle decorates extensions with a generated subclass, which inherits either.
     */
    private fun readMember(
        owner: Any,
        property: String,
    ): Any? {
        val getters = listOf("get${property.replaceFirstChar(Char::uppercaseChar)}", property)

        return getters.firstNotNullOfOrNull { getter ->
            runCatching { owner.javaClass.getMethod(getter).invoke(owner) }.getOrNull()
        }
    }

    /** Reads a `Boolean` or `Property<Boolean>` member of [owner], or null if it is not readable. */
    private fun readBoolean(
        owner: Any,
        property: String,
    ): Boolean? =
        when (val value = readMember(owner, property)) {
            is Boolean -> value
            is Provider<*> -> runCatching { value.orNull as? Boolean }.getOrNull()
            else -> null
        }
}
