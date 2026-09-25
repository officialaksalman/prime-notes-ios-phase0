# :shared — iOS verification

Run [36200470046](https://github.com/officialaksalman/prime-notes-ios-phase0/actions/runs/36200470046) on `macos-15`.

Sandbox commit: `a036dbe94fffc783ce44f1bff7dbe8c560c6adc5`

## shared-ios-logs
```
sha=a036dbe94fffc783ce44f1bff7dbe8c560c6adc5
compileKotlinIosSimulatorArm64=success
linkDebugFrameworkIosSimulatorArm64=success
iosSimulatorArm64Test=failure
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
      65
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
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/compile-1790378369242.json

BUILD SUCCESSFUL in 27s
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
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/link-1790378395698.json

BUILD SUCCESSFUL in 20s
4 actionable tasks: 2 executed, 2 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.1/userguide/configuration_cache_enabling.html
```
### 30-tests.txt
```
O-SOURCE
> Task :shared:iosSimulatorArm64MainKlibrary UP-TO-DATE

> Task :shared:compileTestKotlinIosSimulatorArm64 FAILED
e: file:///Users/runner/work/prime-notes-ios-phase0/prime-notes-ios-phase0/shared-ios/shared/src/commonTest/kotlin/com/primenotes/bd/domain/account/PasswordPolicyTest.kt:59:9 Name contains illegal characters: ",".
e: file:///Users/runner/work/prime-notes-ios-phase0/prime-notes-ios-phase0/shared-ios/shared/src/commonTest/kotlin/com/primenotes/bd/domain/backup/ImportPlanTest.kt:19:9 Name contains illegal characters: ",".
e: file:///Users/runner/work/prime-notes-ios-phase0/prime-notes-ios-phase0/shared-ios/shared/src/commonTest/kotlin/com/primenotes/bd/domain/backup/ImportPlanTest.kt:86:9 Name contains illegal characters: ",".
e: file:///Users/runner/work/prime-notes-ios-phase0/prime-notes-ios-phase0/shared-ios/shared/src/commonTest/kotlin/com/primenotes/bd/domain/backup/NoteArchiveTest.kt:152:51 None of the following candidates is applicable:

fun Array<out Byte>.toByteArray(): ByteArray:
  Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
fun Array<out Byte>.toByteArray(): ByteArray

fun Collection<Byte>.toByteArray(): ByteArray:
  Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
fun Collection<Byte>.toByteArray(): ByteArray

fun UByteArray.toByteArray(): ByteArray:
  Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
fun UByteArray.toByteArray(): ByteArray

fun ImmutableBlob.toByteArray(startIndex: Int = ..., endIndex: Int = ...): ByteArray:
  Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
fun ImmutableBlob.toByteArray(startIndex: Int = ..., endIndex: Int = ...): ByteArray
e: file:///Users/runner/work/prime-notes-ios-phase0/prime-notes-ios-phase0/shared-ios/shared/src/commonTest/kotlin/com/primenotes/bd/domain/backup/NoteArchiveTest.kt:158:88 None of the following candidates is applicable:

fun Array<out Byte>.toByteArray(): ByteArray:
  Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
fun Array<out Byte>.toByteArray(): ByteArray

fun Collection<Byte>.toByteArray(): ByteArray:
  Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
fun Collection<Byte>.toByteArray(): ByteArray

fun UByteArray.toByteArray(): ByteArray:
  Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
fun UByteArray.toByteArray(): ByteArray

fun ImmutableBlob.toByteArray(startIndex: Int = ..., endIndex: Int = ...): ByteArray:
  Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
fun ImmutableBlob.toByteArray(startIndex: Int = ..., endIndex: Int = ...): ByteArray
gradle/actions: Writing build results to /Users/runner/work/_temp/.gradle-actions/build-results/test-1790378416372.json

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':shared:compileTestKotlinIosSimulatorArm64' (registered in build file 'shared/build.gradle.kts').
> Compilation finished with errors

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':shared:compileTestKotlinIosSimulatorArm64' (registered in build file 'shared/build.gradle.kts').
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.lambda$executeIfValid$1(ExecuteActionsTaskExecuter.java:135)
	at org.gradle.internal.Try$Failure.ifSuccessfulOrElse(Try.java:288)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:133)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:121)
	at org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter.execute(ProblemsTaskPathTrackingTaskExecuter.java:41)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.DefaultNodeExecutor.executeLocalTaskNode(DefaultNodeExecutor.java:55)
	at org.gradle.execution.plan.DefaultNodeExecutor.execute(DefaultNodeExecutor.java:34)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:355)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:343)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:339)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:84)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:339)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:328)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:65)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
Caused by: org.jetbrains.kotlin.backend.konan.KonanCompilationException: Compilation finished with errors
	at org.jetbrains.kotlin.cli.bc.K2Native$Companion.mainNoExitWithRenderer$lambda$0(K2Native.kt:230)
	at org.jetbrains.kotlin.util.UtilKt.profileIf(Util.kt:22)
	at org.jetbrains.kotlin.util.UtilKt.profile(Util.kt:16)
	at org.jetbrains.kotlin.cli.bc.K2Native$Companion.mainNoExitWithRenderer(K2Native.kt:228)
	at org.jetbrains.kotlin.cli.bc.K2NativeKt.mainNoExitWithGradleRenderer(K2Native.kt:246)
	at org.jetbrains.kotlin.cli.utilities.MainKt$daemonMain$1.invoke(main.kt:47)
	at org.jetbrains.kotlin.cli.utilities.MainKt$daemonMain$1.invoke(main.kt:47)
	at org.jetbrains.kotlin.cli.utilities.MainKt.mainImpl(main.kt:20)
	at org.jetbrains.kotlin.cli.utilities.MainKt.inProcessMain(main.kt:53)
	at org.jetbrains.kotlin.cli.utilities.MainKt.daemonMain(main.kt:47)
	at org.jetbrains.kotlin.internal.compilerRunner.native.KotlinNativeToolRunner.runInProcess(KotlinNativeToolRunner.kt:176)
	at org.jetbrains.kotlin.internal.compilerRunner.native.KotlinNativeToolRunner.runTool(KotlinNativeToolRunner.kt:66)
	at org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile$compile$1.invoke(KotlinNativeTasks.kt:599)
	at org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile$compile$1.invoke(KotlinNativeTasks.kt:583)
	at org.jetbrains.kotlin.compilerRunner.ReportUtilsKt.addBuildMetricsForTaskAction(reportUtils.kt:285)
	at org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile.compile(KotlinNativeTasks.kt:583)
	at org.gradle.internal.reflect.JavaMethod.invoke(JavaMethod.java:125)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.doExecute(StandardTaskAction.java:58)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.execute(StandardTaskAction.java:51)
	at org.gradle.api.internal.project.taskfactory.StandardTaskAction.execute(StandardTaskAction.java:29)
	at org.gradle.api.internal.tasks.execution.TaskExecution$3.run(TaskExecution.java:259)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeAction(TaskExecution.java:244)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeActions(TaskExecution.java:227)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeWithPreviousOutputFiles(TaskExecution.java:210)
	at org.gradle.api.internal.tasks.execution.TaskExecution.execute(TaskExecution.java:176)
	at org.gradle.internal.execution.steps.ExecuteStep.executeInternal(ExecuteStep.java:167)
	at org.gradle.internal.execution.steps.ExecuteStep.access$000(ExecuteStep.java:47)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:137)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:134)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:134)
	at org.gradle.internal.execution.steps.ExecuteStep$Mutable.execute(ExecuteStep.java:80)
	at org.gradle.internal.execution.steps.CancelExecutionStep.execute(CancelExecutionStep.java:42)
	at org.gradle.internal.execution.steps.TimeoutStep.executeWithoutTimeout(TimeoutStep.java:75)
	at org.gradle.internal.execution.steps.TimeoutStep.execute(TimeoutStep.java:55)
	at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:51)
	at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:29)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.executeMutable(RemovePreviousOutputsStep.java:67)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.executeMutable(RemovePreviousOutputsStep.java:39)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:42)
	at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:24)
	at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:69)
	at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:46)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.executeMutable(ResolveInputChangesStep.java:39)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.executeMutable(ResolveInputChangesStep.java:28)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:189)
	at org.gradle.internal.execution.steps.BuildCacheStep.executeAndStoreInCache(BuildCacheStep.java:145)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$executeWithCache$3(BuildCacheStep.java:104)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$executeWithCache$1(BuildCacheStep.java:104)
	at org.gradle.internal.Try$Success.map(Try.java:170)
	at org.gradle.internal.execution.steps.BuildCacheStep.executeWithCache(BuildCacheStep.java:88)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$0(BuildCacheStep.java:75)
	at org.gradle.internal.Either$Left.fold(Either.java:116)
	at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:62)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:74)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:49)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.executeMutable(StoreExecutionStateStep.java:46)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.executeMutable(StoreExecutionStateStep.java:35)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:75)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:35)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)
	at org.gradle.internal.execution.steps.ResolveMutableCachingStateStep.executeDelegate(ResolveMutableCachingStateStep.java:70)
	at org.gradle.internal.execution.steps.ResolveMutableCachingStateStep.executeDelegate(ResolveMutableCachingStateStep.java:32)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:69)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:37)
	at org.gradle.internal.execution.steps.ResolveChangesStep.executeMutable(ResolveChangesStep.java:63)
	at org.gradle.internal.execution.steps.ResolveChangesStep.executeMutable(ResolveChangesStep.java:34)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.ValidateStep$Mutable.executeDelegate(ValidateStep.java:79)
	at org.gradle.internal.execution.steps.ValidateStep$Mutable.executeDelegate(ValidateStep.java:65)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:105)
	at org.gradle.internal.execution.steps.ValidateStep$Mutable.execute(ValidateStep.java:65)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.executeMutable(CaptureMutableStateBeforeExecutionStep.java:86)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.execute(CaptureMutableStateBeforeExecutionStep.java:65)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.execute(CaptureMutableStateBeforeExecutionStep.java:45)
	at org.gradle.internal.execution.steps.SkipEmptyMutableWorkStep.executeWithNonEmptySources(SkipEmptyMutableWorkStep.java:210)
	at org.gradle.internal.execution.steps.SkipEmptyMutableWorkStep.executeMutable(SkipEmptyMutableWorkStep.java:90)
	at org.gradle.internal.execution.steps.SkipEmptyMutableWorkStep.executeMutable(SkipEmptyMutableWorkStep.java:53)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.executeMutable(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.executeMutable(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.executeMutable(HandleStaleOutputsStep.java:77)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.executeMutable(HandleStaleOutputsStep.java:43)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.lambda$executeMutable$0(AssignMutableWorkspaceStep.java:34)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:305)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.executeMutable(AssignMutableWorkspaceStep.java:30)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.executeMutable(AssignMutableWorkspaceStep.java:21)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:40)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:23)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:56)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:38)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:68)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:132)
	... 30 more


BUILD FAILED in 2s
4 actionable tasks: 2 executed, 2 up-to-date
```
### 40-framework.txt
```
shared-ios/shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework
-rwxr-xr-x  1 runner  staff  8546008 Sep 25 23:20 shared-ios/shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared
```
