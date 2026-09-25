/*
 * The shared, multiplatform module.
 *
 * Phase 1 established the structure. Phase 2 moves the domain layer here: it is already pure
 * Kotlin — no `android.*`, and only four files touch `java.*` at all — so most of it moves
 * verbatim, and the Kotlin package stays `com.primenotes.bd...` so no import anywhere changes.
 *
 * Two plugins, deliberately:
 *
 *   org.jetbrains.kotlin.multiplatform        the Kotlin Multiplatform plugin.
 *   com.android.kotlin.multiplatform.library  the Android target for a KMP module.
 *
 * Note what is NOT here and cannot be: `com.android.library` (AGP 9 no longer allows it beside
 * the KMP plugin) and `org.jetbrains.kotlin.android` (AGP 9's built-in Kotlin supplies Kotlin to
 * `:app`, and applying the old plugin beside it is an error).
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    // `RichTextDocument` is `@Serializable`, so this module needs the serialization compiler —
    // an annotation only takes effect in the module that declares it.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // AGP 9's KMP DSL. `androidLibrary { }` is the deprecated spelling of this same block, and
    // `androidTarget()` with `com.android.library` is no longer an option at all.
    android {
        // The Android artefact's own namespace. It is not the Kotlin package, and the app
        // already uses `com.primenotes.bd`, so this stays distinct.
        namespace = "com.primenotes.bd.shared"
        compileSdk = 37
        minSdk = 26

        // The Android-KMP plugin creates no test components unless asked, to keep builds lean.
        // Asking is what makes `commonTest` mean something on this machine: the host (JVM) test
        // compilation is the one place common tests can actually run without a Mac — the iOS test
        // tasks only execute on a macOS runner. See `commonTest` below.
        withHostTest { }
    }

    // Declared so the module is genuinely multiplatform rather than Android in disguise.
    // Kotlin/Native cannot compile on Windows, so on this machine they are configuration only;
    // a macOS runner compiles them. The x64 simulator target is absent because several
    // dependencies never published it.
    //
    // Both produce a framework named `Shared`: that is what a future iOS host embeds, and what
    // the macOS verification run links to prove these targets build at all. Until that run
    // exists, nothing in this module's iOS half has been compiled by anything.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: both appear in the domain layer's own signatures —
            // repository methods return `Flow` — so `:app` has to see them to compile against it.
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }

        // `kotlin("test")`, not JUnit: a test in `commonTest` is compiled for every target that has
        // a test compilation, so it may not name a JVM-only framework. `kotlin.test` resolves to
        // the platform's own runner underneath — JUnit on the Android host, XCTest on iOS — which
        // is what lets one test run in both places. It also matches the existing JUnit 4 spelling
        // closely enough that the conversions stay mechanical (`@Test`, `assertEquals`, and
        // `assertFailsWith` for the one `assertThrows`).
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // Two Kotlin/Native restrictions bite `commonTest`, and both are invisible on this machine
        // because the Android host compilation accepts them happily:
        //
        //   * a backtick-quoted function name may not contain `,` (nor `.`, `;`, `:`, `[`, `]`,
        //     `/`, `\`, `<`, `>`). A test name that reads well on the JVM fails to compile for iOS
        //     with "Name contains illegal characters".
        //   * `String.toByteArray()` is JVM-only. The multiplatform spelling is
        //     `encodeToByteArray()`.
        //
        // The metadata compilation does not catch either one — only compiling the *tests* for a
        // Native target does. The `shared — iOS` workflow in the phase-0 sandbox is what does
        // that, and it is the only thing that does.
    }
}
