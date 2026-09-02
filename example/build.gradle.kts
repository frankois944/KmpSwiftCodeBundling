plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.frankois944.kmpSwiftCodeBundling")
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ExampleKit"
            isStatic = false
        }
    }
}

swiftCodeBundling {
    swiftVersion.set("5")
    produceDistributableFramework()
}
