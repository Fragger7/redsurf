package com.redsurf.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
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

/** Crest display face: hero titles, ruler, channel numbers, caps labels (RESEARCH.md "A. Crest"). */
val BarlowCondensed = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))

/** Crest text face: titles, meta, body. Condensed widths fit ~20% more of a long channel or
 * programme name in the same row than the platform default. */
val BarlowSemiCondensed = FontFamily(
    Font(R.font.barlow_semicondensed_regular, FontWeight.Normal),
    Font(R.font.barlow_semicondensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semicondensed_semibold, FontWeight.SemiBold),
)

/** Tabular figures, so clocks, channel numbers and times don't jitter as digits change. */
private const val Tnum = "tnum"

/** tv-material3's type scale with Crest's text face swapped in, so any ad-hoc
 * `MaterialTheme.typography.*` use still lands in the theme. Sizes are untouched. */
internal val CrestTypography: Typography = Typography().let { t ->
    fun TextStyle.f() = copy(fontFamily = BarlowSemiCondensed)
    Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

/**
 * The handful of text roles the app actually uses, named by job rather than by Material tier so
 * every screen picks the same one for the same job.
 *
 * **Crest, 2026-10-26** (RESEARCH.md "A. Crest"): every role now sets its family and sp
 * explicitly - Barlow Condensed SemiBold for display jobs (hero, ruler, numbers, caps labels),
 * Barlow Semi Condensed for reading jobs - because the theme is a token set: a later theme swaps
 * this object, not the screens. Sizes follow the spec (titles 15, meta 14, secondary 12, caps
 * labels 12 with +1 tracking), measured on this device's 960x540dp canvas.
 */
object RedSurfType {
    /** "Categories", the selected group's name, "Channel Preview". */
    val sectionTitle: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

    /** Channel / group names in list rows. */
    val rowTitle: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowSemiCondensed, fontWeight = FontWeight.Medium, fontSize = 15.sp)

    /** Programme line under a channel, counts, timestamps. */
    val rowSecondary: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowSemiCondensed, fontWeight = FontWeight.Normal, fontSize = 12.sp, fontFeatureSettings = Tnum)

    /** Nav pill labels, buttons. */
    val label: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowSemiCondensed, fontWeight = FontWeight.Medium, fontSize = 15.sp)

    /** The one large title on a screen (preview card's channel name, placeholder titles). */
    val heroTitle: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)

    /** Tiny uppercase tags: the LIVE badge, caps labels. */
    val badge: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp)

    /** The "RedSurf" wordmark specifically - nav strip, loading screen, About. Not a general text
     * role; nothing else in the app should reach for this. */
    val wordmark: TextStyle
        @Composable get() = TextStyle(fontFamily = PoppinsSemiBold, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)

    // --- Guide grid roles (EPG_GRID_REDESIGN.md G.2) ---

    /** A programme title inside a grid cell. */
    val gridCell: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowSemiCondensed, fontWeight = FontWeight.Medium, fontSize = 14.sp)

    /** The channel name in a grid row's label block. */
    val gridChannel: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowSemiCondensed, fontWeight = FontWeight.Medium, fontSize = 14.sp)

    /** Channel numbers, the header date, the collapsed hero bar's hint and clock. */
    val gridMeta: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFeatureSettings = Tnum)

    /** On-the-hour labels in the time ruler ("3:00 PM"). */
    val gridHeaderHour: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFeatureSettings = Tnum)

    /** Half-hour labels in the time ruler ("3:30") - visibly secondary to the hour marks. */
    val gridHeaderHalf: TextStyle
        @Composable get() = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFeatureSettings = Tnum)
}
