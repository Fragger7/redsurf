package com.redsurf.tv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Glow

/**
 * The focus model. Every focusable Surface(onClick=...) in the app must configure all of these -
 * an element with no visible focus state is a bug, not a style choice (UI_SPEC.md #2).
 *
 * **Crest, 2026-10-26** (`docs/vision/design-language/RESEARCH.md` "A. Crest"), replacing the
 * ring + glow + 1.04 scale model:
 * - Rows (lists, settings, pickers): the focused row becomes a full-width solid red bar. No ring,
 *   no glow, no scale - the fill alone is the cursor, and it is the cheapest one to draw (no
 *   shadow pass, no re-layout of a grown node). Selected-but-unfocused rows sit one step up.
 * - Pills (nav, chips, buttons): the current one is filled red; the focused one carries a white
 *   2dp outline, so "where I am" and "what's active" read as two different signals.
 * - Shapes are square everywhere content lives; only [pillShape] is round.
 * The function names are unchanged so call sites stay put; a later theme swaps the bodies.
 */
object RedSurfFocus {

    /** Square by default (Crest). The radius parameter stays for call sites that pass one, but
     * content shapes are 0dp: a rounded clip is a path clip, paid every frame. */
    @Composable
    fun shape(@Suppress("UNUSED_PARAMETER") radius: Dp = 0.dp): ClickableSurfaceShape =
        ClickableSurfaceDefaults.shape(shape = RectangleShape)

    @Composable
    fun pillShape(): ClickableSurfaceShape = ClickableSurfaceDefaults.shape(
        shape = RoundedCornerShape(50),
    )

    @Composable
    fun rowColors(selected: Boolean = false, resting: Color = Color.Transparent): ClickableSurfaceColors =
        ClickableSurfaceDefaults.colors(
            containerColor = if (selected) SurfaceRaised else resting,
            contentColor = TextPrimary,
            focusedContainerColor = Accent,
            focusedContentColor = TextPrimary,
        )

    /** Rows: the red fill is the cursor, so no ring. */
    @Composable
    fun border(): ClickableSurfaceBorder = ClickableSurfaceDefaults.border(focusedBorder = Border.None)

    /** Pills and cards: a white 2dp outline marks focus, distinct from the red "current" fill. */
    @Composable
    fun pillBorder(): ClickableSurfaceBorder = ClickableSurfaceDefaults.border(
        focusedBorder = Border(border = BorderStroke(2.dp, TextPrimary)),
    )

    @Composable
    fun glow(): ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(focusedGlow = Glow.None)

    @Composable
    fun scale(): ClickableSurfaceScale = ClickableSurfaceDefaults.scale(focusedScale = 1f)

    /** Pills: red when current; a focused, non-current pill lifts one step (plus [pillBorder]). */
    @Composable
    fun colors(selected: Boolean = false): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = if (selected) Accent else Surface,
        contentColor = TextPrimary,
        focusedContainerColor = if (selected) Accent else SurfaceRaised,
        focusedContentColor = TextPrimary,
    )

    /** All four Surface visuals for one grid cell, neutralized (see [gridCell]). */
    class GridCellStyle(
        val shape: ClickableSurfaceShape,
        val colors: ClickableSurfaceColors,
        val scale: ClickableSurfaceScale,
        val border: ClickableSurfaceBorder,
        val glow: ClickableSurfaceGlow,
    )

    /**
     * EPG_GRID_REDESIGN.md G.6 - a sibling for the Guide grid's programme cells, not a change to
     * the row model above (which every list/picker in the app keeps using as-is). The standard
     * treatment is designed for standalone list rows and is wrong for a 40dp cell packed edge to
     * edge with its neighbours: [scale]'s 1.04 grows a cell into the rows above and below and
     * breaks the grid's lines, [glow]'s 16dp shadow bleeds across two neighbouring rows, and the
     * 2dp [border] reads as blunt at that density. So this variant switches all of it off -
     * scale 1.0, no glow, no border, transparent fill - and keeps only what `Surface(onClick)`
     * is genuinely needed for: TV focus + D-pad OK → onClick plumbing that's proven across every
     * picker in this app. The grid draws its own cursor (`EpgGridColumn.kt`) behind the cell.
     */
    @Composable
    fun gridCell(radius: Dp): GridCellStyle = GridCellStyle(
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(radius)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow.None),
    )
}

/**
 * Keeps a screen composed (its state, queries and scroll positions survive) while making it
 * genuinely absent from layout, drawing, and focus search. `visible = false` measures the subtree
 * at 0x0 but deliberately never places it - Compose then marks the whole subtree as not placed,
 * and its 2D focus search skips every unplaced focus target.
 *
 * Why not `Modifier.size(0.dp)` (what `LiveTvScreen`/`SettingsScreen` used before, 2026-09-22):
 * a 0dp subtree is still *placed*, so its lists and grid cells stayed focus candidates. Any D-pad
 * press with no real target in that direction (UP or LEFT from the nav strip) widened the search,
 * picked a node inside the hidden screen, and Compose's own bring-into-view on it crashed the app
 * ("Expected BringIntoViewRequester to not be used before parents are placed") - found live
 * 2026-09-28, reproduced with nothing more than UP from the Settings pill. `FOCUS_MODEL.md` rule 4.
 */
fun Modifier.hiddenButComposed(visible: Boolean): Modifier =
    if (visible) {
        this
    } else {
        this.layout { measurable, _ ->
            measurable.measure(Constraints.fixed(0, 0))
            layout(0, 0) { /* deliberately not placed */ }
        }
    }
