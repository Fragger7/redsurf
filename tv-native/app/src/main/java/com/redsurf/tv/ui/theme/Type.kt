package com.redsurf.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import com.redsurf.tv.R

/**
 * The wordmark typeface (branding, decided 2026-09-16, weight corrected 2026-09-16 - see
 * AGENTS.md's Branding entry): Poppins SemiBold (600), picked by matching the concept art's
 * letterforms against several real Google Fonts at high resolution - its circular, geometric
 * bowls (the "e"/"d"/"S") were the closest structural match, not just "a bold font." Weight
 * corrected from an initial Black (900) pick, which the user correctly called too heavy/fat once
 * seen live - Black and even Bold choke the bowls' open counters; SemiBold is the closest match
 * to the reference's actual stroke weight, confirmed side-by-side against a high-res crop of the
 * concept art. Deliberately scoped to the wordmark only - every other UI role below stays on
 * tv-material3's own default typography, chosen for TV-viewing-distance readability, not brand
 * expression.
 */
val PoppinsSemiBold = FontFamily(Font(R.font.poppins_semibold, FontWeight.SemiBold))

/**
 * The handful of text roles the app actually uses, named by job rather than by Material tier so
 * every screen picks the same one for the same job. All derive from tv-material3's typography
 * (already sized for ~3 m viewing) - only weight is adjusted, never sp; see Theme.kt.
 *
 * **One deliberate exception (EPG_GRID_REDESIGN.md G.2):** the `grid*` roles below set explicit
 * sp. The list-row rule above was written for lists; a dense multi-row timeline grid is a
 * different reading task (scan many short labels, not read one row), and TiviMate's own grid
 * runs a step smaller than its list rows for exactly that reason. Scoped to the grid roles only.
 *
 * Sizes were chosen against references/streamvault/LiveTV.png measured at this device's dp
 * canvas (960x540dp at density 2): their column headers are ~17sp semibold, row titles ~14sp,
 * secondary lines ~12sp. The earlier pass used headline/title tiers a step or two larger, which
 * is why only two or three rows fit on screen and everything read as oversized.
 */
object RedSurfType {
    /** "Categories", the selected group's name, "Channel Preview". */
    val sectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    /** Channel / group names in list rows. */
    val rowTitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

    /** Programme line under a channel, counts, timestamps. */
    val rowSecondary: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    /** Nav pill labels, buttons. */
    val label: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

    /** The one large title on a screen (preview card's channel name, placeholder titles). */
    val heroTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    /** Tiny uppercase tags: the LIVE badge. */
    val badge: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)

    /** The "RedSurf" wordmark specifically - nav strip, loading screen, About. Not a general text
     * role; nothing else in the app should reach for this. */
    val wordmark: TextStyle
        @Composable get() = TextStyle(fontFamily = PoppinsSemiBold, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)

    // --- Guide grid roles (EPG_GRID_REDESIGN.md G.2) - see the class doc for why these set sp ---

    /** A programme title inside a grid cell. */
    val gridCell: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)

    /** The channel name in a grid row's label block. */
    val gridChannel: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)

    /** Channel numbers, the header date, the collapsed hero bar's hint and clock. */
    val gridMeta: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)

    /** On-the-hour labels in the time ruler ("3:00 PM"). */
    val gridHeaderHour: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)

    /** Half-hour labels in the time ruler ("3:30") - visibly secondary to the hour marks. */
    val gridHeaderHalf: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
}
