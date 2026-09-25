package com.primenotes.bd.domain.search

import kotlinx.coroutines.flow.Flow

/**
 * What has been searched for, as everything above the disk sees it.
 *
 * An interface for the same reason [com.primenotes.bd.domain.settings.SettingsStore] is one: the
 * screen that reads and changes the history can be tested without a file, and the one part that has
 * to be Android's own storage stays in a single implementation.
 *
 * It answers with the **whole list**, newest first, rather than offering an "add" of its own. What
 * adding means — trimming, deduplicating, capping — is [withRecentSearch]'s to say, so a second
 * implementation (or a fake) cannot quieten those rules by having its own idea of them.
 */
interface RecentSearchStore {

    /** The searches, newest first, and every change to them after that. */
    val recentSearches: Flow<List<String>>

    suspend fun setRecentSearches(queries: List<String>)
}
