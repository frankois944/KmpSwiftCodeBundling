# KMP Swift Code Bundling

Write Swift next to your Kotlin, get **one framework** with both inside.

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.frankois944.kmpSwiftCodeBundling?label=Gradle%20Plugin%20Portal&color=02303A)](https://plugins.gradle.org/plugin/io.github.frankois944.kmpSwiftCodeBundling)
[![Build](https://github.com/frankois944/KmpSwiftCodeBundling/actions/workflows/pre-merge.yaml/badge.svg)](https://github.com/frankois944/KmpSwiftCodeBundling/actions/workflows/pre-merge.yaml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2%2B-7F52FF?logo=kotlin&logoColor=white)
![Platforms](https://img.shields.io/badge/platforms-iOS%20%7C%20macOS%20%7C%20watchOS%20%7C%20tvOS-lightgrey)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

Kotlin Multiplatform gives your Apple targets a framework full of Kotlin, and the moment you need
real Swift in it you normally end up shipping a second module, with its own build, its own
versioning and a bridge in between. This plugin — a standalone reimplementation of
[SKIE's Swift code bundling](https://skie.touchlab.co/features/swift-code-bundling) — compiles your
Swift into the *same* framework the Kotlin compiler produces, as a Swift overlay of its module: one
binary, one `import`, one version.

```swift
import ExampleKit

Greeting().greet(name: "François")   // Kotlin
Greeter.greet("François")            // your Swift
```

## Quick start

**1.** Apply the plugin next to `kotlin("multiplatform")`:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.frankois944.kmpSwiftCodeBundling") version "0.1.0"
}
```

**2.** Put Swift files in a `swift` directory beside the `kotlin` one, in the same source set:

```
src/
  commonMain/
    kotlin/Greeting.kt
    swift/Greeter.swift        ← every Apple target
  iosMain/
    kotlin/IosThing.kt
    swift/DeviceInfo.swift     ← iOS targets only
```

**3.** Build the way you already do — `linkDebugFrameworkIosArm64`,
`embedAndSignAppleFrameworkForXcode`, `assembleXCFramework`. The Swift comes along.

## Writing the Swift

```swift
public enum Greeter {
    public static func greet(_ name: String) -> String {
        Greeting().greet(name: name)   // a Kotlin class from this module
    }
}
```

Three rules, and the build tells you when you break the third:

- **Mark everything you expose as `public`.** Swift defaults to `internal`, unlike Kotlin — anything
  without a modifier is invisible outside the framework. This is the one that bites first.
- **Add `@objc`** to whatever Objective-C consumers need to see. Swift-only consumers do not need it.
- **File names must be unique** across all your source sets, whatever the directory — Swift requires
  it of every file in a module.

## What it handles

| | |
| --- | --- |
| **Platforms** | iOS, macOS, watchOS, tvOS |
| **Binaries** | Dynamic and static frameworks |
| **XCFrameworks** | Yes, multi-architecture slices included |
| **Library modules** | Swift in a library travels to whichever app framework depends on it |
| **Build times** | Debug builds recompile only the Swift files that changed |

## Configuration

All optional; the defaults suit local development.

```kotlin
swiftCodeBundling {
    enabled.set(true)                                       // false turns bundling off entirely
    swiftVersion.set("5")                                   // -swift-version
    freeSwiftCompilerArgs.set(listOf("-warnings-as-errors"))
}
```

### Shipping a framework someone else compiles against

Publishing an XCFramework, or a framework another team builds against? Turn on the three options
that make it portable:

```kotlin
swiftCodeBundling {
    produceDistributableFramework()
}
```

| Option | What it does |
| --- | --- |
| `enableSwiftLibraryEvolution` | Stable ABI and `.swiftinterface` files. Slower to build; enabled automatically inside an XCFramework. |
| `noClangModuleBreadcrumbsInStaticFrameworks` | Keeps build-machine module cache paths out of static framework binaries. |
| `enableRelativeSourcePathsInDebugSymbols` | Relative source paths, so Kotlin *and* Swift stay debuggable from another checkout. |

Each can also be set on its own. They mirror
[SKIE's Swift compiler options](https://skie.touchlab.co/configuration/swift-compiler).

## Kotlin versions

The plugin runs inside the Kotlin/Native compiler, so a build of it is tied to a Kotlin version. It
ships one compiler artifact per version and picks the right one from your build — nothing to
configure. Kotlin releases newer than those listed fall back to the most recent variant.

| Kotlin | Built against |
| --- | --- |
| 2.2.x | 2.2.21 |
| 2.3.x | 2.3.21 |
| 2.4.x and newer | 2.4.10 |

## Known limitations

- **Gradle's configuration cache is not supported yet.** Builds using `--configuration-cache` will
  report the plugin as incompatible.

## Contributing

See [CLAUDE.md](CLAUDE.md) for the architecture, the build commands and the pitfalls.

## License

[MIT](LICENSE). Reimplements a feature of [SKIE](https://github.com/touchlab/SKIE), Copyright 2023
Touchlab, Inc., Apache License 2.0 — see [NOTICE](NOTICE) and
[licenses/](licenses/LICENSE-Apache-2.0.txt). Not affiliated with or endorsed by Touchlab.
