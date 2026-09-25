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

            // Spike B — Compose Multiplatform, declared as direct artifacts because
            // CMP 1.10+ deprecates the `compose.*` DSL accessors at error level.
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)

            // Spike C — Supabase over Ktor (engine supplied per platform).
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

/*
 * Room's processor must run for every target, or the generated `actual` database
 * implementations are missing and the target will not compile.
 *
 * The configuration names are DISCOVERED rather than assumed. With AGP 9's
 * `com.android.kotlin.multiplatform.library` there is no `kspAndroid` configuration,
 * and a hard-coded add() fails the entire build at configuration time — which hides
 * everything else. This reports what actually exists and wires what it can, so a
 * missing processor shows up as a clear finding rather than a stack trace.
 */
afterEvaluate {
    val wanted = listOf("kspAndroid", "kspIosX64", "kspIosArm64", "kspIosSimulatorArm64")

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

    val unhandled = present.filterNot { it in wanted || it.contains("ProcessorClasspath") }
    println("PHASE0-KSP unhandled configurations: $unhandled")
}

room {
    schemaDirectory("$projectDir/schemas")
}
