import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Phase 0 spike. NOT production code and NOT part of the production build.
 *
 * AGP 9 NOTE: `com.android.library` is incompatible with the Kotlin Multiplatform plugin
 * from AGP 9.0 on ("Failed to apply plugin 'com.android.internal.library'"). The Android
 * target therefore comes from `com.android.kotlin.multiplatform.library` and is configured
 * inside `kotlin { android { ... } }` — there is no top-level `android {}` block, and
 * `androidTarget()` is not used at all.
 *
 * Consequences of that plugin worth remembering for the real migration:
 *   - no build types / product flavours (single variant)
 *   - no BuildConfig (production injects its Supabase keys through it)
 *   - Java compilation, Android resources and tests are all off unless opted in
 */

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "phase0.spike"
        compileSdk = 37
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

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

            // Spike B — Compose Multiplatform.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            // Spike C — Supabase over Ktor (engine supplied per platform).
            implementation(platform(libs.supabase.bom))
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

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// Room's processor must run for every target, or the generated `actual` database
// implementations are missing on the non-Android ones.
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
