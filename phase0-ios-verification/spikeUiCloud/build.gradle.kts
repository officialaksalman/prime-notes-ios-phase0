/*
 * Spike B (Compose Multiplatform on iOS) and Spike C (supabase-kt over Ktor Darwin).
 *
 * Deliberately has NO Room and NO KSP dependency, so an upstream Room problem cannot
 * prevent these two from being measured. Separate module from :spikeRoom.
 */

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SpikeUiCloud"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform, declared as direct artifacts: CMP 1.10+ deprecates
            // the `compose.runtime` / `compose.material3` DSL accessors at error level.
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)

            // Supabase — same client libraries the production app uses.
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
