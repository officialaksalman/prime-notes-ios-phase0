package phase0.uicloud

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
 * Mirrors data/cloud/SupabaseCloudProbe.kt's client construction; the only difference is
 * that the HTTP engine comes from each platform's Ktor artifact (OkHttp / Darwin).
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
 * A read the publishable key is allowed to attempt. Being *denied* by row-level security is
 * not a failure — it proves the boundary holds. Returning rows would be the alarm.
 */
suspend fun phase0ProbeRead(client: SupabaseClient): Int =
    client.postgrest
        .from("notes")
        .select(Columns.list("id")) { limit(1) }
        .decodeList<JsonObject>()
        .size

fun phase0HasSession(client: SupabaseClient): Boolean = client.auth.currentSessionOrNull() != null
