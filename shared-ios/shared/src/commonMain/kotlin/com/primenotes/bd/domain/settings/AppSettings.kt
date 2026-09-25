package com.primenotes.bd.domain.settings

import com.primenotes.bd.domain.model.NoteSortOrder
import kotlinx.coroutines.flow.Flow

/**
 * What the app remembers about how it should look, and in what order it should show notes.
 *
 * Every one of these is a choice rather than content: none of them is anybody's writing, and losing
 * all of them would be an inconvenience rather than a loss. That is why they live together in one
 * small file, and why a file that cannot be read is replaced with these defaults instead of taking
 * the app down with it.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    /**
     * Whether colours come from the phone's wallpaper instead of the Prime Notes palette.
     *
     * Off by default, which is the decision phase 1 recorded: the app has an identity of its
     * own, and following the wallpaper is something to ask for. It is also the only honest
     * default on a device older than Android 12, where the choice cannot exist at all.
     */
    val dynamicColor: Boolean = false,

    val sortOrder: NoteSortOrder = NoteSortOrder.LAST_MODIFIED,

    /**
     * How the notes list draws them.
     *
     * A preference for the same reason the sort order is one: somebody picks the way they like to
     * see their notes once, and expects it to still be that way the next time they open the app.
     */
    val viewMode: NoteViewMode = NoteViewMode.LIST,

    /**
     * The style a note begins in when it is created. See [DefaultNoteStyle].
     *
     * [DefaultNoteStyle.BODY] is the default and is exactly the app's behaviour before the choice
     * existed: a new note is plain text with no formatting document.
     */
    val defaultNoteStyle: DefaultNoteStyle = DefaultNoteStyle.BODY,

    /**
     * Whether the editor saves as it is typed in.
     *
     * On by default, and on is what the editor has always done. Turning it off does not mean a note
     * is never saved — it is still written when the editor is left and when the app goes away — it
     * means nothing is written *while* typing. See `NoteEditorViewModel`.
     */
    val autosave: Boolean = true,

    /**
     * Whether the app asks who is holding the device before it shows any notes.
     *
     * Off by default: a lock nobody asked for is an obstacle, and the app's own promise is that it
     * works without ceremony. It is a curtain over the app rather than a safe — the notes are plain
     * rows on the disk and in the cloud either way, and the About screen says so.
     */
    val appLock: Boolean = false,

    /**
     * Whether Prime Notes may tell you how a sync got on.
     *
     * Off by default, like everything this app does not need: a notification nobody asked for is an
     * interruption, and these are about a thing that already worked without being announced.
     *
     * It is a **preference**, and the system's own permission is a separate fact. The two are combined
     * at the moment something would be posted, so a switch that is on while the system refuses to
     * deliver says so rather than pretending — see `Notifier.allowed()`.
     */
    val notifications: Boolean = false
) {
    companion object {
        val Default = AppSettings()
    }
}

/**
 * The app's settings, as everything above the disk sees them.
 *
 * An interface so the screens that read and change them can be tested without a file, and so
 * the one part that has to be an Android DataStore stays in a single implementation — in the
 * same shape as [com.primenotes.bd.domain.backup.NoteBackup].
 */
interface SettingsStore {

    /** The stored settings, and every change to them after that. */
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setSortOrder(sortOrder: NoteSortOrder)

    suspend fun setViewMode(viewMode: NoteViewMode)

    suspend fun setDefaultNoteStyle(style: DefaultNoteStyle)

    suspend fun setAutosave(enabled: Boolean)

    suspend fun setAppLock(enabled: Boolean)

    suspend fun setNotifications(enabled: Boolean)
}
