import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Phase 0 spike. NOT production code and NOT part of the production build.
 *
 * Versions live in gradle/libs.versions.toml so the CI matrix can override them
 * with sed rather than editing this file.
 */

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidTarget {
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

android {
    namespace = "phase0.spike"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
