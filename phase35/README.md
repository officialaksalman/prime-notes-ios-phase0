# Prime Notes — Phase 3.5 probe: can room3 run this app's database on iOS?

A self-contained throwaway Gradle build. Nothing here is imported into Prime Notes, and Android's
`androidx.room` 2.8.5 implementation is never touched. Its only purpose is to answer one question
with evidence rather than inference.

## The question

`androidx.room` 2.8.5 has multiplatform *annotations* — `@Entity`, `@Dao`, `@Fts4`,
`@ConstructedBy` — and real iOS klibs, so the schema and the DAOs can live in a common source set.
That is what Phase 3 of the migration did. But `room-common` contains **no** `RoomDatabase`, no
`Builder`, no `addMigrations`, no `Migration` and no `AutoMigrationSpec`, so 2.8.5 cannot construct a
database, register a migration, or write one on any non-Android target. The candidate is
`androidx.room3` 3.1.0-alpha01.

## What is in here

| Path | What it is |
|---|---|
| `probe/src/commonMain/.../Schema.kt` | The seven entities, copied from Prime Notes' own `app/schemas/…/7.json` |
| `probe/src/commonMain/.../ProbeDatabase.kt` | The `@Database`, the `@ConstructedBy` companion, the v6→v7 auto-migration spec, two hand-written KMP `Migration`s |
| `probe/src/commonMain/.../Daos.kt` | CRUD, soft delete, FTS4 search, and the two sync-metadata tables |
| `probe/schemas/…/6.json` | **Prime Notes' real v6 schema**, committed so the auto-migration is generated from the actual v6 shape — `notes.color` present — and not from an invented one |
| `probe/src/commonTest/...` | The two behavioural tests, driving the *generated* migration |
| `probe/src/iosTest/`, `probe/src/androidHostTest/` | One `probeConnection()` actual per platform, so the same tests run on the Android host and on the iOS simulator |

## The two tests

1. **`v6 to v7 drops colour and keeps every note intact`** — drives the generated
   `ProbeDatabase_AutoMigration_6_7_Impl` against a v6 database holding three notes (one trashed),
   a `sync_cursor` row and a `sync_account_state` row. Asserts no row is lost, `color` is gone, every
   other column survives, the rich-text body is carried across, the trashed note is still a tombstone,
   and both sync-metadata tables keep their values.
2. **`search still answers with the right notes after the rebuild`** — the assertion that matters
   most. Rebuilding `notes` renumbers its rows, and the external-content FTS index is keyed by row
   id, so an index left alone would answer with notes that no longer match. This checks that
   `sourdough` still finds one note and `honey` the other.

Both run on the Android host against the bundled driver, and again on the iOS simulator against a
real file in the app's Documents directory. Compiling is not the same as working, and FTS4 virtual
tables on Apple's bundled SQLite is exactly the kind of thing that compiles and then misbehaves.

## How to run it

```
./gradlew -p phase35 :probe:iosSimulatorArm64Test     # the iOS simulator (macOS only)
./gradlew -p phase35 :probe:testAndroidHostTest       # the host (any platform)
```

`macos-15` GitHub Actions runs the first automatically; the result is committed to
`RESULTS-PHASE35.md` and read over git, because a workflow run cannot be inspected from outside it.

## Findings so far

Recorded here as they are established, so a later reader does not have to re-derive them.

- **`@ConstructedBy` is required**, with a hand-written
  `expect object … : RoomDatabaseConstructor<T>` beside the `@Database`.
- **`AutoMigrationSpec.onPostMigrate` is `suspend`** in room3; it is a plain function in 2.8.5.
- **`Migration` keeps its `startVersion`/`endVersion` constructor** — the `object : Migration(4, 5)`
  spelling survives. Only `migrate` changes: `suspend`, over `SQLiteConnection` rather than
  `SupportSQLiteDatabase`.
- **`@DeleteColumn` is unchanged** — a plain annotation on the spec class. It is *not* nested under
  `.Entries`; that spelling appears in the processor's own suggestion text and does not compile.
- **`@DeleteColumn` is the only annotation the processor will not infer.** Room refuses a column drop
  that is not declared in as many words, which is the behaviour the app relies on.
- **room3 depends on `androidx.sqlite:sqlite:2.8.0-alpha01`**, which publishes iOS — not on the
  Android-only `sqlite-framework` that produced the original Phase 0 404. That 404 came from a spike
  pinned to sqlite 2.7.1, whose `sqlite-framework` has no iOS artifact; 2.6.2 and 2.8.0-alpha01 do.
- **`sqlite-bundled` publishes per-platform modules.** The JVM one is `sqlite-bundled-jvm`, a
  different artifact, and it has no 2.8.0-alpha01 variant — which is why the host test pins 2.7.1
  while iOS uses 2.8.0-alpha01. Declaring it in `commonTest` rather than `androidHostTest` fails the
  iOS test compile with "no matching variant", because a `commonTest` dependency is inherited by
  every target.
- **KSP configurations are `kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`** — `kspAndroidMain`
  is the *task*, not the configuration, and adding to it fails with "Configuration not found". The
  iOS ones exist only after `kotlin { }` has run.
- **The processor is `room3-compiler`, not `room3-runtime`** — the latter ships no annotation
  processor, and KSP reports "No providers found in processor classpath".
- **FTS4 rejects a parameterised `MATCH` outright.** A `?` in the MATCH position raises an
  exception. This is SQLite, not Room, and identical on every platform — so inlining the search
  term, as `FtsMatchQuery` already does, is required rather than merely convenient.
- **`getText` on a NULL column returns `""`, not `null`.** Reading a nullable column needs
  `isNull` to tell "no formatting" from "an empty document".
- **The generated v6 → v7 migration is correct for iOS**: it rebuilds `notes` without `color`,
  recreates the indices, drops and recreates the FTS4 table, repopulates it by `rowid`, runs
  `foreignKeyCheck`, and calls the spec. It rebuilds `sync_cursor` and `sync_account_state` too.
