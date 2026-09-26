# `shared/schemas` — the iOS database's schema history

This is room3's schema output for `com.primenotes.bd.data.local.iosdb.IosDatabase`, configured by

```kotlin
room3 { schemaDirectory("$projectDir/schemas") }
```

in `shared/build.gradle.kts`. It is deliberately **not** `app/schemas`. Room 2.8.5's committed schemas
in `app/schemas/com.primenotes.bd.data.local.PrimeNotesDatabase/` are production: they are the contract
every Android migration is validated against on open, and nothing in this build may write to them.

## `7.json`

Generated. Written by the iOS KSP pass from the declarations in
`shared/src/iosMain/kotlin/com/primenotes/bd/data/local/iosdb/IosEntities.kt`.

`IosSchemaParityTest` compares it field by field against Android's committed `7.json` — table names,
columns with affinity / nullability / default, indices with uniqueness and `WHERE`, foreign keys with
**both** `onDelete` and `onUpdate`, and the FTS configuration. It is currently identical, and the test
fails the build the moment that stops being true. That is what makes the deliberate duplication of the
entities in `IosEntities.kt` safe rather than merely tolerated.

## `6.json`

**Seeded, not generated** — a copy of `app/schemas/com.primenotes.bd.data.local.PrimeNotesDatabase/6.json`.

It is here because `@AutoMigration(from = 6, to = 7, …)` needs the schema it starts from, and room3
generates the 6 → 7 migration by diffing `6.json` against `7.json`. Without it the iOS KSP pass fails:

```
Schema '6.json' required for migration was not found at the schema out folder
```

Copying Android's v6 is correct rather than a shortcut, and the reason is specific: at the moment it was
copied, the two databases' v7 schemas were verified **identical** field for field. Android's v6 is
therefore the exact predecessor of the iOS v7, which is the only thing the auto-migration needs it to
be. Had the v7s differed, the seeded v6 would have baked that difference into the generated migration.

Note that the `identityHash` in this file is Android's, and it will not match the hash room3 would
generate for iOS v6 — the hash covers entity class names, and the iOS copies are deliberately named
`IosNoteEntity` and so on. The hash is not part of the parity test, precisely because the two databases
are meant to describe the same shape under different class names.

## If a future version changes the schema

1. Change the entity in `IosEntities.kt`, and the same change in the `androidx.room` entity in
   `shared/src/commonMain/…/data/local/entity/` — both, or `IosSchemaParityTest` fails.
2. Bump `version` on both `IosDatabase` and `:app`'s `PrimeNotesDatabase`.
3. Add a migration on both sides, with the same SQL.
4. Let both KSP passes write their own `N.json`. Only a *starting* schema for an auto-migration is ever
   copied across, and only after verifying the two v(N) schemas match.
