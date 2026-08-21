# Dependency version decisions (Phase 0)

Recorded so later phases don't need to re-fetch Maven metadata to answer "what version are
we on and why", and don't re-hit the same toolchain incompatibilities. Pinned in
`gradle/libs.versions.toml`. Every version below was **verified by actually building** - not
just checked against metadata - via `./gradlew detekt ktlintCheck test assembleDebug
assembleRelease`. Checked/built on 2026-08-18/19; re-verify before bumping a major version,
since compatibility windows shift.

## Build toolchain

| Component | Version | Why this one, not newer |
|---|---|---|
| JDK | 17 (`jdk17-openjdk`) | AGP 8.13 minimum; installed via pacman |
| Gradle | 8.14.5 | Latest patch on the 8.x line. AGP 8.13.2 requires Gradle ≥ 8.13; the 9.x line pairs with AGP 9 (see below) |
| AGP | 8.13.2 | Latest 8.x stable. **Deliberately not AGP 9.x** (latest stable 9.3.1) - AGP 9 removes `CommonExtension` type parameterization (breaks the shared convention-plugin function pattern this project uses), enables "built-in Kotlin" by default, requires build-tools 36 + Gradle 9.1+, **and Hilt only added AGP 9 support in 2.59 - 2.58 is the last Hilt release that works with AGP 8** (confirmed by actually hitting `AGP 9.0.0 or higher (found ... 8.13.2)` from the Hilt plugin). None of this is needed to hit `compileSdk 35`. Revisit at the start of a phase that needs API 36, not mid-build. |
| Kotlin (KGP) | 2.1.21 | **Not 2.3.20 or 2.4.x.** Gradle 8.14.5's embedded `kotlin-dsl` compiler (used to compile `build-logic/convention`) reads Kotlin module metadata up to format version `2.0.0`/`2.1.0`; jars built with Kotlin ≥2.2 use a newer metadata format and fail with `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.3.0, expected version is 2.1.0` - this hits *any* dependency on `build-logic`'s classpath (AGP, KGP, KSP, Hilt/Room plugins) compiled with a too-new Kotlin, not just KGP itself. 2.1.21 is the newest release that stays under this ceiling. |
| KSP | 2.1.21-2.0.2 | Matches KGP 2.1.21 exactly (KSP's own version now has an independent number, but its POM pins a specific `kotlin-compiler-embeddable` - check that pin, not the KSP version number, when bumping). |

**The metadata-version ceiling isn't just a build-logic problem** - it also breaks the app's
own `compileDebugKotlin` if any *runtime* dependency's stdlib requirement is compiled with a
newer Kotlin than the app's own KGP: `kotlinx-serialization-core` 1.11.0 and
`kotlinx-coroutines-core` 1.11.0 both pull `kotlin-stdlib` 2.2.x/2.3.x transitively, which
then fails to compile against KGP 2.1.21. Fix was to pin those two libraries down to releases
whose POM declares a `kotlin-stdlib` dependency ≤2.1.21 (checked directly:
`curl .../kotlinx-serialization-core-jvm/<version>/....pom | grep -A2 kotlin-stdlib`).

## Application dependencies

Every AndroidX library below is pinned to **the newest release that still declares
`minCompileSdk<=35` and `minAndroidGradlePluginVersion<=8.x`** in its AAR's
`META-INF/com/android/build/gradle/aar-metadata.properties` - not just the newest release.
The androidx train has been shipping releases that require compileSdk 36/37 and AGP 9.1+
since ~Jan 2026; using the latest patch of `compose-bom`, `core-ktx`, `activity-compose`,
etc. blind fails `:app:checkDebugAarMetadata`/`processDebugResources` with "requires Android
Gradle plugin 9.1.0 or higher" even though nothing about the design doc's `compileSdk 35`
decision has changed. Verify with (for example):
```
curl -fsSL -o x.aar <maven-url>/library-1.2.3.aar
unzip -p x.aar META-INF/com/android/build/gradle/aar-metadata.properties
```

| Library | Version | Note |
|---|---|---|
| Hilt (`hilt-android`, `hilt-android-gradle-plugin`, `hilt-compiler`) | 2.58 | Last release compatible with AGP 8 (see toolchain table) |
| `androidx.hilt:hilt-navigation-compose` | 1.2.0 | 1.4.0 requires compileSdk 34+/newer AGP baseline than needed; 1.2.0 confirmed minCompileSdk 34 |
| Room (`room-runtime`, `room-compiler`, `room-ktx`, `androidx.room` Gradle plugin) | 2.8.4 | No compileSdk/AGP conflict found at this version |
| Compose BOM | 2025.09.00 | Pins `compose-ui` 1.9.1 - confirmed `minCompileSdk=35`, `minAndroidGradlePluginVersion=8.6.0` |
| `androidx.compose.material3.adaptive:adaptive` (+ `-layout`, `-navigation`) | 1.2.0 | Not covered by the Compose BOM - versioned independently. 1.3.0 requires newer compileSdk |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | 1.3.2 | Not covered by the Compose BOM. Its `navigationSuiteItems` lambda is **not** `@Composable` at this version - composable calls inside it (e.g. `currentBackStackEntryAsState()`) must happen in the enclosing composable and be passed in as already-computed values |
| `androidx.navigation:navigation-compose` | 2.8.9 | Not covered by the Compose BOM |
| `androidx.window:window` | 1.4.0 | fold posture (`WindowInfoTracker`) |
| `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | 2.9.2 | |
| `androidx.activity:activity-compose` | 1.10.1 | 1.11.0 requires compileSdk 36 |
| `androidx.core:core-ktx` | 1.15.0 | 1.19.0 requires compileSdk 37 + AGP 9.1 |
| `androidx.datastore:datastore-core` (+ `com.google.protobuf` toolchain) | 1.1.7 | Not yet wired into any module - DataStore preferences are a Phase 5/8 concern per the plan, not Phase 0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-android` | 1.10.2 | See metadata-version note above |
| `com.jakewharton.timber:timber` | 5.0.1 | |
| `com.google.protobuf:protobuf-gradle-plugin` | 0.9.6 | Gradle Plugin Portal. Not yet applied anywhere (see datastore-core note) |
| `com.google.protobuf:protoc` / `protobuf-kotlin-lite` | 4.35.1 | Not yet used |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.8.1 | For Nav Compose type-safe routes. See metadata-version note above |

### `NavDestination.hasRoute` - how to actually call it (navigation-compose 2.8.9)

`NavDestination` has a member `hasRoute(route: String, arguments: Bundle?)` **and**, in its
companion object, an extension `fun <T : Any> NavDestination.hasRoute(route: KClass<T>)` plus
a reified `inline fun <reified T : Any> NavDestination.hasRoute()`. Calling `destination
.hasRoute(someKClassValue)` from application code resolves to the *member* overload and fails
to compile with a confusing "String expected" error, because Kotlin's member-vs-extension
shadowing picks the member by name regardless of arity. **Only the reified zero-arg form
works from outside the library**: `destination.hasRoute<SomeRoute>()`, which requires the
route type to be statically known at the call site - you cannot check membership against a
runtime `KClass` value in a generic loop. `ui/navigation/Destinations.kt` works around this
by giving each `TopLevelDestination` its own `isSelected: (NavDestination?) -> Boolean`
lambda, built with the reified call where the concrete route type is still known.

## Test dependencies

| Library | Version |
|---|---|
| JUnit4 | 4.13.2 |
| `app.cash.turbine:turbine` | 1.2.1 |
| `com.google.truth:truth` | 1.4.5 |
| `io.mockk:mockk` | 1.14.11 |
| `androidx.test.ext:junit` | 1.3.0 |
| `androidx.test.espresso:espresso-core` | 3.7.0 |

## Static analysis

| Tool | Version |
|---|---|
| detekt (`io.gitlab.arturbosch.detekt`) | 1.23.8 - config at `config/detekt/detekt.yml`: `FunctionNaming` ignores `@Composable` (PascalCase is the Compose convention), `MagicNumber` off (RF/network code is constants-heavy), `ReturnCount` raised to 4 |
| ktlint Gradle plugin (`org.jlleitschuh.gradle.ktlint`) | 14.2.0 - only on the Gradle Plugin Portal, not Maven Central. Compose-function naming exemption lives in the root `.editorconfig` (`ktlint_function_naming_ignore_when_annotated_with = Composable`), not the detekt config |

## Known benign warning

`./gradlew` prints "The Kotlin Gradle plugin was loaded multiple times in different
subprojects... The Kotlin plugin was loaded in the following projects: ':app',
':core:common'" on every run. This is a byproduct of applying `org.jetbrains.kotlin.android`
and `org.jetbrains.kotlin.jvm` from two different convention-plugin classes
(`AndroidLibraryConventionPlugin`/`AndroidApplicationConventionPlugin` vs
`JvmLibraryConventionPlugin`) rather than a single shared entry point. It has not caused an
actual build failure across `assembleDebug`, `assembleRelease`, `test`, `detekt`, or
`ktlintCheck`. Worth revisiting if a future Gradle/AGP upgrade turns it into a real error, but
not worth restructuring the convention-plugin split to silence pre-emptively - the JVM/Android
module split is a deliberate design decision (§2.1), not an accident of the convention-plugin
wiring.

## A build-logic-specific gotcha: don't name a helper `libs`

`build-logic/convention`'s Kotlin sources are compiled with **no package declaration**
(default package) alongside `.gradle.kts` scripts, which Gradle also treats as unnamed-package
Kotlin files. A top-level `val Project.libs: VersionCatalog` extension defined in build-logic
shadowed Gradle's own generated `libs` version-catalog accessor (`LibrariesForLibs`) in
*every* module's `build.gradle.kts` across the whole build - same-name, same (absent) package,
so no import was needed for it to win, and it silently replaced the richly-typed accessor with
one that only exposes `.findLibrary("name")`. Symptom: `Unresolved reference: androidx` /
`kotlinx` / `versions` on every dotted catalog access, even though `libs.plugins.x` worked
fine (plugin-alias resolution in the `plugins {}` block doesn't go through this accessor).
Fixed by moving all of `build-logic/convention`'s sources into a real package
(`netinspector.buildlogic`). If a future refactor reintroduces a top-level `build-logic`
helper, keep it out of the default package.

## Notes for future phases

- **Re-check AGP 9 / Hilt 2.59+ migration** once a phase actually needs API 36 - by then both
  should be better documented and battle-tested. Re-verify the whole androidx version matrix
  at that point too, since the compileSdk-35 ceiling documented above goes away with it.
- **KSP's version number no longer tracks Kotlin's.** When bumping Kotlin, check
  `com/google/devtools/ksp/symbol-processing-api/maven-metadata.xml`'s latest POM for its
  pinned `kotlin-compiler-embeddable` version, and separately verify build-logic still
  compiles (`./gradlew :build-logic:convention:compileKotlin`) before touching anything else.
- Wireshark's `manuf` OUI dataset location (needed for Phase 3, design §10) was **not**
  looked up during Phase 0 - the design doc flags that Wireshark restructured this during
  the 4.x series, so confirm the current file location when Phase 3 starts, not now.
