/*
 * Root of the Phase 0 spike build.
 *
 * The plugins are declared here with `apply false` on purpose. Both spike modules apply the
 * Kotlin Multiplatform plugin, and without a root declaration Gradle fails to create the
 * native link tasks:
 *
 *   Cannot set the value of task ':spikeUiCloud:linkDebugTestIosSimulatorArm64' property
 *   'kotlinNativeBundleBuildService' ... loaded with InstrumentingVisitableURLClassLoader
 *   This can be caused by a plugin being applied to two sibling projects and then using a
 *   shared build service. ... add the problematic plugin with `apply false` to the root build script.
 *
 * Spikes:
 *   :spikeRoom     Room KMP + SQLite + FTS4 + v1->v2 migration on the iOS simulator
 *   :spikeUiCloud  Compose Multiplatform + supabase-kt over Ktor Darwin on iOS
 *
 * Versions are pinned in gradle/libs.versions.toml and rewritten per CI matrix leg.
 */

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
