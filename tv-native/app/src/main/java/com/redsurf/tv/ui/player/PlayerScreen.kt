package com.redsurf.tv.ui.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.redsurf.tv.data.ChannelRepository
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.player.PlayerHost
import com.redsurf.tv.player.StreamInfo
import com.redsurf.tv.ui.theme.Background
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "PlayerScreen"
private const val ZAP_BANNER_TIMEOUT_MS = 4_000L
private const val CONTROLS_TIMEOUT_MS = 8_000L
private const val CLOCK_TICK_MS = 60_000L

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
 * Owns the fullscreen surface, the overlay state machine, the key router, and real zap
 * (PHASE_2.md #2.1-#2.2). Built in bite-sized slices against the user's rate-limit window
 * (2026-09-12): #2.1a extracted this from `LiveTvScreen`'s inline `Box`; #2.1b added the state
 * machine/router/Back-peeling/timeouts; #2.1's close added the scrim/breadcrumb/clock; #2.2's
 * first slice added the DB queries and player tuning this now calls. Still not built: the tile
 * row and action row content (#2.3), LEFT's real channel-list overlay and RIGHT's real
 * last-channel zap (#2.4).
 *
 * [onExitFullscreen] is called only when Back is pressed with nothing showing ([PlayerOverlay.None])
 * - every other state peels inward first (decision 4). This replaced `LiveTvScreen`'s own
 * `BackHandler`, which only ever knew "exit fullscreen" - now that there's real overlay state to
 * peel through, one `BackHandler` needs to own both behaviors, and it has to live where the state
 * does.
 *
 * Zapping needs to update `focusedChannel`/`previewUrl`, which live in `LiveTvScreen`, not here -
 * [currentChannel] and [onChannelChanged] are that bridge (state/focus discipline, `AGENTS.md`:
 * Back after zapping must land on the channel actually being watched, not the one fullscreen was
 * originally opened on). [onChannelChanged] is expected to update `previewUrl` immediately, the
 * same way `onChannelOpen` already bypasses the browse-debounce - zapping is a deliberate action,
 * not a fly-by, and waiting on that debounce would reintroduce the "duplicate 2-step" bug this
 * exact pattern already fixed once.
 */
@Composable
fun PlayerScreen(
    streamUrl: String?,
    focusRequester: FocusRequester,
    onExitFullscreen: () -> Unit,
    currentChannel: ChannelEntity?,
    repository: ChannelRepository,
    onChannelChanged: (ChannelEntity) -> Unit,
    // "PlaylistName › GroupName" for whatever's playing - blank hides the breadcrumb (e.g. the
    // very first frame before a channel is known). Computed by the caller (LiveTvScreen already
    // has the group/playlist context) rather than re-derived here.
    breadcrumb: String = "",
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var streamInfo by remember { mutableStateOf(StreamInfo()) }
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
                                // UP -> previous, DOWN -> next (decision 3). One zap per press,
                                // not per repeat tick (isFirstDown) - a held key must not
                                // machine-gun through channels.
                                if (isFirstDown) {
                                    overlay = PlayerOverlay.ZapBanner
                                    val channel = currentChannel
                                    if (channel != null) {
                                        val goingUp = event.key == Key.DirectionUp
                                        scope.launch {
                                            val next = if (goingUp) {
                                                repository.prevChannel(channel.playlistId, channel.groupName, channel.num)
                                            } else {
                                                repository.nextChannel(channel.playlistId, channel.groupName, channel.num)
                                            }
                                            next?.let(onChannelChanged)
                                        }
                                    }
                                }
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
        PlayerHost(
            streamUrl = streamUrl,
            fullscreen = true,
            modifier = Modifier.fillMaxSize(),
            onStreamInfo = { streamInfo = it },
        )

        // Scrim + breadcrumb/clock (decision 7's chrome, not its info-block content - that's
        // #2.3). Shown whenever any overlay is up; Level 0 (nothing showing) stays pure video,
        // per the reference screenshots - there's no transient chrome on entry.
        if (overlay != PlayerOverlay.None) {
            var now by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(CLOCK_TICK_MS)
                    now = System.currentTimeMillis()
                }
            }
            val clockText = remember(now) {
                SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()).format(now)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Background.copy(alpha = 0.85f),
                        ),
                    ),
            )

            if (breadcrumb.isNotBlank()) {
                Text(
                    breadcrumb,
                    style = RedSurfType.rowSecondary,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart).padding(24.dp).fillMaxWidth(0.5f),
                )
            }
            Text(
                clockText,
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
            )

            // The info block itself (decision 7's content, not just its chrome) - real now for
            // both the zap banner and Controls, since the tile/action row that would otherwise
            // sit below it on the Controls floor doesn't exist yet (#2.3) to differentiate the
            // two positions the brief describes.
            if (overlay == PlayerOverlay.ZapBanner || overlay is PlayerOverlay.Controls) {
                PlayerInfoBlock(
                    channel = currentChannel,
                    streamInfo = streamInfo,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Level 1 / zap-banner content (decision 7) - logo, channel line, badges. Programme title and
 * the next-programme line are EPG-only and omitted entirely until Phase 3, not shown empty; same
 * honesty as the browse screen's "No schedule information." Badges are individually omitted
 * (never a placeholder) when [StreamInfo] hasn't reported that field yet.
 */
@Composable
private fun PlayerInfoBlock(channel: ChannelEntity?, streamInfo: StreamInfo, modifier: Modifier = Modifier) {
    if (channel == null) return
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            val icon = channel.streamIcon
            if (icon.isNullOrBlank()) {
                Text(channel.name.take(1).uppercase(), style = RedSurfType.heroTitle, color = TextPrimary)
            } else {
                SubcomposeAsyncImage(
                    model = icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    error = { Text(channel.name.take(1).uppercase(), style = RedSurfType.heroTitle, color = TextPrimary) },
                    loading = {},
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text("No schedule information", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.num.toString(),
                    style = RedSurfType.heroTitle,
                    color = TextSecondary,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(channel.name, style = RedSurfType.heroTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val badges = listOfNotNull(
                streamInfo.resolutionClass,
                streamInfo.frameRate?.let { "$it FPS" },
                streamInfo.audioChannels,
                streamInfo.audioCodec,
            )
            if (badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { label ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Surface)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(label, style = RedSurfType.badge, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}
