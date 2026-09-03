package io.github.frankois944.kmpSwiftCodeBundling.internal

/**
 * The Swift target triple and Clang architecture macro of each Apple Kotlin/Native target.
 *
 * Needed when merging frameworks: the Swift module artifacts of a framework are named after its
 * target triple, and a header covering several architectures has to guard each variant.
 *
 * Keyed by `KonanTarget.name` rather than by the instance, because Gradle's configuration cache does
 * not guarantee the same instance across builds.
 */
internal data class AppleTarget(
    val targetTriple: String,
    val architectureClangMacro: String,
) {
    val isMacos: Boolean
        get() = targetTriple.contains("-macos")
}

private const val ARM64 = "__aarch64__"
private const val ARM32 = "__arm__"
private const val X64 = "__x86_64__"

internal val appleTargets: Map<String, AppleTarget> =
    mapOf(
        "ios_arm64" to AppleTarget("arm64-apple-ios", ARM64),
        "ios_x64" to AppleTarget("x86_64-apple-ios-simulator", X64),
        "ios_simulator_arm64" to AppleTarget("arm64-apple-ios-simulator", ARM64),
        "watchos_arm32" to AppleTarget("armv7k-apple-watchos", ARM32),
        "watchos_arm64" to AppleTarget("arm64_32-apple-watchos", ARM64),
        "watchos_device_arm64" to AppleTarget("arm64-apple-watchos", ARM64),
        "watchos_x64" to AppleTarget("x86_64-apple-watchos-simulator", X64),
        "watchos_simulator_arm64" to AppleTarget("arm64-apple-watchos-simulator", ARM64),
        "tvos_arm64" to AppleTarget("arm64-apple-tvos", ARM64),
        "tvos_x64" to AppleTarget("x86_64-apple-tvos-simulator", X64),
        "tvos_simulator_arm64" to AppleTarget("arm64-apple-tvos-simulator", ARM64),
        "macos_x64" to AppleTarget("x86_64-apple-macos", X64),
        "macos_arm64" to AppleTarget("arm64-apple-macos", ARM64),
    )
