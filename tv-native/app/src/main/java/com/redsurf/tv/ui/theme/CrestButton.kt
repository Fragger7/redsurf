package com.redsurf.tv.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * The one button in the app (Crest, `docs/vision/design-language/RESEARCH.md` "A. Crest"):
 * buttons are chrome, so they are pills; focus is the same solid red as a row, no ring, glow or
 * scale. Replaces tv-material3's `Button`, whose defaults (1.1 scale, white focused fill, rounded
 * rect) belong to no RedSurf design.
 */
@Composable
fun CrestButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        colors = RedSurfFocus.rowColors(resting = SurfaceRaised),
        shape = RedSurfFocus.pillShape(),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Text(
            label,
            style = RedSurfType.rowSecondary,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
