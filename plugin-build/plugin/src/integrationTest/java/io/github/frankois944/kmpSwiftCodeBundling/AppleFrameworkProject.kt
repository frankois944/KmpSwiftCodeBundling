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
) {
    val frameworkDirectory: File
        get() = root.resolve("build/bin/$TARGET/debugFramework/$FRAMEWORK_NAME.framework")

    val frameworkBinary: File
        get() = frameworkDirectory.resolve(FRAMEWORK_NAME)

    val swiftModuleDirectory: File
        get() = frameworkDirectory.resolve("Modules/$FRAMEWORK_NAME.swiftmodule")

    val swiftObjectFiles: File
        get() = root.resolve("build/swift-code-bundling/binaries/$TARGET/debugFramework/work/objects")

    /** Holds the swiftc command line and its output, including `-driver-show-incremental` remarks. */
    val swiftcLog: File
        get() = root.resolve("build/swift-code-bundling/binaries/$TARGET/debugFramework/work/logs/swiftc.log")

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
                $TARGET {
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
                $TARGET {
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
                $TARGET()
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

    fun link(): BuildResult = build("linkDebugFramework${TARGET.replaceFirstChar { it.uppercase() }}")

    companion object {
        const val TARGET = "iosSimulatorArm64"
        const val FRAMEWORK_NAME = "IntegrationKit"

        /** Skips the test when the host cannot link an Apple framework. */
        fun assumeApplePlatform() {
            assumeTrue("needs macOS", System.getProperty("os.name").startsWith("Mac"))
            assumeTrue(
                "needs Xcode command line tools",
                runCatching { ProcessBuilder("xcrun", "--find", "swiftc").start().waitFor() == 0 }.getOrDefault(false),
            )
        }
    }
}

/** Mach-O symbol names are plain ASCII in the binary, so no toolchain is needed to look for one. */
internal fun File.containsSymbol(name: String): Boolean = readBytes().toString(Charsets.ISO_8859_1).contains(name)
