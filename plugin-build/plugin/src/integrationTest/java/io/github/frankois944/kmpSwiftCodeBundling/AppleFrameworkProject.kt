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
    ) {
        writeSettings("")
        write(
            "build.gradle.kts",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("io.github.frankois944.kmpSwiftCodeBundling")
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
            """,
        )
        writePlaceholderKotlin("src/commonMain/kotlin/Placeholder.kt")
    }

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
                id("org.jetbrains.kotlin.multiplatform")
                id("io.github.frankois944.kmpSwiftCodeBundling")
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
                id("org.jetbrains.kotlin.multiplatform")
                id("io.github.frankois944.kmpSwiftCodeBundling")
            }

            kotlin {
                $target()
            }
            """,
        )
    }

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
            .withPluginClasspath()
            .forwardOutput()
            .build()

    fun link(): BuildResult = build("linkDebugFramework${target.replaceFirstChar { it.uppercase() }}")

    companion object {
        const val FRAMEWORK_NAME = "IntegrationKit"

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
