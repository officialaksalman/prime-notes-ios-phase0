package com.primenotes.bd.data.cloud

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Builds the cloud client, or null when this build has no cloud configured.
 *
 * Postgrest answers the connection check; Auth carries accounts; Functions reaches the one
 * server-side job the app cannot do for itself — mailing a password-reset code to the address in a
 * profile, which only something holding the service key may read. All three come from the same BOM,
 * so they cannot drift apart. Realtime is still absent because nothing needs it — and no plugin can
 * make a request the app has not asked for.
 *
 * Auth needs no extra configuration here: with no OAuth and no magic links there are no deeplinks to
 * handle, and the plugin's default session storage is what keeps a signed-in user signed in. The
 * password reset is a code typed into the app precisely so that it needs no deeplink either.
 */
fun createCloudClient(config: SupabaseConfig): SupabaseClient? {
    if (!config.isConfigured) return null

    return createSupabaseClient(
        supabaseUrl = config.url,
        supabaseKey = config.publishableKey
    ) {
        install(Postgrest) {
            // Postgrest gets a serializer of its own rather than the client-wide default,
            // so the auth plugin's requests are left exactly as the library builds them.
            //
            // Nulls have to be *written out*, not left out. Moving a note out of a folder
            // is `folder_id: null`, and a field the encoder skipped would leave that note
            // in its old folder on every other device — the one kind of mistake sync must
            // not make, because it would look like it had worked.
            serializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    explicitNulls = true
                }
            )
        }
        install(Auth)
        install(Functions)
    }
}

/**
 * Asks the real project the two questions that matter.
 *
 * The first call proves the project exists and accepts this key. The second proves
 * the security boundary: a client with only the publishable key — which is every
 * client — must not be able to read a single note.
 */
class SupabaseCloudProbe(private val client: SupabaseClient) : CloudProbe {

    override suspend fun probe(): CloudProbeResult {
        // Reachable? If not, why not — that is the whole answer.
        askHealth()?.let { failure -> return failure }

        // Reachable, so the only question left is whether the boundary holds.
        return countVisibleNotes()
    }

    /**
     * Null when `app_health()` answered; otherwise the reason it did not.
     */
    private suspend fun askHealth(): CloudProbeResult.Unreachable? =
        try {
            client.postgrest.rpc("app_health").decodeAs<JsonObject>()
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rest: RestException) {
            if (rest.statusCode == UNAUTHORIZED || rest.statusCode == FORBIDDEN) {
                CloudProbeResult.Unreachable("the publishable key was rejected")
            } else {
                CloudProbeResult.Unreachable("the project answered ${rest.statusCode}")
            }
        } catch (error: Exception) {
            CloudProbeResult.Unreachable(error.toCloudReason())
        }

    /**
     * A refusal here is a pass, not a failure: before sign-in, being denied is exactly
     * right. Rows coming back is the one outcome worth alarming about.
     */
    private suspend fun countVisibleNotes(): CloudProbeResult =
        try {
            val rows = client.postgrest
                .from(NOTES_TABLE)
                .select(Columns.list("id")) { limit(1) }
                .decodeList<JsonObject>()

            CloudProbeResult.Reachable(notesVisible = rows.size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rest: RestException) {
            if (rest.statusCode == UNAUTHORIZED || rest.statusCode == FORBIDDEN) {
                CloudProbeResult.Denied
            } else {
                CloudProbeResult.Unreachable("the notes probe answered ${rest.statusCode}")
            }
        } catch (error: Exception) {
            CloudProbeResult.Unreachable(error.toCloudReason())
        }

    private companion object {
        const val NOTES_TABLE = "notes"
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
    }
}
