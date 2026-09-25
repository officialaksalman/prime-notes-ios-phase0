/*
 * Spike A — Room KMP on iOS. Isolated from the Compose/Supabase spike on purpose.
 *
 * The schema mirrors the production one: a `notes` table with a String primary key,
 * an external-content FTS4 index over it, and a v1 -> v2 migration that introduces that
 * index. Room's own processor generates the `actual` database implementations, which is
 * why KSP must be wired for every iOS target.
 */

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SpikeRoom"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}

/*
 * Room's processor must run for every target, or the generated `actual` database
 * implementations are missing and compilation fails with
 *   "Expected Phase0DatabaseConstructor has no actual declaration in module <commonMain> for Native".
 *
 * These MUST be added here, at the top level. Adding them inside afterEvaluate is too late:
 * KSP has already configured its tasks and silently generates nothing, which is exactly what
 * the first attempt did. The names were confirmed by a discovery run (there is no kspAndroid
 * under AGP 9's KMP plugin, but the three iOS ones exist).
 */
dependencies {
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

/** Reported for the record, so the Phase 0 findings show what KSP actually offered. */
afterEvaluate {
    println(
        "PHASE0-KSP configurations present: " +
            configurations.names.filter { it.startsWith("ksp") }.sorted()
    )
}

/*
 * The extension is named `room3`, NOT `room`. The official KMP page shows `room { ... }`,
 * but androidx.room3 registers its RoomExtension under "room3" (read out of
 * room3-gradle-plugin-3.1.0-alpha01.jar). Using `room` is a build-script compile error,
 * and omitting it fails task creation with "No matching Room schema directory for the KSP target".
 */
room3 {
    schemaDirectory("$projectDir/schemas")
}
