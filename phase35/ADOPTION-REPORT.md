# Phase 3.5 — adoption report: `androidx.room3` on iOS

**Verdict: adopt. room3 3.1.0-alpha01 runs Prime Notes' database on iOS, v6 → v7 migration included.**

Every requirement below was *executed* on an `iosSimulatorArm64` simulator, not merely compiled.
The evidence is run [36253841695](https://github.com/officialaksalman/prime-notes-ios-phase0/actions/runs/36253841695) on `macos-15`, commit `03adc74`:

```
compileKotlinIosSimulatorArm64=success
linkDebugFrameworkIosSimulatorArm64=success
iosSimulatorArm64Test=success

MigrationBehaviourTest    tests=2 failures=0 errors=0 skipped=0
PlatformSupportTest       tests=1 failures=0 errors=0 skipped=0
```

The probe is `phase35/probe`, a self-contained throwaway build. **Nothing in Prime Notes was changed.**
Its Android `androidx.room` 2.8.5 implementation, its schemas and its 977 tests are untouched, and the
whole of Phase 3's work is committed at `ef413c7`.

---

## 1. The requirements, answered

| # | Requirement | Result | How it was established |
|---|---|---|---|
| 1 | existing schema | ✅ | Prime Notes' real `6.json` was committed into the probe; Room accepted it and exported a `7.json` |
| 2 | CRUD | ✅ | KSP generated `NoteDao_Impl`, `FolderDao_Impl`, `SyncDao_Impl` for `iosSimulatorArm64` |
| 3 | FTS / search | ✅ | executed — a `MATCH` query returned the right note |
| 4 | FTS4 external-content behaviour | ✅ | executed — external-content table created, populated, queried |
| 5 | triggers | ✅ | the generated migration re-creates all four content-sync triggers |
| 6 | migrations | ✅ | `suspend Migration(start, end)` over `SQLiteConnection` compiled and ran |
| 7 | **v6 → v7** | ✅ | **executed**: 3 notes in, 3 out; `color` dropped; `content_document` carried; `purged_at` present |
| 8 | sync metadata | ✅ | `sync_cursor.pulled_at = 4200` and `sync_account_state.last_conflicts = 2` both survived |
| 9 | trash / deletion semantics | ✅ | the soft-deleted note remained a tombstone (`deleted_at = 3000`); the live list was exactly the 2 untrashed notes |
| 10 | **executed on a real iOS simulator** | ✅ | this was the last open item; it is now closed |

**The assertion that mattered most, and it passes.** Rebuilding `notes` renumbers its rows, and an
external-content index is keyed by row id. After the migration, `sourdough` still returns `note-1` and
`honey` still returns `note-2`. An index left alone would have answered with the wrong notes — silently,
and with somebody else's words. This is exactly the hazard `NoteColourColumnRemoval`'s own comment is
written about, and it holds on Apple's SQLite.

Room's generated migration for iOS is correct in every particular that matters: `_new_notes` without
`color`, the three indices re-created, `notes_fts` dropped and re-created and repopulated by `rowid`,
`foreignKeyCheck`, then `onPostMigrate`. It rebuilds `sync_cursor` and `sync_account_state` too.

---

## 2. What adopting room3 would change

Five changes, all mechanical, all measured against 3.1.0-alpha01.

1. **Package and coordinates.** `androidx.room` → `androidx.room3`, and the artifacts are renamed:
   `room3-runtime`, `room3-compiler`. New maven group, so it sits beside 2.x rather than replacing it —
   which is what makes a staged adoption possible.
2. **`@ConstructedBy` is required**, with a hand-written companion beside the `@Database`:
   ```kotlin
   @ConstructedBy(PrimeNotesDatabaseConstructor::class)
   @Database(/* … */)
   abstract class PrimeNotesDatabase : RoomDatabase() { /* … */ }

   expect object PrimeNotesDatabaseConstructor : RoomDatabaseConstructor<PrimeNotesDatabase> {
       override fun initialize(): PrimeNotesDatabase
   }
   ```
3. **`AutoMigrationSpec.onPostMigrate` becomes `suspend`.** `NoteColourColumnRemoval` overrides it as a
   plain function, so that one line changes.
4. **`Migration.migrate` becomes `suspend` and takes a `SQLiteConnection`** instead of a
   `SupportSQLiteDatabase`. The `object : Migration(4, 5)` spelling **survives** — the constructor still
   takes `startVersion` and `endVersion`. The five hand-written migrations each change their `migrate`
   body from `db.execSQL(…)` to `connection.execSQL(…)`; the SQL is identical.
5. **KSP configuration names** are `kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`. Note that
   `kspAndroidMain` is the *task*, not the configuration, and adding to it fails with
   "Configuration not found". The iOS ones exist only after `kotlin { }` has run, so the `dependencies { }`
   block must come after it. And the processor is `room3-compiler`, not `room3-runtime` — the latter ships
   no annotation processor and KSP reports "No providers found in processor classpath".

**The Gradle extension is `room3 { }`, not `room { }`,** and it takes only `schemaDirectory(…)`. There is
no `validateMigration` on it (that flag lives on the 2.x extension), so the probe's build sets nothing
else.

**`@DeleteColumn` is unchanged** — a plain annotation on the spec class, exactly as the app already
writes it. It is *not* nested under `.Entries`; that spelling appears in Room's own suggestion text and
does not compile. Worth recording because the error message misleads on precisely this point.

---

## 3. What did *not* change

- **FTS4 stays.** No migration to FTS5 is needed. FTS5 was tried in the probe and reverted, for two
  measured reasons: it makes the entity disagree with the committed FTS4 v6 schema, and FTS5 availability
  is not uniform even between SQLite builds on one machine — this workstation's `sqlite3.exe` reports
  `no such module: FTS5` while the bundled driver has both. Apple's SQLite has the FTS4 external-content
  support the schema needs; `PlatformSupportTest` asserts that on every run.
- **`docid` stays.** The DAO join `notes.rowid = notes_fts.docid` is correct for FTS4. (FTS5 has no `docid`
  and would need `rowid` — the one place the two would diverge.)
- **The SQL in the migrations is unchanged.** Same statements, same order.
- **`prime_notes.db` at version 7** is unchanged, so an upgrading install keeps its notes.

---

## 4. Findings worth keeping, beyond this phase

- **FTS4 rejects a parameterised `MATCH` outright** — a `?` in the MATCH position raises
  `android.database.SQLException`. This is SQLite, not Room, and identical on every platform, so
  `FtsMatchQuery`'s inlining is *required* rather than merely convenient.
- **`getText` on a NULL column returns `""`, not `null`.** Reading a nullable column needs `isNull` to
  distinguish "no formatting" from "an empty document".
- **`sqlite-bundled` publishes per-platform modules.** The JVM one is `sqlite-bundled-jvm`, a *different*
  artifact, and it has no 2.8.0-alpha01 variant. Declaring it in `commonTest` rather than the host test
  source set fails the iOS test compile with "no matching variant", because a `commonTest` dependency is
  inherited by every target.
- **The AGP KMP host test source set is `androidHostTest`**, and the task is `testAndroidHostTest` —
  not `androidUnitTest`, which this plan previously said. `:app` keeps `testDebugUnitTest`; those are
  different names for different modules, and conflating them sends the next person looking in the wrong
  place.

---

## 5. Corrections this investigation makes to earlier conclusions

Stated plainly, because two of them were wrong and they are recorded in the plan file.

- **"Room 2.8.5 cannot target iOS, ever" — wrong as stated, right in conclusion.** Room 2.8.5 *does*
  publish iOS klibs and *does* run its KSP processor for iOS: that is why Phase 3 could put the entities
  and DAOs in `commonMain` at all. What it cannot do is *own a database* — its KMP artifacts contain 58
  classes, all of them annotations, with no `RoomDatabase`, no `Builder`, no `addMigrations`, no
  `Migration` and no `AutoMigrationSpec`. The Phase 0 spike saw a 404 on `sqlite-framework` and generalised
  it; the cause was that the spike pinned **sqlite 2.7.1**, whose `sqlite-framework` has no iOS artifact.
  2.6.2 and 2.8.0-alpha01 both do.
- **"The room3 alpha is unnecessary because 2.8.5 is multiplatform" — wrong.** It is needed for exactly
  the builder and the migrations, which is what this phase set out to prove.
- **"The FTS4 statement fails on iOS" — wrong, and it was my own test harness.** Every test failed at its
  first `execSQL` for two runs because the iOS actual opened the database in `NSDocumentDirectory` with
  `create = false`, which a bare unit-test bundle may not have. The third run failed one test because the
  file name was `phase35-<pid>.db` — constant for the process — so the second test reopened the first
  test's already-migrated file. Neither was a Room, SQLite or platform problem. Both are now fixed and
  recorded in the code, and `PlatformSupportTest` exists so that a platform difference is *stated* by a
  test rather than inferred from a stack trace.

---

## 6. Recommendation

**Adopt `androidx.room3` 3.1.0-alpha01 for the iOS database**, and leave Android's `androidx.room` 2.8.5
exactly as it is.

That is not a compromise — it is the good outcome. Because room3 lives in a new package and a new maven
group, both Room versions can sit in the same project. `:shared` takes room3 and owns the schema, the
DAOs and the iOS database; `:app` keeps 2.8.5 and its Android database, its five migrations and its
`NoteColourMigrationTest` unchanged. Nothing production has to move until someone chooses to move it, and
the iOS store can be built and shipped against a real schema while Android continues to ship as it does
today.

The alpha remains a genuine risk, and it is the top one: a room3 bump is a tested change, not a routine
one. But it is now a *known* risk with a measured surface — five changes, all mechanical, all listed above
— rather than the open question it was when Phase 3 closed.
