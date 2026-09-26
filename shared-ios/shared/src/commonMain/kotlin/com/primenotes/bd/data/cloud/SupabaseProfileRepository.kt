package com.primenotes.bd.data.cloud

import com.primenotes.bd.domain.account.ProfileRepository
import com.primenotes.bd.domain.account.ProfileResult
import com.primenotes.bd.domain.model.AccountProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `public.profiles`, as much of it as the app reads.
 *
 * `ignoreUnknownKeys` is on for this client, so a partial row is safe: the table can grow a column
 * without every build that predates it failing to parse what it would otherwise have understood.
 */
@Serializable
internal data class ProfileRow(
    val id: String,
    val username: String,
    val email: String? = null,
    @SerialName("created_at") val createdAt: Long? = null
)

/**
 * The signed-in account's profile, on the real project.
 *
 * A plain read of the account's own row, which the table's policy restricts to its owner — there is
 * no filter here that could be removed to widen it, because the widening is refused by the database
 * and not by this class. Nothing is written: the profile is created with the account by a trigger,
 * and an account does not edit it from this app.
 *
 * A device with no network answers [ProfileResult.Failed], and the screen keeps showing what the
 * session already knows. That is the honest reading of a read that did not happen.
 */
class SupabaseProfileRepository(private val client: SupabaseClient) : ProfileRepository {

    override suspend fun profile(): ProfileResult =
        try {
            val userId = client.auth.currentUserOrNull()?.id
                ?: return ProfileResult.SignedOut

            val row = client.postgrest
                .from(PROFILES)
                .select(Columns.list("id", "username", "email", "created_at")) {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeSingleOrNull<ProfileRow>()
                ?: return ProfileResult.Failed(NO_PROFILE)

            ProfileResult.Found(
                AccountProfile(
                    id = row.id,
                    username = row.username,
                    email = row.email,
                    createdAt = row.createdAt
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ProfileResult.Failed(error.toCloudReason())
        }

    private companion object {
        const val PROFILES = "profiles"

        /**
         * The account exists and the row was not there. It should be impossible — the account and the
         * profile are written in one transaction — so it is reported rather than papered over.
         */
        const val NO_PROFILE = "the account has no profile"
    }
}

/**
 * The profile when there is no cloud to have one on.
 *
 * A build with no project configured says so, rather than reporting an account it cannot know
 * anything about.
 */
object UnconfiguredProfileRepository : ProfileRepository {

    override suspend fun profile(): ProfileResult = ProfileResult.NotConfigured
}
