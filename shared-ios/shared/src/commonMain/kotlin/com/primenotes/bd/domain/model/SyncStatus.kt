package com.primenotes.bd.domain.model

/**
 * How a locally stored row stands relative to the cloud copy.
 *
 * [wireValue] is a deliberate, stable contract: it is what gets persisted and,
 * from Phase 9, what travels to Supabase. Persisting the constant rather than the
 * enum's ordinal means reordering or renaming entries can never silently
 * reinterpret existing rows.
 */
enum class SyncStatus(val wireValue: String) {
    SYNCED("SYNCED"),
    PENDING("PENDING"),
    FAILED("FAILED");

    companion object {
        /**
         * Unknown values fall back to [PENDING].
         *
         * A row written by a newer app version must never make the database
         * unreadable, and treating it as pending means it will simply be pushed
         * again rather than being lost.
         */
        fun fromWireValue(value: String): SyncStatus =
            entries.firstOrNull { status -> status.wireValue == value } ?: PENDING
    }
}
