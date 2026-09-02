# KMP Swift Code Bundling 🐦

Ship handwritten **Swift** code inside the framework your Kotlin Multiplatform module produces, so
your iOS app gets one framework and one `import` — a standalone reimplementation of
[SKIE's Swift code bundling](https://skie.touchlab.co/features/swift-code-bundling).

## Setup

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.frankois944.kmpSwiftCodeBundling") version "1.0.0"
}
```

Put Swift files next to your Kotlin ones, using the same source set names:

```
src/commonMain/kotlin/…          src/commonMain/swift/Greeter.swift
src/iosMain/kotlin/…             src/iosMain/swift/DeviceInfo.swift
```

That's all. Build your framework as usual (`linkDebugFrameworkIosSimulatorArm64`,
`embedAndSignAppleFrameworkForXcode`, …) and the Swift comes along.

## Writing the Swift

Your Swift code can use the module's Kotlin API directly, with no import:

```swift
public enum Greeter {
    public static func greet(_ name: String) -> String {
        Greeting().greet(name: name)   // a Kotlin class from this module
    }
}
```

> [!IMPORTANT]
> **Mark everything you want to expose as `public`.** Swift declarations default to `internal`,
> unlike Kotlin — anything without a modifier will be invisible outside the framework.

Two more rules worth knowing:

- **File names must be unique** across all your Swift source sets, whatever the folder. Swift
  requires it, and the build fails with a clear message if two collide.
- **`@objc` is needed for Objective-C consumers.** A plain Swift `enum` or `struct` reaches Swift
  callers only; that is usually what you want.

On the app side, everything arrives through the single framework import:

```swift
import ExampleKit

Greeter.greet("François")   // Swift
Greeting().greet(name: "François")  // Kotlin
```

## Configuration

Every option is optional; the defaults suit local development.

```kotlin
swiftCodeBundling {
    enabled.set(true)      // set to false to turn bundling off entirely
    swiftVersion.set("5")  // -swift-version

    freeSwiftCompilerArgs.set(listOf("-warnings-as-errors"))
}
```

### Distributing the framework

Three extra options matter only when the framework is compiled against on **another machine** —
publishing an XCFramework, for instance. They are off by default because each costs build time or is
only correct in that case.

```kotlin
swiftCodeBundling {
    produceDistributableFramework()   // enables all three at once
}
```

| Option | What it does |
| --- | --- |
| `enableSwiftLibraryEvolution` | Gives the framework a stable ABI and emits `.swiftinterface` files. Noticeably slower to build. Turned on automatically for frameworks assembled into an XCFramework, whatever you set here. |
| `noClangModuleBreadcrumbsInStaticFrameworks` | For static frameworks, keeps references to a build-machine module cache out of the binary. Avoids `…/xyz.pcm: No such file or directory` warnings when debugging. |
| `enableRelativeSourcePathsInDebugSymbols` | Records source paths relative to the root project, so the Kotlin *and* Swift sources can be debugged from a different checkout. |

These mirror [SKIE's Swift compiler options](https://skie.touchlab.co/configuration/swift-compiler)
and can also be set individually.

## Frameworks, static frameworks and XCFrameworks

All three work, and nothing extra needs configuring:

- **Dynamic or static** — `isStatic = true` is handled; the bundled Swift ends up in the static
  archive like any other object file.
- **XCFramework** — declare it as usual with `XCFramework()`. Each framework carries its own Swift
  before assembly, and library evolution is enabled for you.
- **Fat frameworks** — when an XCFramework slice covers several architectures (an iOS simulator
  slice built for `iosSimulatorArm64` and `iosX64`, say), `lipo` merges only the binaries, so the
  plugin merges the Swift module of every architecture into the fat framework itself.

## Requirements and limits

- macOS with Xcode, and a Kotlin/Native Apple target.
- Kotlin 2.1.21 — the plugin uses Kotlin/Native internals and is pinned to that version.

## License and attribution

This project is released under the license in [LICENSE](LICENSE).

It reimplements a feature of [SKIE](https://github.com/touchlab/SKIE) and derives code from it.
SKIE is Copyright 2023 Touchlab, Inc. and licensed under the Apache License, Version 2.0, a copy of
which is included at [licenses/LICENSE-Apache-2.0.txt](licenses/LICENSE-Apache-2.0.txt). Every file
containing derived code carries a header naming the SKIE sources it is based on and what was
changed; [NOTICE](NOTICE) lists them. This project is not affiliated with or endorsed by Touchlab.
