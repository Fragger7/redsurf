package com.redsurf.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.TextPrimary

/** The seven destinations from docs/vision/UI_SPEC.md #3. LiveTv is the only one that's real. */
enum class NavDestination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    LiveTv("Live TV", Icons.Filled.PlayArrow),
    Movies("Movies", Icons.Filled.Star),
    Series("Series", Icons.Filled.List),
    Guide("Guide", Icons.Filled.Info),
    Search("Search", Icons.Filled.Search),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * The top nav strip (UI_SPEC.md #3, references/streamvault/Home.png): wordmark left, each
 * destination an icon+label pill. The active destination is filled red (RedSurfFocus.colors'
 * selected state); wherever the D-pad cursor sits gets the ring/glow/scale on top of that -
 * both can be true on the same pill and must look right together.
 *
 * Sized to fit: the canvas is 960x540dp (1080p at density 2), so the strip has 864dp inside the
 * safe area for a wordmark plus seven pills. The previous version didn't fit - and a Row gives
 * its last child whatever width is left, so "Settings" was measured at near-zero width, its label
 * soft-wrapped to one character per line, and that tall, clipped, invisible pill set the height
 * of the whole strip at ~225dp. Every label here is single-line and non-wrapping so overflow can
 * only ever clip horizontally, never inflate the strip. Found from a device screenshot 2026-09-11.
 */
@Composable
fun NavStrip(current: NavDestination, onSelect: (NavDestination) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Wordmark(modifier = Modifier.padding(start = 4.dp, end = 20.dp))
        NavDestination.entries.forEach { dest ->
            NavPill(dest = dest, selected = dest == current, onClick = { onSelect(dest) })
        }
    }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(Accent, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "RedSurf",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun NavPill(dest: NavDestination, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RedSurfFocus.pillShape(),
        colors = RedSurfFocus.colors(selected = selected),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(dest.icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            Text(
                dest.label,
                style = RedSurfType.label,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
