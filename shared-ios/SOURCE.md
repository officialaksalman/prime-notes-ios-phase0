# `shared-ios/` — a copy of the `:shared` module

This is a **copy** of the `shared` module from the private `Prime-Notes` repository, committed
here so the iOS verification workflow can build it from a public repository. macOS runner minutes
are free on a public repository and billable on the private one, and that is the only reason this
directory exists.

Nothing here is edited in place. A fix belongs upstream in `Prime-Notes`; this is a copy, and the
copy is what drifts if that is forgotten.

## Where this copy came from

- Prime-Notes commit **`d9c18ef`** — *"feat: Introduce TimeSource interface and refactor
  DevicePresence to use it"*
- **plus the uncommitted Phase 2b working tree on top of that commit**, which is in no commit
  yet. The difference is the whole point of the run:
  - `domain/account/PasswordPolicy.kt`, `SecureRandom.kt`, `SecureRandom.android.kt`,
    `SecureRandom.ios.kt` and `PasswordPolicyTest.kt`
  - `domain/backup/NoteArchive.kt`, `ImportPlan.kt`, `ArchiveCodec.kt`, `ArchiveCodec.android.kt`,
    `ArchiveCodec.ios.kt`, and the two tests
  - `shared/build.gradle.kts` — the `Shared` framework binary on both iOS targets

Because the source is a working tree rather than a commit, this copy corresponds to no immutable
revision of Prime-Notes. Once 2b is committed, that commit becomes the honest answer here.

## Re-syncing

From a directory holding a checkout of both repositories:

    cp Prime-Notes/shared/build.gradle.kts      prime-notes-ios-phase0/shared-ios/shared/
    cp -R Prime-Notes/shared/src                prime-notes-ios-phase0/shared-ios/shared/
    cp Prime-Notes/gradle/libs.versions.toml    prime-notes-ios-phase0/shared-ios/gradle/

Then update the commit above in the same commit, so the record moves with the copy.

## Not copied

`shared/build/`, and anything else the production repository does not commit. `.gradle/` likewise.

## A caveat worth stating plainly

This directory is in a **public** repository. The module it mirrors is not. Whatever is in
`shared/src` above is published, so the sync procedure should be used deliberately rather than on
a schedule.

## Revisions

- **Second sync.** The first iOS run of this copy compiled and linked, and then failed to compile
  the tests for Kotlin/Native: three backtick-quoted test names contained a comma, which Native
  rejects, and two call sites used `String.toByteArray()`, which is JVM-only. Both were fixed
  upstream in Prime-Notes and re-copied. The Android host compilation had accepted all five
  without complaint, which is the whole reason this directory exists.
