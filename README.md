# KMP Swift Code Bundling 🐦

A Gradle plugin that lets a Kotlin Multiplatform module ship handwritten **Swift** code inside the
Kotlin/Native framework it produces — a standalone reimplementation of
[SKIE's Swift code bundling](https://skie.touchlab.co/features/swift-code-bundling).

```
plugins {
    kotlin("multiplatform")
    id("io.github.frankois944.kmpSwiftCodeBundling")
}
```

Swift files go next to the Kotlin ones:

```
src/commonMain/kotlin/…      src/commonMain/swift/SwiftGreeter.swift
src/iosMain/kotlin/…         src/iosMain/swift/IosOnlyGreeter.swift
```

They can use the Kotlin API of the framework directly — the bundled code is compiled as a Swift
overlay of the framework's Objective-C module, so no import is needed. Consumers get everything from
a single `import ExampleKit`.

> **Swift defaults to `internal`.** Unlike Kotlin, a declaration without a visibility modifier is not
> visible outside the framework. Mark anything you want to expose as `public`.

## Configuration

```
swiftCodeBundling {
    enabled.set(true)      // default
    swiftVersion.set("5")  // -swift-version

    freeSwiftCompilerArgs.set(listOf("-warnings-as-errors"))
}
```

### Distributing the framework

Mirroring [SKIE's Swift compiler options](https://skie.touchlab.co/configuration/swift-compiler),
three options matter only when the framework is compiled against on *another* machine — publishing
an XCFramework, say. They are off by default because each one costs build time or is only correct in
that scenario:

| Option | Effect |
| --- | --- |
| `enableSwiftLibraryEvolution` | Compiles with `-enable-library-evolution` and emits `.swiftinterface` files, giving the framework a stable ABI. Required for XCFrameworks; noticeably slower. |
| `noClangModuleBreadcrumbsInStaticFrameworks` | Passes `-no-clang-module-breadcrumbs` for static frameworks, so the binary does not carry DWARF references to a module cache that only exists on the build machine. |
| `enableRelativeSourcePathsInDebugSymbols` | Records source paths relative to the root project instead of absolute, so the Kotlin and Swift sources can be debugged from a different checkout. |

Turn all three on at once with:

```
swiftCodeBundling {
    produceDistributableFramework()
}
```

`enableRelativeSourcePathsInDebugSymbols` covers both languages: it adds `-file-compilation-dir .`
to the Swift compilation and `-Xdebug-prefix-map=<rootDir>=.` to the link task, and works around the
Kotlin/Native bug that would otherwise drop the links to the Kotlin sources — the same workaround
SKIE applies in its `CodegenPhaseInterceptor`.

## How it works

The feature is split between a Gradle plugin and a Kotlin/Native compiler plugin.

| Step | Where | What happens |
| --- | --- | --- |
| `processSwiftSources<Target>` | Gradle | Gathers `src/<sourceSet>/swift/**/*.swift` for a compilation and checks that no two files share a name (Swift requires unique file names within a module). |
| Compile task `doLast` | Gradle | Copies those sources into the compilation's klib, under `default/swift-code-bundling/swift`, so they travel with the published module. |
| `unpackSwiftSources<…>` | Gradle | Extracts the bundled Swift out of every klib the binary links against — the module's own klib and its dependencies — prefixing file names with their origin to keep them unique. |
| `LinkerPhase` interception | Compiler plugin | Runs `swiftc` against the framework's generated Objective-C headers, adds the resulting object file to the ones the native linker receives, then installs `<Framework>.swiftmodule`, `<Framework>-Swift.h` and the matching `module.modulemap` entry into the framework. |

The compiler plugin is what makes a single link possible: the Kotlin/Native `LinkerPhase` is a
singleton whose body lives in a private field, and the plugin swaps that body for a wrapper that
delegates to the original one with extra object files. This mirrors what SKIE does in
`LinkerPhaseInterceptor` / `LinkObjectFilesPhase`.

## Project layout

- [`plugin-build/plugin`](plugin-build/plugin) — the Gradle plugin.
- [`plugin-build/compiler-plugin`](plugin-build/compiler-plugin) — the Kotlin/Native compiler plugin,
  loaded inside the compiler and therefore free of any Gradle class.
- [`example`](example) — a KMP module with Swift sources in `commonMain` and `iosMain`.

Both are wired through a [composite build](https://docs.gradle.org/current/userguide/composite_builds.html),
so `./gradlew :example:linkDebugFrameworkIosSimulatorArm64` builds the plugin and uses it in one go.

## Status and limits

- Requires macOS and a Kotlin/Native Apple target.
- Framework binaries only; XCFrameworks and fat frameworks are not wired yet.
- The `LinkerPhase` interception relies on Kotlin/Native internals; it is pinned to the Kotlin
  version of `gradle/libs.versions.toml` and needs review on every Kotlin upgrade.
