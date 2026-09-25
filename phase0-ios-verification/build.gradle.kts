import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Phase 0 spike — iOS targets only. NOT production code, NOT part of the production build.
 *
 * WHY THERE IS NO ANDROID TARGET (measured, not assumed):
 *   1. `com.android.library` is incompatible with the Kotlin Multiplatform plugin from
 *      AGP 9.0 on ("Failed to apply plugin 'com.android.internal.library'").
 *   2. Its official replacement, `com.android.kotlin.multiplatform.library`, does not
 *      create a `kspAndroid` configuration, and KSP then dies with
 *      "KotlinMultiplatformAndroidCompilationImpl cannot be cast to KotlinJvmAndroidCompilation".
 *      Room needs KSP, so a shared module with an Android target cannot use Room today.
 *
 * So this spike measures what it still can: Room KMP + FTS4 + migrations on the iOS
 * simulator, Compose Multiplatform on iOS, and Supabase over Ktor Darwin.
 * Android's own health is covered by the production build, not by this module.
 *
 * The Room code below deliberately stays in commonMain: that is what production would
 * look like, and compiling it only for iOS is enough to answer the schema question.
 */

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Phase0Spike"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Spike A — Room KMP + the bundled SQLite driver.
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            // Spike B — Compose Multiplatform, declared as direct artifacts because
            // CMP 1.10+ deprecates the `compose.*` DSL accessors at error level.
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)

            // Spike C — Supabase over Ktor.
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
            implementation(libs.ktor.client.core)

            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

/*
 * Room's processor must run for every target, or the generated `actual` database
 * implementations are missing and the target will not compile.
 *
 * Configuration names are DISCOVERED rather than assumed, and reported, so a missing
 * processor shows up as a clear finding rather than a stack trace.
 */
afterEvaluate {
    val wanted = listOf("kspIosX64", "kspIosArm64", "kspIosSimulatorArm64")

    val present = configurations.names.filter { it.startsWith("ksp") }.sorted()
    println("PHASE0-KSP configurations present: $present")

    wanted.forEach { name ->
        if (configurations.findByName(name) != null) {
            dependencies.add(name, libs.room.compiler)
            println("PHASE0-KSP wired: $name")
        } else {
            println("PHASE0-KSP MISSING: $name")
        }
    }
}

/*
 * The extension is named `room3`, NOT `room`. The official KMP page shows `room { ... }`,
 * but the androidx.room3 Gradle plugin registers its RoomExtension under "room3" — verified
 * by reading the string constants out of room3-gradle-plugin-3.1.0-alpha01.jar. Using `room`
 * is a build-script compile error, and omitting it fails task creation with
 * "No matching Room schema directory for the KSP target ...".
 */
room3 {
    schemaDirectory("$projectDir/schemas")
}
