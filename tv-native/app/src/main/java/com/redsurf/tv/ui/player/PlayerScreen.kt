package com.redsurf.tv.ui.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.redsurf.tv.player.PlayerHost
import kotlinx.coroutines.delay

private const val TAG = "PlayerScreen"
private const val ZAP_BANNER_TIMEOUT_MS = 4_000L
private const val CONTROLS_TIMEOUT_MS = 8_000L

/**
 * The overlay chrome shown while a channel plays fullscreen (PHASE_2.md - "The Player"). See
 * PHASE_2.md decision 2 for the shape and decision 4 for exactly how Back peels between them.
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
 * Owns the fullscreen surface and, as of this task, the overlay state machine and key router
 * (PHASE_2.md #2.1b) - still no visual chrome beyond the opaque background: the info block, tile
 * row, action row, scrim and breadcrumb/clock are #2.1's remaining slice and #2.3. This is
 * deliberately a smaller step than the full #2.1 task (user request, 2026-09-12 - bite-sized
 * chunks against the rate-limit window): state machine + router + Back-peeling + timeouts, all
 * verifiable from `logcat` alone, no device visual check needed yet.
 *
 * [onExitFullscreen] is called only when Back is pressed with nothing showing ([PlayerOverlay.None])
 * - every other state peels inward first (decision 4). This replaced `LiveTvScreen`'s own
 * `BackHandler`, which only ever knew "exit fullscreen" - now that there's real overlay state to
 * peel through, one `BackHandler` needs to own both behaviors, and it has to live where the state
 * does.
 */
@Composable
fun PlayerScreen(
    streamUrl: String?,
    focusRequester: FocusRequester,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlay by remember { mutableStateOf<PlayerOverlay>(PlayerOverlay.None) }

    // Long-press OK (decision 3) is tracked across a held key: isLongPress flips true on a
    // repeat KeyDown once Android's own long-press timeout elapses, not on a single event, so
    // whether it fired has to be remembered across the DOWN stream and checked on UP.
    var okWasLongPress by remember { mutableStateOf(false) }

    // Bumped on every handled key so the timeout effects below restart even when the key press
    // doesn't change `overlay` itself (e.g. zapping again while ZapBanner is already showing) -
    // decision 6's "any key resets the timer". A plain `LaunchedEffect(overlay)` wouldn't see a
    // change in that case, since re-assigning the same PlayerOverlay value is a no-op to Compose.
    var activityTick by remember { mutableStateOf(0) }

    LaunchedEffect(overlay) {
        Log.d(TAG, "overlay -> $overlay")
    }

    LaunchedEffect(overlay, activityTick) {
        when (overlay) {
            PlayerOverlay.ZapBanner -> {
                delay(ZAP_BANNER_TIMEOUT_MS)
                overlay = PlayerOverlay.None
            }
            is PlayerOverlay.Controls -> {
                delay(CONTROLS_TIMEOUT_MS)
                overlay = PlayerOverlay.None
            }
            else -> {} // ChannelList, ContextMenu, Picker never auto-hide - decision 6.
        }
    }

    // Back peels exactly one layer, always (decision 4) - owns all Back handling for the player,
    // not just "exit fullscreen", now that there's overlay state to peel through first.
    BackHandler(enabled = true) {
        overlay = when (val current = overlay) {
            is PlayerOverlay.Picker -> PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Actions)
            is PlayerOverlay.Controls ->
                if (current.floor == PlayerOverlay.Controls.Floor.Actions) {
                    PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Tiles)
                } else {
                    PlayerOverlay.None
                }
            PlayerOverlay.ChannelList, PlayerOverlay.ContextMenu, PlayerOverlay.ZapBanner -> PlayerOverlay.None
            PlayerOverlay.None -> {
                onExitFullscreen()
                PlayerOverlay.None
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Opaque black, painted before anything else (PHASE_1 round 6 - the browse Row stays
            // composed underneath for state preservation, so a transparent letterbox gap on a
            // non-16:9 channel would otherwise show it through).
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                // Back never gets consumed here - it must keep bubbling to the BackHandler above,
                // a separate dispatcher mechanism from this modifier. Swallowing it here
                // unconditionally, as an earlier version of this fix did, silently broke exiting
                // fullscreen entirely. Found live, 2026-09-12.
                if (event.key == Key.Back) return@onKeyEvent false

                val isDown = event.type == KeyEventType.KeyDown
                val isFirstDown = isDown && event.nativeKeyEvent.repeatCount == 0
                if (isDown) activityTick++

                when (val current = overlay) {
                    is PlayerOverlay.Controls -> {
                        // The elevator (decision 5): DOWN/UP swap floors in the same slot instead
                        // of opening/closing a new overlay.
                        if (isFirstDown) {
                            when (event.key) {
                                Key.DirectionDown ->
                                    if (current.floor == PlayerOverlay.Controls.Floor.Tiles) {
                                        overlay = PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Actions)
                                    }
                                Key.DirectionUp ->
                                    if (current.floor == PlayerOverlay.Controls.Floor.Actions) {
                                        overlay = PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Tiles)
                                    }
                                else -> {}
                            }
                        }
                    }
                    PlayerOverlay.None -> {
                        // The Level 0 control matrix (decision 3). LEFT/RIGHT/UP/DOWN act once
                        // per press (isFirstDown), not once per repeat tick, so a held button
                        // doesn't machine-gun through channels.
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> when {
                                isDown && event.nativeKeyEvent.repeatCount == 0 -> okWasLongPress = false
                                isDown && event.nativeKeyEvent.isLongPress && !okWasLongPress -> {
                                    okWasLongPress = true
                                    overlay = PlayerOverlay.ContextMenu
                                }
                                event.type == KeyEventType.KeyUp && !okWasLongPress ->
                                    overlay = PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Tiles)
                                else -> {}
                            }
                            Key.DirectionUp, Key.DirectionDown ->
                                // Real zap (neighbour lookup, tune, badges) is #2.2 - this only
                                // proves the state transition for now.
                                if (isFirstDown) overlay = PlayerOverlay.ZapBanner
                            Key.DirectionLeft -> if (isFirstDown) overlay = PlayerOverlay.ChannelList
                            Key.DirectionRight ->
                                // Last-channel zap is #2.4 - state transition only for now.
                                if (isFirstDown) overlay = PlayerOverlay.ZapBanner
                            else -> {}
                        }
                    }
                    else -> {} // ZapBanner, ChannelList, ContextMenu, Picker: only Back acts (above).
                }
                true
            },
    ) {
        PlayerHost(streamUrl = streamUrl, fullscreen = true, modifier = Modifier.fillMaxSize())
    }
}
