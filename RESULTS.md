# :shared — iOS verification

Run [36200696197](https://github.com/officialaksalman/prime-notes-ios-phase0/actions/runs/36200696197) on `macos-15`.

Sandbox commit: `d10fd5274ead39351423efd95e163e21248c9407`

## shared-ios-logs
```
sha=d10fd5274ead39351423efd95e163e21248c9407
compileKotlinIosSimulatorArm64=success
linkDebugFrameworkIosSimulatorArm64=success
iosSimulatorArm64Test=success
```
### test results
```
<testsuite name="iosSimulatorArm64Test.com.primenotes.bd.domain.account.PasswordPolicyTest" tests="10" skipped="0" failures="0" errors="0" timestamp="2026-09-25T23:27:40.774Z" hostname="sjc20-cw711-da44f87f-036f-4122-893c-ed291f977bf4-3AF6782363CF.local" time="0.015">
<testsuite name="iosSimulatorArm64Test.com.primenotes.bd.domain.backup.ImportPlanTest" tests="12" skipped="0" failures="0" errors="0" timestamp="2026-09-25T23:27:40.789Z" hostname="sjc20-cw711-da44f87f-036f-4122-893c-ed291f977bf4-3AF6782363CF.local" time="0.006">
<testsuite name="iosSimulatorArm64Test.com.primenotes.bd.domain.backup.NoteArchiveTest" tests="11" skipped="0" failures="0" errors="0" timestamp="2026-09-25T23:27:40.795Z" hostname="sjc20-cw711-da44f87f-036f-4122-893c-ed291f977bf4-3AF6782363CF.local" time="0.007">
```
### 00-toolchain.txt
```
### runner
ProductName:		macOS
ProductVersion:		15.7.9
BuildVersion:		24G830
### xcode
Xcode 26.3
Build version 17C529
### java
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
### gradle

------------------------------------------------------------
Gradle 9.7.1
------------------------------------------------------------

Build time:    2026-08-19 14:16:09 UTC
Revision:      92f0512e7f06d84621afba191f75e265363890cf

Kotlin:        2.4.0
Groovy:        4.0.32
Ant:           Apache Ant(TM) version 1.10.17 compiled on April 6 2026
Launcher JVM:  25.0.4.1 (Eclipse Adoptium 25.0.4.1+1-LTS)
Daemon JVM:    /Users/runner/hostedtoolcache/Java_Temurin-Hotspot_jdk/25.0.4-101.0/arm64/Contents/Home (no Daemon JVM specified, using current Java home)
OS:            Mac OS X 15.7.9 aarch64

### source files
      62
### copied from
# `shared-ios/` — a copy of the `:shared` module

This is a **copy** of the `shared` module from the private `Prime-Notes` repository, committed
here so the iOS verification workflow can build it from a public repository. macOS runner minutes
are free on a public repository and billable on the private one, and that is the only reason this
directory exists.

Nothing here is edited in place. A fix belongs upstream in `Prime-Notes`; this is a copy, and the
copy is what drifts if that is forgotten.

## Where this copy came from

- Prime-Notes commit **`d9c18ef`** — *"feat: Introduce TimeSource interface and refactor
  DevicePresence to use it"*
- **plus the uncommitted Phase 2b working tree on top of that commit**, which is in no commit
  yet. The difference is the whole point of the run:
  - `domain/account/PasswordPolicy.kt`, `SecureRandom.kt`, `SecureRandom.android.kt`,
    `SecureRandom.ios.kt` and `PasswordPolicyTest.kt`
  - `domain/backup/NoteArchive.kt`, `ImportPlan.kt`, `ArchiveCodec.kt`, `ArchiveCodec.android.kt`,
    `ArchiveCodec.ios.kt`, and the two tests
  - `shared/build.gradle.kts` — the `Shared` framework binary on both iOS targets

Because the source is a working tree rather than a commit, this copy corresponds to no immutable
revision of Prime-Notes. Once 2b is committed, that commit becomes the honest answer here.

## Re-syncing
```
### 10-compile.txt
```
Starting a Gradle Daemon (subsequent builds will be faster)
> Task :shared:kmpPartiallyResolvedDependenciesChecker
> Task :shared:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :shared:downloadKotlinNativeDistribution
Kotlin/Native bundle directory /Users/runner/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.20 is not empty. Native bundle files will be overwritten.
Downloading native dependencies (LLVM, sysroot etc). This is a one-time action performed only on the first run of the compiler.
Downloading dependency https://download.jetbrains.com/kotlin/native/resources/llvm/19-aarch64-macos/llvm-19-aarch64-macos-essentials-81.tar.gz to /Users/runner/.konan/dependencies/cache/llvm-19-aarch64-macos-essentials-81.tar.gz
Done.
Extracting dependency: /Users/runner/.konan/dependencies/cache/llvm-19-aarch64-macos-essentials-81.tar.gz into /Users/runner/.konan/dependencies
Downloading dependency https://download.jetbrains.com/kotlin/native/libffi-3.3-1-macos-arm64.tar.gz to /Users/runner/.konan/dependencies/cache/libffi-3.3-1-macos-arm64.tar.gz
Done.
Extracting dependency: /Users/runner/.konan/dependencies/cache/libffi-3.3-1-macos-arm64.tar.gz into /Users/runner/.konan/dependencies

> Task :shared:compileKotlinIosSimulatorArm64 FROM-CACHE
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/compile-1790378631559.json

BUILD SUCCESSFUL in 34s
3 actionable tasks: 2 executed, 1 from cache
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.1/userguide/configuration_cache_enabling.html
```
### 20-link.txt
```
> Task :shared:kmpPartiallyResolvedDependenciesChecker
> Task :shared:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:downloadKotlinNativeDistribution UP-TO-DATE
> Task :shared:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :shared:linkDebugFrameworkIosSimulatorArm64
w: The number of threads 4 is more than the number of processors 3
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/link-1790378662713.json

BUILD SUCCESSFUL in 31s
4 actionable tasks: 2 executed, 2 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.1/userguide/configuration_cache_enabling.html
```
### 30-tests.txt
```
> Task :shared:kmpPartiallyResolvedDependenciesChecker
> Task :shared:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:downloadKotlinNativeDistribution UP-TO-DATE
> Task :shared:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :shared:iosSimulatorArm64ProcessResources NO-SOURCE
> Task :shared:iosSimulatorArm64MainKlibrary UP-TO-DATE
> Task :shared:compileTestKotlinIosSimulatorArm64

> Task :shared:linkDebugTestIosSimulatorArm64
w: The number of threads 4 is more than the number of processors 3

> Task :shared:iosSimulatorArm64Test
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/test-1790378694262.json

BUILD SUCCESSFUL in 2m 47s
6 actionable tasks: 4 executed, 2 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.1/userguide/configuration_cache_enabling.html
```
### 40-framework.txt
```
shared-ios/shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework
-rwxr-xr-x  1 runner  staff  8546008 Sep 25 23:24 shared-ios/shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared
```
