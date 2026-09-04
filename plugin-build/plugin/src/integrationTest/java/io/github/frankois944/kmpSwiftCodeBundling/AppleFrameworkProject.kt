package io.github.frankois944.kmpSwiftCodeBundling

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * A generated Kotlin Multiplatform project that really links an Apple framework.
 *
 * Unlike the functional tests, these builds run the Kotlin/Native toolchain and `swiftc`, so they
 * are skipped anywhere they cannot succeed rather than failing.
 */
internal class AppleFrameworkProject(
    private val root: File,
    private val target: String = "iosSimulatorArm64",
    private val targetTriple: String = "arm64-apple-ios-simulator",
    /** macOS frameworks are versioned bundles; every other Apple platform is flat. */
    private val isVersionedBundle: Boolean = false,
    /**
     * When set, the project resolves both plugins from repositories at this Kotlin version instead
     * of taking them from the classpath TestKit injects. That is the only way to exercise a Kotlin
     * version other than the one this build was compiled against - and it goes through the real
     * plugin marker, exactly as a consumer would.
     *
     * It is also the only mode a third-party plugin can be trusted in: SKIE finds the Kotlin Gradle
     * Plugin by loading `KotlinBasePlugin` from its own class loader and testing the applied plugins
     * against it, which never matches the copy TestKit injects. SKIE then logs that it cannot infer
     * the Kotlin version and quietly does nothing.
     */
    private val kotlinVersion: String? = null,
    /** When set, the project also applies the real SKIE plugin at this version. */
    private val skieVersion: String? = null,
) {
    val frameworkDirectory: File
        get() = root.resolve("build/bin/$target/debugFramework/$FRAMEWORK_NAME.framework")

    private val frameworkContent: File
        get() = if (isVersionedBundle) frameworkDirectory.resolve("Versions/A") else frameworkDirectory

    val frameworkBinary: File
        get() = frameworkContent.resolve(FRAMEWORK_NAME)

    val modulemapFile: File
        get() = frameworkContent.resolve("Modules/module.modulemap")

    val swiftModuleDirectory: File
        get() = frameworkContent.resolve("Modules/$FRAMEWORK_NAME.swiftmodule")

    val swiftModule: File
        get() = swiftModuleDirectory.resolve("$targetTriple.swiftmodule")

    val swiftInterface: File
        get() = swiftModuleDirectory.resolve("$targetTriple.swiftinterface")

    val swiftHeader: File
        get() = frameworkContent.resolve("Headers/$FRAMEWORK_NAME-Swift.h")

    private val workDirectory: File
        get() = root.resolve("build/swift-code-bundling/binaries/$target/debugFramework/work")

    val swiftObjectFiles: File
        get() = workDirectory.resolve("objects")

    /** Holds the swiftc command line and its output, including `-driver-show-incremental` remarks. */
    val swiftcLog: File
        get() = workDirectory.resolve("logs/swiftc.log")

    fun write(
        path: String,
        content: String,
    ): File =
        root.resolve(path).apply {
            parentFile.mkdirs()
            writeText(content.trimIndent())
        }

    /** A single-module project: Kotlin and Swift in the module that produces the framework. */
    fun singleModule(
        pluginConfiguration: String = "",
        isStatic: Boolean = false,
        skieConfiguration: String = "",
    ) {
        writeSettings("")
        write(
            "build.gradle.kts",
            """
            plugins {
                $pluginsBlock
            }

            kotlin {
                $target {
                    binaries.framework {
                        baseName = "$FRAMEWORK_NAME"
                        isStatic = $isStatic
                    }
                }
            }

            swiftCodeBundling {
                $pluginConfiguration
            }

            ${skieBlock(skieConfiguration)}
            """,
        )
        writePlaceholderKotlin("src/commonMain/kotlin/Placeholder.kt")
    }

    /** Kept on one line: a multi-line insert would defeat the `trimIndent` of the template. */
    private fun skieBlock(configuration: String): String = if (skieVersion == null) "" else "skie { $configuration }"

    /**
     * Two modules: the Swift lives in a library the framework module merely depends on, so it can
     * only reach the framework by travelling through the library's klib.
     */
    fun withLibraryModule() {
        writeSettings("""include(":library")""")
        write(
            "build.gradle.kts",
            """
            plugins {
                $pluginsBlock
            }

            kotlin {
                $target {
                    binaries.framework {
                        baseName = "$FRAMEWORK_NAME"
                    }
                }
                sourceSets.commonMain.dependencies {
                    implementation(project(":library"))
                }
            }
            """,
        )
        writePlaceholderKotlin("src/commonMain/kotlin/Placeholder.kt")
        write(
            "library/build.gradle.kts",
            """
            plugins {
                $pluginsBlock
            }

            kotlin {
                $target()
            }
            """,
        )
    }

    /**
     * With no [kotlinVersion] the plugins come from the classpath TestKit injects, so they carry no
     * version; with one, both are resolved from repositories through their plugin markers, exactly
     * as a consumer resolves them. SKIE is always resolved from a repository.
     */
    private val pluginsBlock: String
        get() =
            listOfNotNull(
                pluginLine("org.jetbrains.kotlin.multiplatform", kotlinVersion),
                pluginLine(PLUGIN_ID, kotlinVersion?.let { PLUGIN_VERSION }),
                skieVersion?.let { pluginLine(SKIE_PLUGIN_ID, it) },
            ).joinToString("\n")

    private fun pluginLine(
        id: String,
        version: String?,
    ): String = if (version == null) "id(\"$id\")" else "id(\"$id\") version \"$version\""

    /**
     * A module with no Kotlin source at all produces no framework, so every generated project gets
     * one. Nothing under test depends on its content.
     */
    private fun writePlaceholderKotlin(path: String) {
        write(path, "class Placeholder")
    }

    private fun writeSettings(extra: String) {
        write(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    // The companion compiler plugin is resolved from here, exactly as a real
                    // consumer would resolve it.
                    mavenLocal()
                    mavenCentral()
                }
            }

            rootProject.name = "integration-test"
            $extra
            """,
        )
        write("gradle.properties", "org.gradle.jvmargs=-Xmx3g")
    }

    fun build(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(root)
            .withArguments(*arguments, "--stacktrace")
            .apply { if (kotlinVersion == null) withPluginClasspath() }
            .forwardOutput()
            .build()

    fun link(vararg extraArguments: String): BuildResult =
        build("linkDebugFramework${target.replaceFirstChar { it.uppercase() }}", *extraArguments)

    companion object {
        const val FRAMEWORK_NAME = "IntegrationKit"

        const val PLUGIN_ID = "io.github.frankois944.kmpSwiftCodeBundling"

        const val SKIE_PLUGIN_ID = "co.touchlab.skie"

        /** Version of SKIE the coexistence test applies, from the version catalog. */
        val SKIE_VERSION: String
            get() = System.getProperty("skieVersion") ?: error("skieVersion system property is not set")

        /** The Kotlin version this build uses, from the version catalog. */
        val KOTLIN_VERSION: String
            get() = System.getProperty("kotlinVersion") ?: error("kotlinVersion system property is not set")

        /** Version of the plugin under test, published to mavenLocal by the Gradle task. */
        val PLUGIN_VERSION: String
            get() = System.getProperty("pluginVersion") ?: error("pluginVersion system property is not set")

        /** Kotlin versions the plugin claims to support, from the version catalog. */
        val SUPPORTED_KOTLIN_VERSIONS: List<String>
            get() = System.getProperty("kotlinVersions")?.split(",").orEmpty()

        /** Skips the test when the host cannot link an Apple framework. */
        fun assumeApplePlatform() {
            assumeTrue("needs macOS", System.getProperty("os.name").startsWith("Mac"))
            assumeTrue("needs Xcode command line tools", runsSuccessfully("xcrun", "--find", "swiftc"))
        }

        /** Skips the test when the platform SDK is not installed in the current Xcode. */
        fun assumeSdkInstalled(sdk: String) {
            assumeTrue("needs the $sdk SDK", runsSuccessfully("xcrun", "--sdk", sdk, "--show-sdk-path"))
        }

        private fun runsSuccessfully(vararg command: String): Boolean =
            runCatching {
                ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor() == 0
            }.getOrDefault(false)
    }
}

/** Mach-O symbol names are plain ASCII in the binary, so no toolchain is needed to look for one. */
internal fun File.containsSymbol(name: String): Boolean = readBytes().toString(Charsets.ISO_8859_1).contains(name)
