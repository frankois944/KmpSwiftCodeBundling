package io.github.frankois944.kmpSwiftCodeBundling.compiler

/**
 * The `module <Name>.Swift` entry appended to the framework's module map.
 *
 * Delimited by markers so it can be replaced rather than stacked: the framework is rebuilt on every
 * link, and the module map read back as input must never contain the entry this plugin adds.
 */
object BundledSwiftModuleMap {
    const val MARKER_BEGIN: String = "// BEGIN kmpSwiftCodeBundling"
    const val MARKER_END: String = "// END kmpSwiftCodeBundling"

    /** [modulemap] with the bundled Swift module declared, replacing any previous declaration. */
    fun withBundledModule(
        modulemap: String,
        moduleName: String,
        swiftHeaderName: String,
    ): String {
        val declaration =
            """
            $MARKER_BEGIN
            module $moduleName.Swift {
                header "$swiftHeaderName"
                requires objc
            }
            $MARKER_END
            """.trimIndent()

        return withoutBundledModule(modulemap).trimEnd() + "\n\n" + declaration + "\n"
    }

    /** [modulemap] with any declaration this plugin previously added removed. */
    fun withoutBundledModule(modulemap: String): String {
        val begin = modulemap.indexOf(MARKER_BEGIN)

        if (begin < 0) {
            return modulemap
        }

        val end = modulemap.indexOf(MARKER_END, startIndex = begin)
        val after = if (end < 0) modulemap.length else end + MARKER_END.length

        return (modulemap.substring(0, begin) + modulemap.substring(after)).trimEnd() + "\n"
    }
}
