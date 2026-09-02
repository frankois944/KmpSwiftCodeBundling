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
- `example` — KMP module producing the `ExampleKit` framework for `iosArm64`, `iosSimulatorArm64`
  and `iosX64`, assembled into an XCFramework, with Swift in `src/commonMain/swift` and
  `src/iosMain/swift`.

Tests come in three layers, all under `plugin-build/plugin`:

| Task | What it covers | Where it runs |
| --- | --- | --- |
| `test` | Unit tests and TestKit functional tests | any host |
| `integrationTest` | Links real Apple frameworks, four platforms, static, XCFramework, incremental | macOS with Xcode |
| `kotlinVersionTest` | Links with every supported Kotlin version | macOS, downloads one Kotlin/Native toolchain per version, CI runs it on `main` only |

`kotlinVersionTest` is the only one that resolves the plugins from a repository instead of the
classpath TestKit injects — the only way to vary the Kotlin version, and the same path a consumer
takes. It also exercises the *Gradle* plugin against each KGP, which is why the plugin compiles
against the oldest supported KGP (`kotlin-gradle-plugin-min` in the catalog) rather than the newest.

A skipped integration test is not a passing one — the platform tests skip themselves when their SDK
is missing, so check the counts in the report, not just the build result.

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

The compiler plugin uses Kotlin/Native internals, so a jar built against one Kotlin version will not
run inside another — source compatibility is not enough. The same sources are compiled once per
Kotlin version and published as separate artifacts:

- `plugin-build/compiler-plugin/src/common` — everything.
- `plugin-build/compiler-plugin/src/compat-2.2` and `src/compat-2.4` — only the names JetBrains
  renamed, as typealiases. 2.2 and 2.3 share the 2.2 compat file.
- `compiler-plugin/build.gradle.kts` loops over `compilerVariants`, creating one source set, one jar
  and one publication each (`compiler-plugin-kotlin-2.2`, `-2.3`, `-2.4`).
- `CompilerPluginArtifact` on the Gradle side maps `project.getKotlinPluginVersion()` to a variant;
  anything newer than the last known one falls back to it.

Adding a Kotlin version means: a catalog entry, a line in `compilerVariants`, a branch in
`CompilerPluginArtifact.variantFor`, and a new `src/compat-*` directory only if something was
renamed. What changed so far:

| API | Change |
| --- | --- |
| `SimpleNamedCompilerPhase` | became `NamedCompilerPhase` in 2.2 — *does not affect us*, the phase body is swapped reflectively |
| `KonanConfig` | became `NativeSecondStageCompilationConfig` in 2.4 |
| `KonanConfigKeys` | became `NativeConfigurationKeys` in 2.4, `OUTPUT` renamed `KONAN_OUTPUT_PATH` |
| `PhaseContext` | became `NativePhaseContext` in 2.4 |

`LINKER_ARGS`, `STATIC_FRAMEWORK` and `DEBUG_PREFIX_MAP` kept their names, so the typealias covers
them. `CompilerOutputKind` is deliberately not used: the framework is recognised from its output
path instead, which is one less internal API to track. SKIE keeps version-specific source sets under
`SKIE/kotlin-compiler/linker-plugin/src/` — consult them when bumping.

The compiler plugin needs `optIn` on
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

## Incremental Swift compilation

Debug builds pass `-incremental -enable-batch-mode` with an output file map giving every source its
own `.o`, `.d`, `.swiftdeps` and `~partial.swiftmodule`; release builds use whole-module
optimisation with a single object declared on the map's root entry. All the resulting object files
go to the linker, and `deleteStaleIntermediates` removes the outputs of sources that disappeared.

Three constraints, each found the hard way with `-driver-show-incremental` (add it to
`freeSwiftCompilerArgs` to make the driver explain its decisions in `work/logs/swiftc.log`):

- **The root entry of the output file map must declare `swift-dependencies`.** The driver uses it as
  its build record path; without it, `Disabling incremental build: no build record path`.
- **Per-file entries must declare only outputs the compiler actually writes** — `object` and
  `swift-dependencies`. A `.d` without `-emit-dependencies`, or the `~partial.swiftmodule` modern
  Swift no longer emits, gives `Missing an output` and recompiles everything.
- **`deleteStaleIntermediates` strips only the last extension.** Unpacked sources are named
  `bundled.<origin>.<Source>`, so cutting at the first dot matches nothing and wipes every object
  file each build — which shows up as `Missing an output` too, from a completely different cause.

Incrementality also depends on timestamps, so two more things must stay as they are:

- `UnpackSwiftSourcesTask` syncs with `syncDirectoryContentIfDifferent`, leaving unchanged sources
  untouched instead of rewriting them.
- `prepareFrameworkForSwiftCompilation` copies the Kotlin header, apinotes and module map into the
  work directory only when their content differs. An unconditional copy moves their timestamps and
  makes the Swift compiler rebuild everything, every time.

Sources and output-file-map keys are the same relative paths, resolved against the working directory
(the unpacked sources directory); the source list is passed as a `@response` file.
