# `shared-ios/` — a copy of the `:shared` module

This is a **copy** of the `shared` module from the private `Prime-Notes` repository, committed
here so the iOS verification workflow can build it from a public repository. macOS runner minutes
are free on a public repository and billable on the private one, and that is the only reason this
directory exists.

Nothing here is edited in place. A fix belongs upstream in `Prime-Notes`; this is a copy, and the
copy is what drifts if that is forgotten.

## Where this copy came from

- Prime-Notes commit **`cbc0337`**, plus the **uncommitted Phase 4 working tree** on top of it,
  which is in no commit yet. The difference is the whole point of the run:
  - `iosMain/…/data/local/iosdb/` — the entire iOS database on `androidx.room3` 3.1.0-alpha01:
    `IosEntities.kt` (7 entities), `IosDaos.kt` (4 DAOs), `IosDatabase.kt` (`@Database`,
    `@ConstructedBy`, and the v6 → v7 `AutoMigration`), `IosMigrations.kt` (the five hand-written
    migrations and the v6 → v7 spec), and `IosDatabaseFactory.kt` (the `Room.databaseBuilder` call
    that registers them)
  - `iosTest/` — `IosMigrationBehaviourTest`, which builds a real v6 database and lets Room run the
    real v6 → v7 migration on the simulator
  - `schemas/` — the generated iOS `7.json`, plus a seeded `6.json` (see that directory's README)
  - `shared/build.gradle.kts` — room3's runtime and processor on the iOS targets, the
    `room3 { schemaDirectory(...) }` block, the split KSP wiring, and the `Shared` framework binary
  - `gradle/libs.versions.toml` — the `room3` entries, and `androidx.sqlite` realigned to 2.8.0-alpha01

Because the source is a working tree rather than a commit, this copy corresponds to no immutable
revision of Prime-Notes. Once Phase 4 is committed, that commit becomes the honest answer here.

## Re-syncing

From a directory holding a checkout of both repositories:

    cp Prime-Notes/shared/build.gradle.kts      prime-notes-ios-phase0/shared-ios/shared/
    cp -R Prime-Notes/shared/src                prime-notes-ios-phase0/shared-ios/shared/
    cp Prime-Notes/gradle/libs.versions.toml    prime-notes-ios-phase0/shared-ios/gradle/

Then update the commit above in the same commit, so the record moves with the copy.

## Not copied

- `androidHostTest/…/IosSchemaParityTest.kt`. It compares the generated iOS schema against
  `app/schemas/com.primenotes.bd.data.local.PrimeNotesDatabase/7.json`, which is **production** data
  in the private repository and is not part of this copy. It runs in the project's own
  `:shared:testAndroidHostTest`, where both files are present. Remove it again after every sync.
- `shared/build/`, and anything else the production repository does not commit. `.gradle/` likewise.

## What this run proves, and what it does not

It proves `:shared`'s iOS half **compiles, links, and runs** on a real simulator: the domain layer,
the three seams (`SecRandomCopyBytes`, the Foundation zip codec, `TimeSource`), and now the iOS
database with its migration chain. `IosMigrationBehaviourTest` is the part with no other execution
path — nothing else in the project opens an iOS database.

It does **not** prove anything about Android. Android's database is `androidx.room` 2.8.5, lives in
`:app`, and is untouched by any of this; it is verified by the project's own `:app:testDebugUnitTest`,
which is not run from here.

## A caveat worth stating plainly

This directory is in a **public** repository. The module it mirrors is not. Whatever is in
`shared/src` above is published, so the sync procedure should be used deliberately rather than on
a schedule.

## Revisions

- **Third sync (Phase 4).** The iOS database arrived: a second Room major version in one project,
  with the entities and DAOs deliberately duplicated, because `@Entity` is `androidx.room.Entity` on
  Android and `androidx.room3.Entity` on iOS. Android's copies are production and are not touched.
  Getting it to compile took four real fixes, all recorded in the upstream build file: room3's
  Gradle plugin, for `room3 { schemaDirectory(…) }`, without which the processor failed with an
  opaque `[MissingType]`; the room3 processor scoped to the iOS KSP configurations only; each Room
  version's processor kept off the other's targets, since 2.8.5's cannot even initialise on iOS
  (its `DatabaseVerifier` needs a JDBC driver); and `androidx.sqlite` moved to 2.8.0-alpha01 — the
  version room3 is built against, without which `BundledSQLiteDriver` does not resolve.
- **Second sync.** The first iOS run of this copy compiled and linked, and then failed to compile
  the tests for Kotlin/Native: three backtick-quoted test names contained a comma, which Native
  rejects, and two call sites used `String.toByteArray()`, which is JVM-only. Both were fixed
  upstream in Prime-Notes and re-copied. The Android host compilation had accepted all five
  without complaint, which is the whole reason this directory exists.
