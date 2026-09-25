/*
 * Phase 0 spike build — deliberately a SEPARATE Gradle build.
 *
 * Not included by the production settings.gradle.kts, so the Android app cannot be
 * affected by anything here. Run with:
 *   ./gradlew -p phase0-ios-verification <task>
 *
 * Two modules, on purpose: a Room/ksp failure must not prevent Compose Multiplatform
 * and Supabase from being verified. They share nothing but the version catalog.
 */
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "prime-notes-phase0"

include(":spikeRoom")
include(":spikeUiCloud")
