package phase0.uicloud

/** The only platform difference this module needs: reading credentials from the environment. */
expect fun platformEnv(name: String): String?
