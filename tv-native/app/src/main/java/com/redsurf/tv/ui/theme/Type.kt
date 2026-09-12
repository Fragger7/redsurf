package com.redsurf.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme

/**
 * The handful of text roles the app actually uses, named by job rather than by Material tier so
 * every screen picks the same one for the same job. All derive from tv-material3's typography
 * (already sized for ~3 m viewing) - only weight is adjusted, never sp; see Theme.kt.
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
}
