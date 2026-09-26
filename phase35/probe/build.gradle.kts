plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    // Wired at the TOP LEVEL, never in afterEvaluate — inside afterEvaluate KSP has already
    // configured its tasks and silently generates nothing.
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    // `android { }` — not `androidLibrary { }` (deprecated) and not `androidTarget()` with
    // `com.android.library` (illegal on AGP 9). The same DSL the real :shared module uses.
    android {
        namespace = "phase35.probe"
        // Present only so the host test has somewhere to run on a Windows developer machine. The
        // probe is about iOS; the Android target is scaffolding.
        compileSdk = 37
        withHostTest { }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Probe"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.room3.runtime)
            implementation(libs.sqlite)
            implementation(libs.kotlinx.coroutines.core)
        }
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        // The iOS test source set, so `iosSimulatorArm64Test` exists. Without it this workflow has
        // nothing to execute on the simulator, which is the entire point of the run.
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // The bundled driver for the *host* only. It must not be declared in commonTest: the JVM
        // artifact has no iOS variant, and a commonTest dependency is inherited by every target's
        // test compilation, which fails the iOS test compile with "no matching variant". The iOS
        // test gets the real driver from iosMain, where it belongs.
        //
        // Named by string: the Android-KMP plugin creates the host test source set but does not
        // expose it as a typed `sourceSets` accessor.
        getByName("androidHostTest").dependencies {
            implementation(libs.sqlite.bundled.host)
        }
    }
}

dependencies {
    // The Android configuration is `kspAndroid` under the AGP KMP plugin — `kspAndroidMain` is the
    // *task*, not the configuration, and adding to it fails with "Configuration not found". The iOS
    // ones match their target names but are only created once `kotlin { }` above has run, which is
    // why this block comes after it.
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { name ->
        if (configurations.findByName(name) != null) {
            add(name, libs.room3.compiler)
        } else {
            logger.lifecycle("PHASE35-KSP MISSING configuration '$name'")
        }
    }
}

room3 {
    // This probe's own directory. Prime Notes' app/schemas is never written to.
    schemaDirectory("$projectDir/schemas")
}
