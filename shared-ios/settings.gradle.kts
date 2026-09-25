/*
 * A side-by-side build of `:shared`, so the iOS verification run can build it from this public
 * repository. macOS runner minutes are free on a public repository and billable on the private
 * one, which is the only reason the module is duplicated here at all.
 *
 * Deliberately a SEPARATE Gradle build, like `../phase0-ios-verification`. Run with:
 *   ./gradlew -p shared-ios :shared:iosSimulatorArm64Test
 *
 * It is not included by the production settings.gradle.kts, so nothing here can affect the app.
 */
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "prime-notes-shared-ios"

include(":shared")
