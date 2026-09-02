# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this project is

`KmpSwiftCodeBundling` is a Gradle plugin that lets a Kotlin Multiplatform module ship handwritten
**Swift** code inside the Kotlin/Native framework it produces. It is a standalone reimplementation of
[SKIE's Swift code bundling](https://skie.touchlab.co/features/swift-code-bundling) and of its
[Swift compiler options](https://skie.touchlab.co/configuration/swift-compiler).

SKIE itself is the reference implementation and is usually cloned next to this repo (`../SKIE`).
It is Apache 2.0, Copyright 2023 Touchlab — **any file that derives code or documentation from it
must carry the attribution header** (see an existing one for the shape) and be listed in `NOTICE`.
When a behaviour is unclear, read SKIE's source rather than guessing — most of the design decisions
here were taken by reading `SKIE/skie-gradle/plugin-impl/.../switflink/` and
`SKIE/kotlin-compiler/linker-plugin/.../entrypoint/`.

Plugin coordinates: `io.github.frankois944.kmpSwiftCodeBundling` (this string is the plugin id, the
Maven group, the compiler plugin id and the Kotlin package — they must stay identical, see
`plugin-build/gradle.properties`).

## Layout

Composite build: the plugin lives in the included build `plugin-build/`, the consumer that exercises
it is `example/`.

- `plugin-build/plugin` — the Gradle plugin. Sources are under `src/main/java/` (template quirk,
  not Java). Depends on the Kotlin Gradle Plugin as `compileOnly`.
- `plugin-build/compiler-plugin` — a Kotlin/Native **compiler** plugin, loaded inside the compiler.
  It must never see a Gradle class; `kotlin-native-compiler-embeddable` and the stdlib are
  `compileOnly` so nothing is bundled.
- `example` — KMP module producing the `ExampleKit` framework for `iosArm64` / `iosSimulatorArm64`,
  with Swift in `src/commonMain/swift` and `src/iosMain/swift`.

The two modules share constants by **duplication**, not by a common module: `SwiftBundling` (Gradle
side) and `SwiftBundlingPluginIds` (compiler side). Change one, change the other.

## Commands

```bash
# build the plugin and run it end to end (macOS + Xcode required)
./gradlew :example:linkDebugFrameworkIosSimulatorArm64

# plugin checks only — works on Linux/CI
./gradlew -p plugin-build check validatePlugins

./gradlew reformatAll   # ktlint across both builds
./gradlew preMerge      # everything
```

Verifying that bundling actually worked (a green build proves nothing — the pipeline silently does
nothing when it finds no Swift):

```bash
F=example/build/bin/iosSimulatorArm64/debugFramework/ExampleKit.framework
nm -gU "$F/ExampleKit" | grep SwiftGreeter
ls "$F/Modules/ExampleKit.swiftmodule"
grep "module ExampleKit.Swift" "$F/Modules/module.modulemap"
cat example/build/swift-code-bundling/binaries/*/*/work/logs/swiftc.log   # the swiftc command line
```

## How the feature works

Four steps, split between the two modules:

1. `processSwiftSources<Target>` (Gradle) — collects `src/<sourceSet>/swift/**/*.swift` for a
   compilation and rejects duplicate file names (Swift requires unique file names within a module,
   whatever the path).
2. The Kotlin compile task's `doLast` (Gradle) — writes those sources into the klib under
   `default/swift-code-bundling/swift`, so they travel with the published module. It is a `doLast`
   and not a task of its own because the klib *is* the compile task's output.
3. `unpackSwiftSources<…>` (Gradle) — extracts the bundled Swift back out of every klib the binary
   links against, prefixing file names with their origin to keep them unique.
4. `LinkerPhase` interception (compiler plugin) — runs `swiftc`, adds the resulting object file to
   the ones the native linker receives, then installs `.swiftmodule`, `-Swift.h` and the
   `module <Name>.Swift` entry into the framework.

### Phase interception

`PhaseBodyInterception` replaces the private `$op` field of a Kotlin/Native phase singleton with a
wrapper delegating to the original body. This is what makes a **single** link possible; without it
you would have to link twice (once to get the Objective-C header, once to link the Swift).

Two phases are wrapped:

- `LinkerPhase` — always, to add the Swift object file.
- `CodegenPhase` — only when `enableRelativeSourcePathsInDebugSymbols` is on.

The phase is global and shared by every compilation in the same daemon, so the wrapper captures
nothing compilation-specific: it reads what to do from the `CompilerConfiguration` of the running
compilation, under a key stored *inside the installed wrapper* so that two copies of the plugin
loaded by different class loaders agree on where to look. Everything is erased to `Any` because the
two phases have unrelated context and input types.

## Traps — do not reintroduce these

- **`compilation.allKotlinSourceSets` fills in progressively.** Reading it eagerly returns an empty
  set and `processSwiftSources` silently becomes NO-SOURCE. It is read through a `project.provider {}`
  to defer resolution. SKIE uses its `forAll` callback for the same reason.
- **`KotlinNativeCompile.outputFile` is a `Provider<File>`**, not a `Provider<FileSystemLocation>`,
  so Gradle cannot infer the producing task from it — `dependsOn(compileTaskProvider)` is explicit.
- **Swift runtime linker arguments are required**, otherwise a dynamic framework cannot find
  libswift: `-L <toolchain>/lib/swift/<platformName>`, `-L <sysroot>/usr/lib/swift`,
  `-rpath /usr/lib/swift` (SKIE's `ConfigureSwiftSpecificLinkerArgsPhase`).
- **swiftc reads the framework while it is being built.** It compiles the bundled code as an overlay
  of the framework's Clang module (`-import-underlying-module`), so it gets a *flat copy* holding
  only the headers, the module map and the apinotes, never the real framework — otherwise the
  `module <Name>.Swift` entry we append would be read back as an input.
- **The `.apinotes` must be in that copy**, or Swift sees the prefixed Objective-C names
  (`ExampleKitGreeting`) instead of the Kotlin ones (`Greeting`).
- **macOS frameworks are versioned bundles** (`Versions/A`); every other Apple platform is flat.
- **`-Xdebug-prefix-map` needs the `CodegenPhase` workaround.** A non-empty prefix map when
  `NativeGenerationState.debugInfo` is first created makes the binary lose its links to the Kotlin
  sources. `RelativeSourcePathsWorkaround` empties the map, touches `debugInfo`, restores it. Never
  ship the option without it.
- **An empty `ExampleKit-Swift.h` is not a bug.** A Swift `enum` without `@objc` is not exported to
  Objective-C. Consumers `import ExampleKit` from Swift and get the overlay module.

## Kotlin version coupling

The compiler plugin uses Kotlin/Native internals and is pinned to the `kotlin` version of
`gradle/libs.versions.toml` (currently 2.1.21). Re-check on every Kotlin upgrade:

| API | Change |
| --- | --- |
| `SimpleNamedCompilerPhase` | becomes `NamedCompilerPhase` in 2.2+ |
| `KonanConfigKeys.OUTPUT` | becomes `NativeConfigurationKeys.KONAN_OUTPUT_PATH` in 2.4 |
| `KonanConfig` | becomes `NativeSecondStageCompilationConfig` in 2.4 |
| `PhaseContext` | becomes `NativePhaseContext` in 2.4 |

SKIE keeps version-specific source sets (`src/2.2.0..`, `src/2.4.0..`) for exactly these; consult
them when bumping. The compiler plugin needs `optIn` on
`org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi`, and its files carry
`@file:Suppress("invisible_reference", "invisible_member")` to reach the internal APIs.

## Conventions

- ktlint runs with `ktlint_official` style and experimental rules. Note the root ktlint filter is
  `include("**/kotlin/**")`, so `plugin-build/plugin` (under `src/main/java/`) is *not* checked while
  `plugin-build/compiler-plugin` is. Run `./gradlew reformatAll` before committing.
- Gradle task classes must stay `abstract` (Gradle instantiates them) — detekt 2.0 renamed the rule
  to `AbstractClassCanBeConcreteClass`, suppress it rather than refactoring.
- `kotlin.compiler.execution.strategy=in-process` is a deliberate workaround in both
  `gradle.properties` files for a `kotlin-compiler-embeddable` version clash on the buildscript
  classpath. Remove only once the real cause is found.

## Frameworks, static frameworks, XCFrameworks

Dynamic and static frameworks both go through the same path: the Swift object file is added to
`LinkerPhaseInput.objectFiles`, which Kotlin/Native passes to the linker or the archiver.

XCFrameworks need two extra things, both in `SwiftCodeBundlingConfigurator`:

- **Library evolution is forced** on any framework an XCFramework is assembled from. Detection
  matches `Framework.outputFile` against the input files of every `XCFrameworkTask` — over all of
  them, not only those in the task graph, so a framework is built identically whether it is linked
  on its own or through the XCFramework.
- **Fat frameworks are patched** by `MergeBundledSwiftIntoFatFramework`. `lipo` merges binaries
  only and the bundle structure comes from one input framework, so the `<triple>.swiftmodule` of the
  other architectures would be missing and Swift would refuse the import. Wired from
  `gradle.taskGraph.whenReady`, because a `FatFrameworkTask`'s frameworks are only known once KGP
  has finished configuring the XCFramework.

When checking an XCFramework, assert on `.swiftinterface` and not `.swiftmodule`: `xcodebuild
-create-xcframework` strips the binary Swift modules, which are tied to an exact compiler version,
and keeps the textual interfaces. Verify the merge itself on the fat framework under
`build/<name>XCFrameworkTemp/fatframework/`, before assembly.

## Current limits

Swift is compiled whole-module on every build; SKIE does incremental debug builds with
`-enable-batch-mode`, an output-file-map and one object file per source.
