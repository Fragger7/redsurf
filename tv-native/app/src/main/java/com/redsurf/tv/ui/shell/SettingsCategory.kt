package com.redsurf.tv.ui.shell

import com.redsurf.tv.R

/**
 * The nine categories, in TiviMate's order (`docs/plans/SETTINGS.md`) - the taxonomy half of
 * "TiviMate decides what's there and where; StreamVault decides what it looks like." Only
 * Playlists and About have real content today; every other category is entirely grey rows until
 * its own module lands - see [SETTINGS_GREY_ROWS], which must stay in sync with `SETTINGS.md`'s
 * table (a flipped-live row moves out of that map into real composable content, and out of
 * `AGENTS.md`'s Backlog).
 *
 * [iconRes] (branding, decided 2026-09-16 - see AGENTS.md's Branding entry) replaces the rail's
 * old initial-letter tiles: six are real vector icons from Phosphor Icons (MIT-licensed),
 * recolored to the brand red; EPG and Remote control are custom (a "7" + guide-grid bars on a
 * blank calendar; the remote glyph hand-extracted from the brand mark itself, since no library
 * has a literal remote icon); About reuses the full mark.
 */
enum class SettingsCategory(val label: String, val iconRes: Int) {
    General("General", R.drawable.ic_settings_general),
    Playlists("Playlists", R.drawable.ic_settings_playlists),
    Epg("EPG", R.drawable.ic_settings_epg),
    Appearance("Appearance", R.drawable.ic_settings_appearance),
    Playback("Playback", R.drawable.ic_settings_playback),
    RemoteControl("Remote control", R.drawable.ic_settings_remote),
    ParentalControls("Parental controls", R.drawable.ic_settings_parental),
    Other("Other", R.drawable.ic_settings_other),
    About("About", R.drawable.ic_settings_about),
}

/**
 * A planned-but-unbuilt row: real name, real planned default value, grey and unfocusable (user
 * decision, `SETTINGS.md` "Grey rows - the rule"). Never wrapped in a focusable `Surface`, so the
 * D-pad skips it entirely - a tester can never land on fiction. Flipping one live means deleting
 * it from here and adding real, focusable content in its place.
 */
data class GreyRow(val label: String, val plannedValue: String)

/** `SETTINGS.md`'s table, verbatim - the visible backlog for every settings-shaped idea. */
val SETTINGS_GREY_ROWS: Map<SettingsCategory, List<GreyRow>> = mapOf(
    // "Resume last channel on launch" flipped live (AGENTS.md backlog, 2026-09-15) - now
    // generalContent's own toggle row in SettingsScreen.kt, not a grey row here.
    SettingsCategory.General to listOf(
        GreyRow("Language", "System"),
    ),
    // "Update EPG now" / "Last updated" / "Sources" / "Refresh every" / "Time offset" are live rows in
    // SettingsScreen.kt's epgContent.
    SettingsCategory.Epg to emptyList(),
    // "Resolution badge" flipped live (BACKLOG_SWEEP.md #12) - now AppearanceContent's own toggle
    // row in SettingsScreen.kt, not a grey row here.
    SettingsCategory.Appearance to listOf(
        GreyRow("Show full category name on hold", "Off"),
        GreyRow("Accent", "Red"),
    ),
    // "Black screen between zaps" flipped live (BACKLOG_SWEEP.md #11) - now PlaybackContent's own
    // toggle row in SettingsScreen.kt, not a grey row here.
    SettingsCategory.Playback to listOf(
    ),
    SettingsCategory.RemoteControl to listOf(
        // "Digit keys" and "Long-press OK" are live - SettingsScreen.kt's remoteControlContent.
    ),
    SettingsCategory.ParentalControls to listOf(
        // "PIN" is live - SettingsScreen.kt's parentalContent; it guards unhiding.
        // "Hidden groups" is live - SettingsScreen.kt's parentalContent ("Hidden categories").
    ),
    SettingsCategory.Other to listOf(
        GreyRow("Backup & restore", "—"),
        // "Diagnostics" is live - SettingsScreen.kt's DiagnosticsRows.
    ),
)
