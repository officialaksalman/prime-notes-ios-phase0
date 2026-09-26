package com.primenotes.bd.data.cloud

/**
 * The cloud this build is pointed at.
 *
 * Configuration is deliberately allowed to be absent. A checkout with no keys is the
 * normal state of the repository, and it must build, run, and report itself as
 * unconfigured rather than failing — so anything unusable collapses to an empty
 * config instead of a half-built client.
 *
 * The [publishableKey] is not a secret: it travels in every request the app makes and
 * ships in every Supabase client. Row-level security is what protects the data. The
 * service-role key must never reach this class.
 */
data class SupabaseConfig(
    val url: String,
    val publishableKey: String
) {

    val isConfigured: Boolean get() = url.isNotEmpty() && publishableKey.isNotEmpty()

    /** The project host, for display. Never includes the key. */
    val projectLabel: String get() = url.removePrefix(HTTPS).substringBefore('/')

    companion object {
        private const val HTTPS = "https://"

        /**
         * Normalises and validates what the build handed over.
         *
         * Anything that is not a usable https endpoint — blank, whitespace, a bare
         * host, an `http://` URL or a relative path — yields an unconfigured result
         * rather than an error, because none of those are worth failing a build or a
         * launch over.
         */
        fun of(rawUrl: String, rawKey: String): SupabaseConfig {
            val url = rawUrl.trim().trimEnd('/')
            val key = rawKey.trim()
            val usable = url.startsWith(HTTPS) && url.length > HTTPS.length && key.isNotEmpty()

            return if (usable) SupabaseConfig(url, key) else SupabaseConfig("", "")
        }
    }
}
