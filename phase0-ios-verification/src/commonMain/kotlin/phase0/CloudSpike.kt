package phase0

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonObject

/**
 * Spike C. Uses the *existing* Prime Notes backend and the *existing* client libraries —
 * no second backend, no schema change, no RLS change.
 *
 * This mirrors data/cloud/SupabaseCloudProbe.kt's client construction, with one difference:
 * the HTTP engine is supplied by the platform (OkHttp on Android, Darwin on iOS) via the
 * Ktor engine that is on each platform's classpath.
 */
fun phase0CloudClient(supabaseUrl: String, publishableKey: String): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = publishableKey
    ) {
        install(Postgrest)
        install(Auth)
    }

suspend fun phase0SignIn(client: SupabaseClient, email: String, password: String) {
    client.auth.signInWith(Email) {
        this.email = email
        this.password = password
    }
}

/**
 * A read the publishable key is allowed to make. Being *denied* by row-level security is
 * not a failure here — it proves the boundary holds. Returning rows would be the alarm.
 */
suspend fun phase0ProbeRead(client: SupabaseClient): Int =
    client.postgrest
        .from("notes")
        .select(Columns.list("id")) { limit(1) }
        .decodeList<JsonObject>()
        .size

fun phase0HasSession(client: SupabaseClient): Boolean = client.auth.currentSessionOrNull() != null
