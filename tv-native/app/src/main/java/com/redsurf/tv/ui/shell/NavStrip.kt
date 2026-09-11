package com.redsurf.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.ui.theme.RedSurfFocus
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
 */
@Composable
fun NavStrip(current: NavDestination, onSelect: (NavDestination) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(SurfaceColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "RedSurf",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(end = 24.dp),
        )
        NavDestination.entries.forEach { dest ->
            NavPill(dest = dest, selected = dest == current, onClick = { onSelect(dest) })
        }
    }
}

@Composable
private fun NavPill(dest: NavDestination, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = RedSurfFocus.colors(selected = selected),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(dest.icon, contentDescription = dest.label, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Text(dest.label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
    }
}
