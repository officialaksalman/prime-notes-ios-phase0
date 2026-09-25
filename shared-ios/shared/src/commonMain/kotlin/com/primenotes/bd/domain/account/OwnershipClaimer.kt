package com.primenotes.bd.domain.account

import com.primenotes.bd.domain.repository.FolderRepository
import com.primenotes.bd.domain.repository.NoteRepository
import com.primenotes.bd.domain.repository.TagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hands the notes someone wrote before they had an account to that account.
 *
 * Phase 2 left `user_id` null for exactly this: rows written on a device belong to
 * nobody until somebody signs in, and then they belong to them.
 *
 * It watches the session rather than the sign-in button, because a session restored
 * at launch is just as much a reason to claim: notes written during an earlier
 * signed-in session may still be unowned.
 *
 * Claiming only ever touches rows with no owner, so an account can never take a note
 * that already belongs to another one.
 */
class OwnershipClaimer(
    private val account: AccountRepository,
    private val notes: NoteRepository,
    private val folders: FolderRepository,
    private val tags: TagRepository
) {

    /** Runs until [scope] is cancelled. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            account.account
                .map { signedIn -> signedIn?.id }
                .distinctUntilChanged()
                .collect { userId -> if (userId != null) claim(userId) }
        }
    }

    /**
     * Claims everything unowned for [userId].
     *
     * A failure is swallowed on purpose: signing in must not break because a local
     * update did not land, nothing is lost by waiting, and the next session simply
     * tries again.
     */
    suspend fun claim(userId: String) {
        try {
            notes.claimUnowned(userId)
            folders.claimUnowned(userId)
            tags.claimUnowned(userId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Deliberately ignored — see above.
        }
    }
}
