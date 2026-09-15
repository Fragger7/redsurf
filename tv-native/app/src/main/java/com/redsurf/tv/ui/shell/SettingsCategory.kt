package com.redsurf.tv.ui.shell

/**
 * The nine categories, in TiviMate's order (`docs/plans/SETTINGS.md`) - the taxonomy half of
 * "TiviMate decides what's there and where; StreamVault decides what it looks like." Only
 * Playlists and About have real content today; every other category is entirely grey rows until
 * its own module lands - see [SETTINGS_GREY_ROWS], which must stay in sync with `SETTINGS.md`'s
 * table (a flipped-live row moves out of that map into real composable content, and out of
 * `AGENTS.md`'s Backlog).
 */
enum class SettingsCategory(val label: String) {
    General("General"),
    Playlists("Playlists"),
    Epg("EPG"),
    Appearance("Appearance"),
    Playback("Playback"),
    RemoteControl("Remote control"),
    ParentalControls("Parental controls"),
    Other("Other"),
    About("About"),
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
    SettingsCategory.General to listOf(
        GreyRow("Resume last channel on launch", "Off"),
        GreyRow("Start on", "Live TV"),
        GreyRow("Language", "System"),
    ),
    SettingsCategory.Epg to listOf(
        GreyRow("Sources", "None"),
        GreyRow("Time offset", "0h"),
        GreyRow("Refresh every", "12h"),
    ),
    // "Resolution badge" flipped live (BACKLOG_SWEEP.md #12) - now AppearanceContent's own toggle
    // row in SettingsScreen.kt, not a grey row here.
    SettingsCategory.Appearance to listOf(
        GreyRow("Hide nav strip when idle", "Off"),
        GreyRow("Show full category name on hold", "Off"),
        GreyRow("Accent", "Red"),
    ),
    // "Black screen between zaps" flipped live (BACKLOG_SWEEP.md #11) - now PlaybackContent's own
    // toggle row in SettingsScreen.kt, not a grey row here.
    SettingsCategory.Playback to listOf(
        GreyRow("Auto frame rate", "Off"),
        GreyRow("Buffer", "Default"),
        GreyRow("Preferred audio", "Auto"),
        GreyRow("Preferred subtitles", "Off"),
        GreyRow("Default aspect", "Fit"),
    ),
    SettingsCategory.RemoteControl to listOf(
        GreyRow("Long-press OK", "Context menu"),
        GreyRow("Digit keys", "Channel number entry"),
    ),
    SettingsCategory.ParentalControls to listOf(
        GreyRow("PIN", "Not set"),
        GreyRow("Hidden groups", "None"),
    ),
    SettingsCategory.Other to listOf(
        GreyRow("Backup & restore", "—"),
        GreyRow("Clear watch history", "—"),
        GreyRow("Diagnostics & logs", "—"),
    ),
)

/** Playlists' per-playlist grey rows (`SETTINGS.md`) - shown under every loaded playlist's real
 * Remove action, not once per category. */
val PLAYLIST_GREY_ROWS: List<GreyRow> = listOf(
    GreyRow("Rename", "—"),
    GreyRow("Refresh / re-sync", "—"),
    GreyRow("Content", "Live+VOD"),
)

/** About's one grey row, alongside its live version/update rows. */
val ABOUT_GREY_ROWS: List<GreyRow> = listOf(
    GreyRow("Auto-update", "On"),
)
