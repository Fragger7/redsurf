package com.redsurf.tv.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * An honest "not built yet" screen for the destinations Phase 1 doesn't implement (see
 * docs/plans/PHASE_1.md Non-goals) - themed, not fake content.
 *
 * Claims real initial focus and shows it visibly (found live, 2026-09-13/14,
 * `BACKLOG_SWEEP.md` #1) - this used to be a plain `.focusable()` Box with nothing ever calling
 * `requestFocus()` on it, so it held focus invisibly (no ring - Binding technical constraint #4
 * violated) until the first arrow key went looking for *something* to focus and landed
 * unpredictably on a NavStrip pill. Now behaves like every other real screen: claims focus on
 * compose, shows it with the same `RedSurfFocus` ring/glow every other focusable element uses.
 */
@Composable
fun PlaceholderScreen(title: String, subtitle: String) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { focusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            onClick = {},
            modifier = Modifier.focusRequester(focusRequester),
            shape = RedSurfFocus.shape(12.dp),
            colors = RedSurfFocus.rowColors(),
            scale = RedSurfFocus.scale(),
            border = RedSurfFocus.border(),
            glow = RedSurfFocus.glow(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
        }
    }
}
