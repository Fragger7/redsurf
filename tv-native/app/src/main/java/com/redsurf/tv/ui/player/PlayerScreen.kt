package com.redsurf.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import com.redsurf.tv.player.PlayerHost

/**
 * The overlay chrome shown while a channel plays fullscreen (PHASE_2.md - "The Player"). None of
 * the states beyond the implicit "nothing showing" do anything yet - [overlay]/the key router/the
 * state machine land in #2.1b. This step (#2.1a) is a pure extraction: PlayerScreen now owns the
 * fullscreen surface `LiveTvScreen.kt` used to build inline, with zero behavior change, so the
 * router/state-machine work lands in a file that isn't also doing browse-screen duty. See
 * PHASE_2.md decision 2 for the eventual shape of [PlayerOverlay].
 */
sealed class PlayerOverlay {
    object None : PlayerOverlay()
    object ZapBanner : PlayerOverlay()
    data class Controls(val floor: Floor) : PlayerOverlay() {
        enum class Floor { Tiles, Actions }
    }
    object ChannelList : PlayerOverlay()
    object ContextMenu : PlayerOverlay()
    data class Picker(val kind: PickerKind) : PlayerOverlay()
}

enum class PickerKind { Audio, Subtitles, Info, History }

/**
 * Owns the fullscreen surface: opaque black background (fixes the 4:3-letterbox bleed-through
 * found in PHASE_1 round 6 - the browse Row stays composed underneath for state preservation, so
 * a transparent letterbox gap would show it through), focus capture so the D-pad has somewhere
 * real to land, and Back-key passthrough (everything else is swallowed so input can't leak to the
 * browse screen underneath; Back must keep bubbling to `LiveTvScreen`'s `BackHandler`, whose
 * `OnBackPressedDispatcher` registration is a separate mechanism from this `onKeyEvent` - found
 * live 2026-09-12 the first time this swallowed Back unconditionally and broke exiting fullscreen
 * entirely).
 *
 * Currently identical in behavior to the inline `Box` it replaced - `PlayerOverlay`/the key router
 * aren't wired to anything here yet (PHASE_2.md #2.1b onward).
 */
@Composable
fun PlayerScreen(streamUrl: String?, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { it.key != Key.Back },
    ) {
        PlayerHost(streamUrl = streamUrl, fullscreen = true, modifier = Modifier.fillMaxSize())
    }
}
