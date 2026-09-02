import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.frankois944.kmpSwiftCodeBundling")
}

// Run with `-PexampleStaticFramework=true` to check the static framework path.
val useStaticFramework = providers.gradleProperty("exampleStaticFramework").map { it.toBoolean() }.getOrElse(false)

kotlin {
    val xcFramework = XCFramework("ExampleKit")

    listOf(
        iosArm64(),
        // Two simulator targets on purpose: the XCFramework merges them into a fat framework, which
        // is the case where the Swift modules of every architecture have to be merged by hand.
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ExampleKit"
            isStatic = useStaticFramework
            xcFramework.add(this)
        }
    }
}

swiftCodeBundling {
    swiftVersion.set("5")

    // Temporary: makes the Swift driver explain, in work/logs/swiftc.log, why it recompiled each
    // file. Remove once the incremental behaviour has been checked.
    freeSwiftCompilerArgs.set(listOf("-driver-show-incremental"))
}
