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
    // The `-swift-version` passed to swiftc. "5" unless the bundled Swift needs a newer language
    // mode; it has nothing to do with the Swift *compiler* version, which comes from Xcode.
    swiftVersion.set("5")

    // Everything below is off by default, and should stay off for a framework consumed locally.
    // It only earns its build time when the framework is compiled against on another machine - a
    // published XCFramework, or a binary someone else links.
    //
    // produceDistributableFramework()   // the three options below, in one call
    //
    // enableSwiftLibraryEvolution.set(true)                  // stable ABI, and `.swiftinterface`
    //                                                        // files instead of a compiler-locked
    //                                                        // `.swiftmodule`. Forced anyway on
    //                                                        // every slice of an XCFramework.
    // noClangModuleBreadcrumbsInStaticFrameworks.set(true)   // keeps this machine's module cache
    //                                                        // paths out of a static framework
    // enableRelativeSourcePathsInDebugSymbols.set(true)      // Kotlin and Swift stay debuggable
    //                                                        // from another checkout
    //
    // Extra swiftc arguments, appended last. `-driver-show-incremental` makes the Swift driver
    // explain what it decided to recompile and why, in
    // build/swift-code-bundling/binaries/<target>/<binary>/work/logs/swiftc.log.
    //
    // freeSwiftCompilerArgs.set(listOf("-driver-show-incremental"))
}
