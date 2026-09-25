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
 * Configuration names are DISCOVERED rather than assumed, and reported, so a missing
 * processor is a clear finding rather than a stack trace.
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
 * but androidx.room3 registers its RoomExtension under "room3" (read out of
 * room3-gradle-plugin-3.1.0-alpha01.jar). Using `room` is a build-script compile error,
 * and omitting it fails task creation with "No matching Room schema directory for the KSP target".
 */
room3 {
    schemaDirectory("$projectDir/schemas")
}
