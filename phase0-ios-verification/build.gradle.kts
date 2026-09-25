/*
 * Root of the Phase 0 spike build.
 *
 * Intentionally empty: every spike lives in its own module so that one failing
 * (Room, blocked by an upstream klib problem) cannot stop the others being measured.
 *
 *   :spikeRoom     Room KMP + SQLite + FTS4 + v1->v2 migration on the iOS simulator
 *   :spikeUiCloud  Compose Multiplatform + supabase-kt over Ktor Darwin on iOS
 *
 * Versions are pinned in gradle/libs.versions.toml and rewritten per CI matrix leg.
 */
