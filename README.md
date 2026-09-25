# Phase 0 — iOS / KMP verification sandbox

A throwaway repository for verifying the Apple-side toolchain before any migration is
planned against the real app. **It contains no Prime Notes production code.**

## What this answers

Whether the proposed migration architecture is actually viable on iOS:

| Spike | Question |
|---|---|
| **A** | Can Room KMP create, populate and query the external-content **FTS4** index, and run a v1→v2 migration, on an iOS simulator? |
| **B** | Does one **Compose Multiplatform** screen build for iOS *and* Android from the same shared source? |
| **C** | Does **supabase-kt over Ktor Darwin** reach the live backend from an iOS simulator — real HTTPS round trip and RLS boundary — and, with credentials, sign in and restore a session? |

Spike A also probes the bundled SQLite build directly for FTS4 and FTS5 support, because
that is the question underneath the Room one.

## Results

Every run on `macos-15` commits a [`RESULTS.md`](./RESULTS.md) back to this repository.
That exists because the agent driving these runs has no GitHub token or CLI, so the run
cannot be inspected from outside — the results are published by the workflow itself.

## Layout

```
.github/workflows/phase0-verification.yml   the only workflow; runs on push to main
phase0-ios-verification/                    a standalone Gradle build (its own settings)
  gradle/libs.versions.toml                 pinned candidate versions
  src/commonMain/…/Data.kt                  Room entities + FTS4 schema + migration
  src/commonMain/…/Platform.kt              the only per-platform database API
  src/commonMain/…/SpikeUi.kt               the shared Compose Multiplatform screen
  src/commonMain/…/CloudSpike.kt            Supabase client + sign-in + read
  src/commonTest/…/SpikeATest.kt            FTS4 / Room / migration tests
  src/commonTest/…/SpikeCTest.kt            Supabase tests
  src/androidMain|iosMain/…                 platform actuals
```

## Notes

* The CI matrix runs three Kotlin legs — **2.2.20, 2.3.20 and 2.4.20** — so the
  compatibility matrix is measured rather than guessed. Kotlin **≥ 2.3.20 is required**:
  the `room3` and `material3` iOS klibs are published with ABI 2.3.0 and a 2.2.x compiler
  refuses to read them.
* The backend URL and the publishable ("anon") key are supplied to Spike C as plain
  environment values in the workflow. They are **not secrets** — both ship inside every
  Android APK — and they let Spike C make a real HTTPS round trip with **no credentials**:
  `app_health` must answer, and an anonymous Postgrest read must be refused by RLS.
* Only the full **sign-in / session-restore** flow needs credentials, and it **skips**
  (printing `NOT VERIFIED`) unless `PHASE0_TEST_EMAIL` and `PHASE0_TEST_PASSWORD` secrets
  exist. It never pretends to have verified anything.
* Never add a service-role key here. The publishable/anon key is not a secret; RLS is the boundary.
