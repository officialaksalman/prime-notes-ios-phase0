/*
 * Phase 0 spike — deliberately a SEPARATE Gradle build.
 *
 * It is NOT included by the root settings.gradle.kts, so the production Android
 * build does not know this directory exists and cannot be affected by it.
 * Run it with:  ./gradlew -p phase0-ios-verification <task>
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
