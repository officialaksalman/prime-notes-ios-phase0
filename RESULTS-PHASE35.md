# Phase 3.5 — room3 on iOS

Run [36253841695](https://github.com/officialaksalman/prime-notes-ios-phase0/actions/runs/36253841695) on `macos-15`.

Sandbox commit: `03adc74d517f4665ceafd438d6bf7c6d7571eba9`

## phase35-room3-ios-logs
```
sha=03adc74d517f4665ceafd438d6bf7c6d7571eba9
compileKotlinIosSimulatorArm64=success
linkDebugFrameworkIosSimulatorArm64=success
iosSimulatorArm64Test=success
```
### test results
```
<testsuite name="iosSimulatorArm64Test.phase35.probe.MigrationBehaviourTest" tests="2" skipped="0" failures="0" errors="0" timestamp="2026-09-26T16:04:04.432Z" hostname="sat12-bq150-a00c05bc-15bc-41be-b613-32448975633a-42B3592A0A22.local" time="0.043">
<testsuite name="iosSimulatorArm64Test.phase35.probe.PlatformSupportTest" tests="1" skipped="0" failures="0" errors="0" timestamp="2026-09-26T16:04:04.475Z" hostname="sat12-bq150-a00c05bc-15bc-41be-b613-32448975633a-42B3592A0A22.local" time="0.006">
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

### sources
       8
```
### 10-compile.txt
```
Starting a Gradle Daemon (subsequent builds will be faster)
> Task :probe:kmpPartiallyResolvedDependenciesChecker
> Task :probe:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :probe:downloadKotlinNativeDistribution
Kotlin/Native bundle directory /Users/runner/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.20 is not empty. Native bundle files will be overwritten.
Downloading native dependencies (LLVM, sysroot etc). This is a one-time action performed only on the first run of the compiler.
Downloading dependency https://download.jetbrains.com/kotlin/native/resources/llvm/19-aarch64-macos/llvm-19-aarch64-macos-essentials-81.tar.gz to /Users/runner/.konan/dependencies/cache/llvm-19-aarch64-macos-essentials-81.tar.gz
Done.
Extracting dependency: /Users/runner/.konan/dependencies/cache/llvm-19-aarch64-macos-essentials-81.tar.gz into /Users/runner/.konan/dependencies
Downloading dependency https://download.jetbrains.com/kotlin/native/libffi-3.3-1-macos-arm64.tar.gz to /Users/runner/.konan/dependencies/cache/libffi-3.3-1-macos-arm64.tar.gz
Done.
Extracting dependency: /Users/runner/.konan/dependencies/cache/libffi-3.3-1-macos-arm64.tar.gz into /Users/runner/.konan/dependencies

> Task :probe:kspKotlinIosSimulatorArm64 FROM-CACHE
> Task :probe:copyRoomSchemas
> Task :probe:compileKotlinIosSimulatorArm64 FROM-CACHE
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/compile-1790438480380.json

BUILD SUCCESSFUL in 23s
5 actionable tasks: 3 executed, 2 from cache
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.1/userguide/configuration_cache_enabling.html
```
### 20-link.txt
```
> Task :probe:kmpPartiallyResolvedDependenciesChecker
> Task :probe:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :probe:downloadKotlinNativeDistribution UP-TO-DATE
> Task :probe:kspKotlinIosSimulatorArm64 FROM-CACHE
> Task :probe:copyRoomSchemas NO-SOURCE
> Task :probe:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :probe:linkDebugFrameworkIosSimulatorArm64
w: The number of threads 4 is more than the number of processors 3
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/link-1790438502298.json

BUILD SUCCESSFUL in 23s
5 actionable tasks: 2 executed, 1 from cache, 2 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.1/userguide/configuration_cache_enabling.html
```
### 30-tests.txt
```
> Task :probe:kmpPartiallyResolvedDependenciesChecker
> Task :probe:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :probe:downloadKotlinNativeDistribution UP-TO-DATE
> Task :probe:kspKotlinIosSimulatorArm64 UP-TO-DATE
> Task :probe:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :probe:iosSimulatorArm64ProcessResources NO-SOURCE
> Task :probe:iosSimulatorArm64MainKlibrary UP-TO-DATE
> Task :probe:kspTestKotlinIosSimulatorArm64
> Task :probe:copyRoomSchemas NO-SOURCE

> Task :probe:compileTestKotlinIosSimulatorArm64
w: file:///Users/runner/work/prime-notes-ios-phase0/prime-notes-ios-phase0/phase35/probe/src/commonTest/kotlin/phase35/probe/MigrationBehaviourTest.kt:153:74 Unnecessary non-null assertion (!!) on a non-null receiver of type 'String'.
w: file:///Users/runner/work/prime-notes-ios-phase0/prime-notes-ios-phase0/phase35/probe/src/commonTest/kotlin/phase35/probe/MigrationBehaviourTest.kt:189:74 Unnecessary non-null assertion (!!) on a non-null receiver of type 'String'.

> Task :probe:linkDebugTestIosSimulatorArm64
w: The number of threads 4 is more than the number of processors 3

> Task :probe:iosSimulatorArm64Test
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/test-1790438526424.json

BUILD SUCCESSFUL in 1m 58s
8 actionable tasks: 5 executed, 3 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.1/userguide/configuration_cache_enabling.html
```
