package com.primenotes.bd.domain.settings

/**
 * Which theme the app draws in, as the person using it chose.
 *
 * [SYSTEM] is the default because following the phone is what someone expects from an app they
 * have never configured — and because it is what this app did before the choice existed, so
 * nobody who never opens the setting sees anything change under them.
 */
enum class ThemeMode {

    SYSTEM,
    LIGHT,
    DARK;

    /**
     * Whether the app should draw dark, given what the phone says.
     *
     * The one rule this phase owns, kept pure and here rather than in the theme, because
     * "dark" has to mean the same thing everywhere: the colour scheme, and a note's colour
     * marker. A forced theme that moved the surfaces but not the markers would be a bug that
     * only shows up on somebody else's phone.
     */
    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /**
         * The mode [name] means, or [SYSTEM] for anything this version does not recognise.
         *
         * A value written by a newer build must never make the app fail to open — the same
         * stance as an unknown colour key reading as no colour, or an unknown sync status
         * reading as pending.
         */
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { mode -> mode.name == name } ?: SYSTEM
    }
}
