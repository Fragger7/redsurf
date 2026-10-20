package com.redsurf.tv.ui

import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/**
 * A channel logo, or the channel's initial letter when there is no logo or it fails to load -
 * blank while loading (no flicker on fast scrolls). A plain `AsyncImage`: the
 * `SubcomposeAsyncImage` it replaces runs a subcomposition per image, which Coil's own docs warn
 * is slow (performance salvage, 2026-10-20 - it showed in the fullscreen entry frame).
 */
@Composable
fun LogoOrLetter(
    icon: String?,
    name: String,
    letterStyle: TextStyle,
    letterColor: Color,
    imageModifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var failed by remember(icon) { mutableStateOf(icon.isNullOrBlank()) }
    if (failed) {
        Text(name.take(1).uppercase(), style = letterStyle, color = letterColor)
    } else {
        AsyncImage(
            model = icon,
            contentDescription = null,
            contentScale = contentScale,
            modifier = imageModifier,
            onState = { if (it is AsyncImagePainter.State.Error) failed = true },
        )
    }
}
