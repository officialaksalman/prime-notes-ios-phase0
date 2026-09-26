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
    // Room's processor, for the entities and DAOs that moved here in Phase 3. It is the same
    // `androidx.room` 2.8.5 the app already ships: 2.8.5 is multiplatform, so no alpha is needed.
    // It must be wired at the TOP LEVEL of this script — inside `afterEvaluate` KSP has already
    // configured its tasks and silently generates nothing, which is a failure mode that looks like
    // "KSP does not support this module at all".
    alias(libs.plugins.ksp)
    // room3's Gradle plugin, for the `room3 { schemaDirectory(...) }` block at the bottom of this file.
    // Applied *in addition to* the 2.8.5 plugin, not instead of it: this module really does contain two
    // Room major versions — the Android entities and DAOs on 2.8.5, and the iOS database on room3.
    // The two are kept off each other's compilation by the processor wiring in `dependencies {}`.
    alias(libs.plugins.room3)
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

            // The Android Room entities and DAOs. 2.8.5's `room-common` has a `commonMain`/`nativeMain`
            // split, so `@Entity`, `@Dao`, `@Fts4` and friends are legal in a common source set
            // and the same declarations compile for every target. Only the *builder* needs a
            // per-platform `actual`; that stays in `:app` for now.
            //
            // These are **production**. `:app` generates every DAO implementation and the
            // `AutoMigration_6_7_Impl` from them, and `prime_notes.db` version 7 is what ships. Nothing
            // in this module may change them — see `IosEntities.kt` for why the iOS database
            // duplicates them rather than sharing them.
            api(libs.androidx.room.runtime)

            // supabase-kt, the same pinned version the app ships. It is KMP, which is why the sync
            // gateway is common code: one client, one set of call sequences, one set of wire
            // models, and only the HTTP engine differs per platform.
            //
            // Spelled out as coordinates because the catalog's supabase entries are versionless —
            // `:app` gets them from the BOM, and this module has no BOM.
            api("io.github.jan-tennert.supabase:postgrest-kt:${libs.versions.supabase.get()}")
            api("io.github.jan-tennert.supabase:auth-kt:${libs.versions.supabase.get()}")
            // Edge Functions. The account repository calls a server-side function to have the
            // server be the authority on password strength, so this is not optional to it.
            api("io.github.jan-tennert.supabase:functions-kt:${libs.versions.supabase.get()}")
            // Likewise for Ktor: a shorter alias that is a prefix of another
            // (`ktor-client-darwin`, `ktor-client-okhttp`) would claim the shorter name.
            api("io.ktor:ktor-client-core:${libs.versions.ktor.get()}")
        }

        // The iOS SQLite driver.
        //
        // `sqlite-bundled`, and its version has to match room3's own `androidx.sqlite` version. The
        // catalog's shared `sqlite` key is 2.6.2 — the version Room 2.8.5 declares for its iOS
        // artifacts — but room3 3.1.0-alpha01 is built against 2.8.0-alpha01, and Gradle's conflict
        // resolution then leaves the 2.6.2 *request* on the classpath beside a 2.8.0-alpha01
        // resolution, so `BundledSQLiteDriver` does not resolve and `IosDatabaseFactory` fails with
        // "Unresolved reference 'bundled'". The Phase 3.5 probe, which built and ran a real iOS
        // database, pins the whole `androidx.sqlite` group to 2.8.0-alpha01 for exactly this reason.
        //
        // `api`, not `implementation`, because `IosDatabaseFactory.openAt` takes the driver as a
        // parameter, so the driver type is part of that public signature.
        iosMain.dependencies {
            api(libs.androidx.sqlite.bundled)
            // `androidx.sqlite:sqlite` itself, in `commonMain`, and it is required rather than
            // incidental: it is the module that declares `SQLiteDriver` and `SQLiteConnection`, the
            // types `IosDatabaseFactory.openAt` takes and the generated `IosDatabase_Impl` calls. The
            // bundled driver alone does not bring them in — `sqlite-bundled` publishes a 9 KB Kotlin
            // API klib that holds the driver and nothing else. The Phase 3.5 probe declares exactly
            // this pair, and with only `sqlite-bundled` declared the same import fails to resolve.
            api(libs.androidx.sqlite)

            // The iOS database, on `androidx.room3` 3.1.0-alpha01. A second Room major version
            // alongside 2.8.5, which is the point: room3 is a new package *and* a new maven group, so
            // both live in this project and Android keeps its database untouched.
            //
            // `iosMain` only, and that is load-bearing. The `androidx.room3` types are declared in
            // `iosMain` (see `data/local/iosdb/IosEntities.kt`), and putting room3 on the Android
            // classpath would put a second Room version where the processor for 2.8.5 is running —
            // which fails with "[MissingType] … references a type that is not present" and names no
            // missing type. Each platform's compilation sees exactly one Room version.
            implementation(libs.room3.runtime)

            // The HTTP engine for the iOS target. `CloudFailure.ios.kt` classifies failures by
            // reading the `NSError` that this engine's `DarwinHttpRequestException` carries, so the
            // classification is written against this version's exception rather than a guess.
            implementation(libs.ktor.client.darwin)
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

        // The iOS test source set, so `iosSimulatorArm64Test` exists. It is what runs
        // `IosMigrationBehaviourTest` on a simulator — the only check in the project that opens the iOS
        // database and runs its migrations. Without this source set there is nothing to execute, and
        // the migration would only ever be proven to compile.
        //
        // `kotlinx-coroutines-test` is needed for `runTest`, because the migrations are `suspend` over a
        // `SQLiteConnection` and the test has to drive them. It is added to the iOS tests rather than to
        // `commonTest` for the same reason `sqlite-bundled-jvm` is host-only: it is not a dependency the
        // common tests need, and declaring it twice would be noise.
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
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

/*
 * Room's processor wiring. Two Room major versions live in this module, so each processor runs only on
 * the targets its own version serves. Looked up rather than assumed, so a missing configuration is
 * reported instead of killing the build script with a name that does not exist.
 *
 * The Android name is `kspAndroid` under the AGP KMP plugin (not `kspAndroidMain`, which is the
 * *task*); the iOS ones match the target names.
 */
dependencies {
    // Room 2.8.5's processor — the **Android** target only.
    //
    // It used to run on all three targets, which was fine while the only Room-annotated declarations
    // here were 2.8.5's. Now that `iosMain` also declares a database on `androidx.room3`, having 2.8.5's
    // processor on the iOS pass is actively wrong: the log shows
    //   loaded provider(s): [androidx.room.RoomKspProcessor, androidx.room3.RoomProcessor]
    // and 2.8.5's processor then reads the room3-annotated `IosDatabase`, does not recognise
    // `androidx.room3.Database`, and fails with
    //   [MissingType]: Element '…iosdb.IosDatabase' references a type that is not present
    // naming no missing type — the type it cannot see is the annotation itself.
    //
    // The Android entities and DAOs stay in `commonMain` and still need this processor there, because
    // `:app` compiles their generated implementations. So this stays on `kspAndroid` and *only* there.
    if (configurations.findByName("kspAndroid") != null) {
        add("kspAndroid", libs.androidx.room.compiler)
    } else {
        logger.lifecycle("PHASE4-KSP MISSING configuration 'kspAndroid'")
    }

    // room3's processor — the **iOS** targets only, for the mirror-image reason: it must not see the
    // `androidx.room` 2.8.5 entities, or it will fail on those in the same way.
    listOf("kspIosArm64", "kspIosSimulatorArm64").forEach { name ->
        if (configurations.findByName(name) != null) {
            add(name, libs.room3.compiler)
        } else {
            logger.lifecycle("PHASE4-KSP MISSING configuration '$name'")
        }
    }
}

/*
 * Where room3 writes the schema it generates for the iOS database.
 *
 * `IosDatabase` is declared `exportSchema = true`, and room3's processor needs to be told where to put
 * the result. Without this block the iOS KSP pass fails with
 *   [MissingType]: Element 'com.primenotes.bd.data.local.iosdb.IosDatabase' references a type that is
 *   not present
 * naming no missing type, because the unresolved thing is the unconfigured schema destination. That is
 * the actual root cause, found by bisecting the Phase 3.5 probe — it configures this block, `:shared`
 * did not, and the delta reproduced the failure.
 *
 * `:shared/schemas`, NOT `:app/schemas`. Room 2.8.5's committed schemas in
 * `app/schemas/com.primenotes.bd.data.local.PrimeNotesDatabase/` are **production** — they are the
 * record of what Android actually creates, and `IosSchemaParityTest` compares against `7.json` from
 * there. This project must never write to that directory. A separate output directory also means the
 * two Room versions cannot overwrite each other's schema files, which is what the split processors in
 * `dependencies {}` above exist to prevent on the annotation side.
 */
room3 {
    schemaDirectory("$projectDir/schemas")
}

afterEvaluate {
    logger.lifecycle(
        "PHASE3-KSP configurations: " + configurations.names.filter { it.startsWith("ksp") }.sorted()
    )
}
