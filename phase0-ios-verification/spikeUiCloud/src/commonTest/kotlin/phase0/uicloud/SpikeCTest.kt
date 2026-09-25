package phase0.uicloud

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Spike C. Proves the *existing* backend can be reached from a Ktor Darwin client on iOS.
 *
 * Credentials come from the environment (GitHub Secrets in CI). When they are absent the
 * credential-dependent test SKIPS and says so, rather than pretending to have verified
 * anything. Nothing here creates a backend, changes the schema, or touches RLS.
 *
 * The URL and the publishable ("anon") key are NOT secrets: they ship inside every Android
 * APK, and row-level security — not the key — is the boundary. That lets one test exercise
 * real HTTPS on the simulator with no credentials at all.
 */
class SpikeCTest {

    /**
     * Needs NO credentials, so it always runs. It proves supabase-kt constructs on iOS with
     * the Ktor Darwin engine actually linked in — a linkage problem would surface here.
     *
     * The key is a structurally valid but unsigned JWT, because the client decodes the key's
     * payload to decide how to send it.
     */
    @Test
    fun supabaseClientInitializesOnIos() {
        val placeholderKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJpc3MiOiJzdXBhYmFzZSIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjIwMDAwMDAwMDB9." +
            "c2lnbmF0dXJl"

        val client = phase0CloudClient("https://phase0-placeholder.supabase.co", placeholderKey)
        assertFalse(phase0HasSession(client), "a fresh client should start with no session")
        println("PHASE0 SPIKE C: Supabase client constructed on iOS with the Darwin engine OK")
    }

    /**
     * Needs no credentials, but does need a network round trip. It proves the Ktor Darwin
     * engine performs real HTTPS against the live backend on the simulator — not merely that
     * the client constructs. Two outcomes must both hold: the health function answers, and an
     * anonymous read is refused by RLS. Being refused is the safe result; rows would be the alarm.
     */
    @Test
    fun backendReachableOverKtorDarwinOnIos() = runTest {
        val url = platformEnv("PHASE0_SUPABASE_URL")
        val key = platformEnv("PHASE0_SUPABASE_ANON_KEY")
        if (url.isNullOrBlank() || key.isNullOrBlank()) {
            println("PHASE0 SPIKE C: SKIPPED — no URL/key supplied. HTTPS round trip NOT VERIFIED.")
            return@runTest
        }

        val client = phase0CloudClient(url, key)

        val health = runCatching { phase0HealthBody(client) }
        println(
            "PHASE0 SPIKE C: app_health over HTTPS on iOS -> " +
                health.fold({ it.trim() }, { "FAILED (${it::class.simpleName}: ${it.message})" })
        )
        assertTrue(
            health.getOrNull()?.contains("\"ok\"") == true,
            "the backend health function did not answer over Ktor Darwin on iOS"
        )

        val anonRead = runCatching { phase0ProbeRead(client) }
        println(
            "PHASE0 SPIKE C: anonymous PostgREST read over HTTPS on iOS -> " +
                anonRead.fold(
                    { "$it row(s) returned" },
                    { "${it::class.simpleName}: ${it.message}" }
                )
        )
        assertTrue(
            anonRead.isFailure,
            "an anonymous read returned rows — the RLS boundary did not hold"
        )
    }

    @Test
    fun signInPostgrestReadAndSessionRestore() = runTest {
        val url = platformEnv("PHASE0_SUPABASE_URL")
        val key = platformEnv("PHASE0_SUPABASE_ANON_KEY")
        val email = platformEnv("PHASE0_TEST_EMAIL")
        val password = platformEnv("PHASE0_TEST_PASSWORD")

        if (url.isNullOrBlank() || key.isNullOrBlank() ||
            email.isNullOrBlank() || password.isNullOrBlank()
        ) {
            println("PHASE0 SPIKE C: SKIPPED — credentials were not supplied. Result: NOT VERIFIED.")
            return@runTest
        }

        val client = phase0CloudClient(url, key)
        phase0SignIn(client, email, password)
        assertTrue(phase0HasSession(client), "sign-in produced no session on iOS")
        println("PHASE0 SPIKE C: sign-in OK")

        // A read the publishable key may attempt. Being denied is the safe outcome and is
        // reported rather than treated as a failure — RLS holding is the point.
        val visible = runCatching { phase0ProbeRead(client) }
        println(
            "PHASE0 SPIKE C: notes visible to the publishable key = " +
                visible.fold({ "$it row(s)" }, { "denied (${it.message}) — RLS holding" })
        )

        // "Relaunch": a second client over the same session store.
        val relaunched = phase0CloudClient(url, key)
        assertTrue(phase0HasSession(relaunched), "the session did not survive a fresh client")
        println("PHASE0 SPIKE C: session restored on a fresh client OK")
    }
}
