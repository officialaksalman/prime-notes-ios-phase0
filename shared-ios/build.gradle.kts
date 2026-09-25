/*
 * The root script for the `shared-ios` build.
 *
 * The two multiplatform plugins are declared here, once, with `apply false`, for the reason the
 * production root script documents: applying a plugin to a sibling project without a root
 * declaration makes Gradle fail to create the native link tasks —
 *
 *   "Cannot set the value of task ':shared:linkDebugTestIosSimulatorArm64' property
 *    'kotlinNativeBundleBuildService' ... This can be caused by a plugin being applied to two
 *    sibling projects and then using a shared build service."
 *
 * There is only one project here, so that exact failure cannot arise, but the declaration costs
 * nothing and keeps this build honest about how the module is normally wired.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
