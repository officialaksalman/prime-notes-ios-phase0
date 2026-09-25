# :shared — iOS verification

`.github/workflows/shared-ios.yml` compiles `:shared` for `iosSimulatorArm64`, links the
`Shared` framework, and runs the module's `commonTest` suite on the iOS simulator. It is the
only place `:shared`'s iOS half is ever built: the project is developed on Windows, where
Kotlin/Native cannot build Apple targets.

## What it needs

- The secret `PRIME_NOTES_READ_PAT`: a token with read access to `Prime-Notes` alone. It lets
  this public workflow clone the private repository, so a run describes the code that is
  committed rather than a drifting copy.
- The Prime-Notes ref to verify to be pushed. A run can only see what is on the remote, and
  nothing watches Prime-Notes, so it is dispatched by hand.

## Running it

Actions → "shared — iOS" → Run workflow, with `ref` = the ref to verify. It also runs on a push
to `main` here.

## Reading the result

A run cannot be inspected from outside, so it commits `RESULTS.md` back to this repository and
uploads its logs as the `shared-ios-logs` artifact. `RESULTS.md` carries the verdict, the test
suite's counts, and the tail of each step's log.
