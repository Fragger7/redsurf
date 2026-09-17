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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.redsurf.tv.data.ChannelRepository
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.player.NetworkStats
import com.redsurf.tv.player.PlayerHost
import com.redsurf.tv.player.StreamInfo
import com.redsurf.tv.player.rememberPlayerController
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.Background
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import com.redsurf.tv.ui.theme.WaveSpinner
import com.redsurf.tv.vod.XtreamApi
import com.redsurf.tv.vod.XtreamUserInfo
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
    // Most-recently-watched first, capped at 8 by the caller (decision 8) - an in-memory stub
    // until #2.5's real `recent_channels` table lands; the tile row and History picker both read
    // this same list. Excludes the channel currently playing - the caller's job, not this one's.
    recentChannels: List<ChannelEntity> = emptyList(),
    // BACKLOG_SWEEP.md #11/#12, AppPreferences-backed (Settings -> Playback/Appearance) - both
    // default false, matching this screen's pre-toggle behavior exactly.
    blackScreenBetweenZaps: Boolean = false,
    showRawResolution: Boolean = false,
    // PLAYER_ENGINEERING_BRIEF.md §6/§9 - the currently-playing channel's own playlist User-Agent,
    // resolved reactively by LiveTvScreen (see its own doc comment for why not here).
    playlistUserAgent: String? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var overlay by remember { mutableStateOf<PlayerOverlay>(PlayerOverlay.None) }
    val tilesFloorFocus = remember { FocusRequester() }
    val actionsFloorFocus = remember { FocusRequester() }
    var favoriteToast by remember { mutableStateOf<String?>(null) }

    // PLAYER_ENGINEERING_BRIEF.md §6/§9: [playlistUserAgent] is resolved by the caller
    // (LiveTvScreen, reactively, well before this screen is ever composed - see its own doc
    // comment) rather than looked up here. A fresh async lookup started at this composable's own
    // first composition would race directly against `rememberPlayerController`'s `remember {}` in
    // the very same composition pass and always lose - the controller and its data source's
    // User-Agent have to exist together from this screen's very first frame.
    val controller = rememberPlayerController(playlistUserAgent)
    val streamInfo by controller.streamInfo.collectAsState()
    val networkStats by controller.networkStats.collectAsState()
    val errorPresentation by controller.errorPresentation.collectAsState()
    val isBuffering by controller.isBuffering.collectAsState()
    val resizeMode by controller.resizeMode.collectAsState()

    // Shared by Level 0's zap and the Controls elevator's ceiling/floor fallback below (user
    // decision, 2026-09-12, after comparing against real TiviMate behavior: TiviMate stops UP/DOWN
    // dead once its own player-controls step is also showing, but that step doesn't exist here yet
    // - rather than copy the dead stop, an UP/DOWN with nowhere left to navigate to falls through
    // to changing the channel, exactly like Level 0's own UP/DOWN). Shows the zap banner the same
    // way regardless of which overlay state triggered it.
    //
    // Direction corrected 2026-09-14 (AGENTS.md, user verified against real TiviMate): UP must
    // increment (go to the next *higher* channel number), DOWN decrement - PHASE_2.md decision 3
    // originally had this backwards (UP -> prevChannel), which is exactly what read as "up means
    // down" to the user. UP -> nextChannel, DOWN -> prevChannel, now matching TiviMate.
    fun zap(goingUp: Boolean) {
        overlay = PlayerOverlay.ZapBanner
        val channel = currentChannel ?: return
        scope.launch {
            val next = if (goingUp) {
                repository.nextChannel(channel.playlistId, channel.groupName, channel.num)
            } else {
                repository.prevChannel(channel.playlistId, channel.groupName, channel.num)
            }
            // Zap-order diagnostics (AGENTS.md, 2026-09-14 mini-sprint) - debug builds only in
            // spirit (this whole app is unreleased-signed-release-only anyway, but this line
            // exists purely to be grepped out of logcat during the mini-sprint's machine test,
            // not for any release-path purpose). Format is the one the test script parses.
            Log.d(
                TAG,
                "zap dir=${if (goingUp) "up" else "down"} from=(${channel.num} ${channel.name}) " +
                    "-> to=${next?.let { "(${it.num} ${it.name})" } ?: "null"} group=${channel.groupName}",
            )
            next?.let(onChannelChanged)
        }
    }

    // RIGHT's last-channel zap (PHASE_2.md decision 3/14) - the second-newest recent_channels
    // row, since the newest one is whatever's actually playing right now (decision 14's own
    // note). Previously a state transition only (ZapBanner with no real channel change); now that
    // #2.5's real table exists, this is real.
    fun lastChannelZap() {
        overlay = PlayerOverlay.ZapBanner
        scope.launch {
            val previous = repository.secondMostRecentChannel()
            previous?.let(onChannelChanged)
        }
    }

    // Zap-order diagnostics (AGENTS.md, 2026-09-14 mini-sprint) - logs the group's own num/name
    // sequence once, on entering fullscreen, in the exact order the visible channel list and the
    // zap queries both use (`ORDER BY num, name`) - the reference the machine test compares zap's
    // actual behavior against. Keyed on Unit, not currentChannel, deliberately: this is "what did
    // the group look like when this fullscreen session started," not a running log of every
    // subsequent channel change (the zap log above already covers that).
    LaunchedEffect(Unit) {
        val channel = currentChannel ?: return@LaunchedEffect
        val snapshot = repository.debugFirstInGroup(channel.playlistId, channel.groupName)
        Log.d(
            TAG,
            "group snapshot (${channel.groupName}, ${snapshot.size} shown): " +
                snapshot.joinToString(", ") { "(${it.num} ${it.name})" },
        )
    }

    // Opening the Tiles floor focuses the first recent channel if any, else TV guide (decision 8)
    // - re-fires every genuine transition into Controls(Tiles), not just once ever, since the
    // user can leave and re-open this floor repeatedly within one fullscreen session. Opening the
    // Actions floor focuses its first tile the same way, now that #2.3 gives it real content.
    LaunchedEffect(overlay) {
        if (overlay == PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Tiles)) {
            delay(50)
            runCatching { tilesFloorFocus.requestFocus() }
        } else if (overlay == PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Actions)) {
            delay(50)
            runCatching { actionsFloorFocus.requestFocus() }
        }
    }

    // Reclaim focus onto this composable's own root whenever `overlay` collapses back to
    // "nothing with real descendant content" (None/ZapBanner) - found live, 2026-09-12, reported
    // as "the whole button engine seems to crash": tuning a tile from the Controls floor tears
    // down the tile row that held real D-pad focus, and without this, Compose's fallback focus-
    // picking after a torn-down focused descendant is unpredictable - sometimes landing nowhere,
    // silently breaking all further key routing (this screen's onKeyEvent needs real focus to
    // receive anything) until Back is pressed, since Back's dispatcher is a separate mechanism
    // that doesn't require focus at all - exactly why it was the only way out.
    //
    // Controls(Actions) used to be included here too, back when its content was a plain,
    // non-focusable Text placeholder - removed now that #2.3 gives it real focusable tiles of its
    // own (the effect above claims `actionsFloorFocus` for it instead); leaving this condition in
    // would now steal focus away from those tiles the instant the floor opens.
    LaunchedEffect(overlay) {
        if (overlay == PlayerOverlay.None || overlay == PlayerOverlay.ZapBanner) {
            delay(50)
            runCatching { focusRequester.requestFocus() }
        }
    }

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
            // History is opened from the Tiles floor (decision 8), not Actions - peeling back
            // from it has to return there, not to Actions like the other three Picker kinds
            // (decision 9, all opened from Actions). The brief's decision 4 didn't distinguish
            // Picker kinds; fixing that here rather than silently sending History to the wrong
            // floor, per the brief's own instruction to say so when a decision needs adjusting.
            is PlayerOverlay.Picker ->
                if (current.kind == PickerKind.History) {
                    PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Tiles)
                } else {
                    PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Actions)
                }
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
            // Focus trap - found live, 2026-09-12, reported as focus ending up "left of Live TV"
            // with Back then landing on the channel group. LiveTvScreen keeps its browse Row
            // (Categories/Channels/Preview) composed and real - invisible, but still focusable -
            // as a sibling underneath this fullscreen Box the entire time it's showing (its own
            // doc comment: state/focus discipline). Compose's directional focus search walks the
            // *whole* composition, not just this subtree, so any arrow key that hits a dead end in
            // here (a picker's top/bottom row, an overlay edge) can resolve to a node in that
            // hidden Row instead of stopping - landing real D-pad focus on an invisible category
            // button, indistinguishable from "focus is just gone" until Back does something
            // nonsensical. `exit = Cancel` refuses every such move, for every direction, so a key
            // with nowhere sensible to go in here is a no-op instead of an escape hatch - one
            // fix covering every current and future overlay/picker edge, not a patch per case.
            // Back is unaffected: BackHandler is a separate dispatcher that doesn't use focus
            // search at all.
            .focusProperties { exit = { FocusRequester.Cancel } }
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
                        // of opening/closing a new overlay. LEFT/RIGHT between tiles and OK to
                        // activate one are deliberately left unconsumed (false) so Compose's own
                        // focus traversal and the focused Surface's built-in click-on-OK handling
                        // act on it normally; this router owns Level 0 and the floor swap, not
                        // navigation inside real overlay content.
                        //
                        // DOWN/UP are always consumed here, though - even DOWN on the bottom floor
                        // (Actions) and UP on the top one (Tiles), which have no floor to swap to.
                        // Found live, 2026-09-12, reported as "focus is lost without recovery...
                        // tiles disappear": leaving those two cases unconsumed (an earlier version
                        // did, matching the swap conditions exactly) let the key fall through to
                        // Compose's default focus search, which had nowhere to go from a
                        // horizontal Row's edge and left the tile row with no focused descendant
                        // at all - and unlike the None/ZapBanner case above, `overlay` never
                        // changes here, so the LaunchedEffect that reclaims focus onto this
                        // screen's root never fires either. Swallowing the dead-end presses as a
                        // no-op keeps focus exactly where it already was.
                        // Consumed on every DOWN event for these two keys, not just isFirstDown -
                        // a held key's repeat-count ticks need to stay dead ends too, the same
                        // reasoning as the comment above, just covering the repeat stream as well
                        // as the initial press. At the bottom floor (Actions) DOWN has nowhere
                        // left to swap to, so it falls through to channel-down instead (see zap()
                        // above); DOWN at the top floor (Tiles) still just swaps to Actions.
                        if (isDown && event.key == Key.DirectionDown) {
                            if (isFirstDown) {
                                if (current.floor == PlayerOverlay.Controls.Floor.Tiles) {
                                    overlay = PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Actions)
                                } else {
                                    zap(goingUp = false)
                                }
                            }
                            return@onKeyEvent true
                        }
                        // Mirror of the above: UP at the top floor (Tiles) has nowhere left to
                        // swap to, so it falls through to channel-up.
                        if (isDown && event.key == Key.DirectionUp) {
                            if (isFirstDown) {
                                if (current.floor == PlayerOverlay.Controls.Floor.Actions) {
                                    overlay = PlayerOverlay.Controls(PlayerOverlay.Controls.Floor.Tiles)
                                } else {
                                    zap(goingUp = true)
                                }
                            }
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    }
                    // Picker rows (History, built this round) navigate and select normally too -
                    // same reasoning as Controls above.
                    is PlayerOverlay.Picker -> return@onKeyEvent false
                    // ZapBanner included here, not just None (found live, 2026-09-12, reported
                    // as "repeatedly fails... have to wait for the banner to disappear"): the
                    // banner is a transient decoration on top of Level 0, not a distinct modal
                    // state - every Level-0 key must keep working while it's showing, including
                    // zapping again immediately, or the D-pad goes dead for up to 4 seconds after
                    // every single zap.
                    PlayerOverlay.None, PlayerOverlay.ZapBanner -> {
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
                                if (isFirstDown) zap(goingUp = event.key == Key.DirectionUp)
                            Key.DirectionLeft -> if (isFirstDown) overlay = PlayerOverlay.ChannelList
                            Key.DirectionRight -> if (isFirstDown) lastChannelZap()
                            else -> {}
                        }
                    }
                    PlayerOverlay.ChannelList -> return@onKeyEvent false // real focusable content now (#2.4) - only Back is handled here.
                    PlayerOverlay.ContextMenu -> {
                        // The long-press that opens this menu is one continuous key-down/up
                        // sequence, and holding a D-pad key generates repeat KeyDown ticks the
                        // whole time it's physically down, same as any held key elsewhere in this
                        // screen. First fix (2026-09-17) only swallowed the terminating KeyUp,
                        // which stopped the menu closing itself instantly - but every repeat
                        // KeyDown tick *before* that release was still unconsumed, each one
                        // independently reaching the menu's just-focused row and firing its click
                        // (found live: "toggles between remove/add if I keep pressing OK" - not
                        // repeated taps, the *same* held press's own repeat stream). Swallow every
                        // DirectionCenter/Enter event for as long as `okWasLongPress` stays true -
                        // covers the whole gesture regardless of how long it's held - and only
                        // clear it on that gesture's own KeyUp, so a genuinely new subsequent press
                        // (which starts with `okWasLongPress` already false) still clicks normally.
                        if ((event.key == Key.DirectionCenter || event.key == Key.Enter) && okWasLongPress) {
                            if (event.type == KeyEventType.KeyUp) okWasLongPress = false
                        } else {
                            return@onKeyEvent false
                        }
                    }
                }
                true
            },
    ) {
        PlayerHost(
            controller = controller,
            streamUrl = streamUrl,
            fullscreen = true,
            modifier = Modifier.fillMaxSize(),
            blackScreenBetweenZaps = blackScreenBetweenZaps,
        )

        // Branded, centered feedback (user request, 2026-09-17, after comparing directly against
        // TiviMate: a spinner front-and-center while genuinely loading/buffering/reconnecting, a
        // full branded error treatment only once retries are exhausted - replacing the old subtle
        // top-left text badge entirely, not supplementing it). PLAYER_ENGINEERING_BRIEF.md §11's
        // "non-blocking, never steals D-pad focus" still holds - this is drawn, not a dialog.
        val terminalError = errorPresentation?.takeIf { it.isTerminal }
        if (terminalError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface.copy(alpha = 0.95f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Accent, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        terminalError.message,
                        style = RedSurfType.sectionTitle,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    terminalError.errorCode?.let { code ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(code, style = RedSurfType.rowSecondary, color = TextSecondary)
                    }
                }
            }
        } else if (isBuffering || errorPresentation?.isRetrying == true) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaveSpinner()
                    errorPresentation?.message?.let { message ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(message, style = RedSurfType.rowSecondary, color = TextSecondary)
                    }
                }
            }
        }

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

            // The info block itself (decision 7's content) plus, on the Controls state, the
            // elevator row beneath it. Both floors sit at the same position for now - no slide
            // animation between them yet (deliberately simplified this round to keep the slice
            // small; the functional floor-swap from #2.1b already works, this is polish on top
            // of it, not new behavior).
            if (overlay == PlayerOverlay.ZapBanner || overlay is PlayerOverlay.Controls) {
                Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    PlayerInfoBlock(
                        channel = currentChannel,
                        streamInfo = streamInfo,
                        showRawResolution = showRawResolution,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val controlsOverlay = overlay as? PlayerOverlay.Controls
                    if (controlsOverlay != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        when (controlsOverlay.floor) {
                            PlayerOverlay.Controls.Floor.Tiles -> TileRow(
                                recentChannels = recentChannels,
                                initialFocus = tilesFloorFocus,
                                onOpenGuide = onExitFullscreen,
                                onOpenHistory = { overlay = PlayerOverlay.Picker(PickerKind.History) },
                                onOpenChannel = { channel ->
                                    onChannelChanged(channel)
                                    overlay = PlayerOverlay.ZapBanner
                                },
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            // The five real actions (decision 9) - only ones that work, no
                            // greyed tiles for Multiview/PiP/Recordings/Search (separate features,
                            // a disabled tile is hollow UI).
                            PlayerOverlay.Controls.Floor.Actions -> ActionRow(
                                resizeMode = resizeMode,
                                rawResolution = streamInfo.rawResolution,
                                initialFocus = actionsFloorFocus,
                                onChannels = { overlay = PlayerOverlay.ChannelList },
                                onAudio = { overlay = PlayerOverlay.Picker(PickerKind.Audio) },
                                onSubtitles = { overlay = PlayerOverlay.Picker(PickerKind.Subtitles) },
                                onAspect = { controller.cycleResizeMode() },
                                onVideoInfo = { overlay = PlayerOverlay.Picker(PickerKind.Info) },
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }
            }
        }

        // Decision 10's narrow bottom-right panel, one kind at a time - History (#2.3's first
        // slice) plus Audio/Subtitles (this slice). Info moved to its own full-screen treatment
        // below (user request, 2026-09-17 - "consider a lightly opaque overlay screen on top of
        // the video, instead of that tiny box... think about VLC"). All share the same dismiss
        // path: Back, routed by the BackHandler above back to whichever floor opened them.
        (overlay as? PlayerOverlay.Picker)?.let { picker ->
            when (picker.kind) {
                PickerKind.History -> HistoryPicker(
                    channels = recentChannels,
                    onSelect = { channel ->
                        onChannelChanged(channel)
                        overlay = PlayerOverlay.ZapBanner
                    },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
                PickerKind.Audio -> AudioPicker(
                    controller = controller,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
                PickerKind.Subtitles -> SubtitlePicker(
                    controller = controller,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
                PickerKind.Info -> VideoInfoOverlay(
                    channel = currentChannel,
                    streamInfo = streamInfo,
                    networkStats = networkStats,
                    repository = repository,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // LEFT overlay (decision 11) - new Categories/Channels instances over a scrim, video
        // still visible on the right. OK tunes and closes (via onChannelChanged, same bridge
        // every other tune path uses); Back closing it is handled by the BackHandler above
        // (ChannelList -> None, decision 4), not anything in here.
        if (overlay == PlayerOverlay.ChannelList) {
            ChannelListOverlay(
                repository = repository,
                currentChannel = currentChannel,
                onChannelSelected = { channel ->
                    onChannelChanged(channel)
                    overlay = PlayerOverlay.ZapBanner
                },
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.62f),
            )
        }

        // Long-press OK's context menu (decision 12) - favourite toggle + hide, the two DAO
        // methods that already existed, dead, until now.
        if (overlay == PlayerOverlay.ContextMenu && currentChannel != null) {
            ContextMenuPanel(
                channel = currentChannel,
                repository = repository,
                onDismiss = { overlay = PlayerOverlay.None },
                onHidden = onExitFullscreen, // a hidden channel can't keep playing here (decision 12)
                onFavoriteToggled = { nowFavorite -> favoriteToast = if (nowFavorite) "Added to favourites" else "Removed from favourites" },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // user-found gap, 2026-09-17: "I couldn't verify where to check if it was added to
        // favorites" - there's no favourites list UI yet (AGENTS.md backlog), so this is the only
        // feedback the toggle gets right now. Same transient-badge language as errorPresentation
        // above, not a native Android Toast (this whole screen is custom Compose chrome).
        favoriteToast?.let { message ->
            LaunchedEffect(message) {
                delay(1_500)
                favoriteToast = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Surface.copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(message, style = RedSurfType.rowSecondary, color = TextPrimary)
            }
        }
    }
}

/**
 * Decision 12's small centred panel. Two items only - favourite/hide, per the brief; "Programme
 * description" and "Lock" wait for EPG and parental controls respectively.
 */
@Composable
private fun ContextMenuPanel(
    channel: ChannelEntity,
    repository: ChannelRepository,
    onDismiss: () -> Unit,
    onHidden: () -> Unit,
    onFavoriteToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isFavorite by remember(channel.streamId) { mutableStateOf(channel.isFavorite) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .width(240.dp)
            .clip(RoundedCornerShape(16.dp))
            // Transparent, matching TiviMate (user request, 2026-09-17) - was a fully opaque
            // Surface, hiding the video behind it entirely.
            .background(Surface.copy(alpha = 0.85f))
            .padding(12.dp),
    ) {
        Text(
            channel.name,
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        TvSurface(
            onClick = {
                val newValue = !isFavorite
                isFavorite = newValue
                scope.launch { repository.setFavorite(channel.playlistId, channel.streamId, newValue) }
                onFavoriteToggled(newValue)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            shape = RedSurfFocus.shape(8.dp),
            colors = RedSurfFocus.rowColors(),
            scale = RedSurfFocus.scale(),
            border = RedSurfFocus.border(),
            glow = RedSurfFocus.glow(),
        ) {
            Text(
                if (isFavorite) "Remove from favourites" else "Add to favourites",
                style = RedSurfType.rowTitle,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        TvSurface(
            onClick = {
                scope.launch { repository.setHidden(channel.playlistId, channel.streamId, true) }
                onHidden()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RedSurfFocus.shape(8.dp),
            colors = RedSurfFocus.rowColors(),
            scale = RedSurfFocus.scale(),
            border = RedSurfFocus.border(),
            glow = RedSurfFocus.glow(),
        ) {
            Text(
                "Hide channel",
                style = RedSurfType.rowTitle,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * The tile row (decision 8, Tiles floor): TV guide, History, then up to 8 recent channels. OK on
 * a channel tile tunes and shows the zap banner (stays fullscreen); OK on TV guide exits
 * fullscreen to the browse screen (the merged guide is Phase 3); OK on History opens the picker.
 */
@Composable
private fun TileRow(
    recentChannels: List<ChannelEntity>,
    initialFocus: FocusRequester,
    onOpenGuide: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenChannel: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusGuide = recentChannels.isEmpty()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(
            icon = Icons.Filled.List,
            label = "TV guide",
            onClick = onOpenGuide,
            modifier = if (focusGuide) Modifier.focusRequester(initialFocus) else Modifier,
        )
        Tile(
            // Icons.Filled.History isn't in this project's icon set (material-icons-core only,
            // no -extended dependency) - DateRange is the closest available stand-in.
            icon = Icons.Filled.DateRange,
            label = "History",
            onClick = onOpenHistory,
        )
        recentChannels.forEachIndexed { index, channel ->
            ChannelTile(
                channel = channel,
                onClick = { onOpenChannel(channel) },
                modifier = if (!focusGuide && index == 0) Modifier.focusRequester(initialFocus) else Modifier,
            )
        }
    }
}

/** "1920x1080" -> "16:9", reduced by GCD - generic, not a lookup table, so any source ratio
 * (4:3, 16:9, 21:9, an odd provider crop) reads correctly, not just the common ones. */
private fun aspectRatioOf(rawResolution: String?): String? {
    val (w, h) = rawResolution?.split("x")?.takeIf { it.size == 2 }
        ?.let { (a, b) -> a.toIntOrNull() to b.toIntOrNull() }
        ?.let { (a, b) -> if (a != null && b != null && a > 0 && b > 0) a to b else null }
        ?: return null
    var a = w
    var b = h
    while (b != 0) { val t = b; b = a % b; a = t }
    val gcd = a.takeIf { it != 0 } ?: 1
    return "${w / gcd}:${h / gcd}"
}

/**
 * Level 2 / Actions floor (decision 9). Only actions that work - no greyed tiles for Multiview,
 * PiP, Recordings, Search (separate features, a disabled tile is hollow UI). [resizeMode] drives
 * the Aspect tile's label so it always shows the mode that's actually applied, not a static name;
 * [rawResolution] adds the actual detected source ratio alongside it (user request, 2026-09-17:
 * "include the aspect ratios on the tile, not just Fit, Zoom" - after confirming the mode itself
 * was cycling correctly, "nothing changes on the video" was the channel's own content already
 * matching the screen's ratio, not broken wiring - this makes that visible instead of a bare mode
 * name giving no clue why).
 */
@Composable
private fun ActionRow(
    resizeMode: Int,
    rawResolution: String?,
    initialFocus: FocusRequester,
    onChannels: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onAspect: () -> Unit,
    onVideoInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modeLabel = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
        else -> "Fit"
    }
    val ratio = aspectRatioOf(rawResolution)
    val aspectLabel = if (ratio != null) "$modeLabel ($ratio)" else modeLabel
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(icon = Icons.Filled.List, label = "Channels", onClick = onChannels, modifier = Modifier.focusRequester(initialFocus))
        // VolumeUp/Subtitles/AspectRatio aren't in this project's icon set (material-icons-core
        // only, no -extended dependency - same constraint as History's DateRange stand-in above).
        // Call/Create/Build are the closest available shapes; the label text is what actually
        // carries the meaning here, same reasoning as that earlier substitution.
        Tile(icon = Icons.Filled.Call, label = "Audio", onClick = onAudio)
        Tile(icon = Icons.Filled.Create, label = "Subtitles", onClick = onSubtitles)
        Tile(icon = Icons.Filled.Build, label = aspectLabel, onClick = onAspect)
        Tile(icon = Icons.Filled.Info, label = "Video info", onClick = onVideoInfo)
    }
}

private val TileWidth = 100.dp
private val TileHeight = 72.dp

@Composable
private fun Tile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.size(TileWidth, TileHeight),
        shape = RedSurfFocus.shape(10.dp),
        colors = RedSurfFocus.rowColors(resting = SurfaceRaised.copy(alpha = 0.85f)),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = RedSurfType.rowSecondary, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChannelTile(channel: ChannelEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.size(TileWidth, TileHeight),
        shape = RedSurfFocus.shape(10.dp),
        colors = RedSurfFocus.rowColors(resting = SurfaceRaised.copy(alpha = 0.85f)),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(channel.name.take(1).uppercase(), style = RedSurfType.badge, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(channel.name, style = RedSurfType.rowSecondary, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Decision 10's narrow bottom-right panel, applied to History (decision 8) - the other three
 * picker kinds (Audio/Subtitles/Info) are #2.3's next slice.
 */
@Composable
private fun HistoryPicker(channels: List<ChannelEntity>, onSelect: (ChannelEntity) -> Unit, modifier: Modifier = Modifier) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .padding(24.dp)
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(12.dp),
    ) {
        Text(
            "History",
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        if (channels.isEmpty()) {
            Text(
                "Nothing watched yet this session",
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
                modifier = Modifier.padding(8.dp),
            )
        }
        channels.forEachIndexed { index, channel ->
            TvSurface(
                onClick = { onSelect(channel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (index == 0) it.focusRequester(firstFocus) else it },
                shape = RedSurfFocus.shape(8.dp),
                colors = RedSurfFocus.rowColors(),
                scale = RedSurfFocus.scale(),
                border = RedSurfFocus.border(),
                glow = RedSurfFocus.glow(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        channel.name,
                        style = RedSurfType.rowTitle,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun channelsLabelOf(channelCount: Int): String? = when {
    channelCount <= 0 -> null
    channelCount == 2 -> "Stereo"
    channelCount == 6 -> "5.1"
    else -> "$channelCount ch"
}

private fun languageLabelOf(format: androidx.media3.common.Format, fallbackIndex: Int): String {
    val code = format.language
    val displayName = code?.let { runCatching { Locale(it).displayLanguage }.getOrNull()?.takeIf { name -> name.isNotBlank() } }
    return displayName ?: "Track ${fallbackIndex + 1}"
}

/**
 * Decision 9/10's Audio picker: one row per track across every audio group ExoPlayer currently
 * reports, language name (falling back to "Track N") plus channel count, current selection
 * marked. Selecting persists the language via [TrackManager] (§2.8) and re-reads
 * [controller]'s tracks so the checkmark moves immediately - `currentTracks` doesn't push
 * updates on its own the way a Flow would.
 */
@Composable
private fun AudioPicker(controller: com.redsurf.tv.player.PlayerController, modifier: Modifier = Modifier) {
    var refreshTick by remember { mutableStateOf(0) }
    val rows = remember(refreshTick) {
        controller.getAudioTracks().flatMap { group ->
            (0 until group.length).map { index -> Triple(group, index, group.isTrackSelected(index)) }
        }
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .padding(24.dp)
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(12.dp),
    ) {
        Text("Audio", style = RedSurfType.sectionTitle, color = TextPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        if (rows.isEmpty()) {
            Text("No alternate audio tracks", style = RedSurfType.rowSecondary, color = TextSecondary, modifier = Modifier.padding(8.dp))
        }
        rows.forEachIndexed { index, (group, trackIndex, isSelected) ->
            val format = group.getTrackFormat(trackIndex)
            val channels = channelsLabelOf(format.channelCount)
            val label = languageLabelOf(format, trackIndex) + (channels?.let { " ($it)" } ?: "")
            TvSurface(
                onClick = {
                    controller.selectAudioTrack(group, trackIndex)
                    refreshTick++
                },
                modifier = Modifier.fillMaxWidth().let { if (index == 0) it.focusRequester(firstFocus) else it },
                shape = RedSurfFocus.shape(8.dp),
                colors = RedSurfFocus.rowColors(),
                scale = RedSurfFocus.scale(),
                border = RedSurfFocus.border(),
                glow = RedSurfFocus.glow(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = RedSurfType.rowTitle,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) Text("Selected", style = RedSurfType.badge, color = TextPrimary)
                }
            }
        }
    }
}

/**
 * Decision 9/10's Subtitles picker: an explicit "Off" row first (disables the text renderer
 * entirely, §2.8's corrected default), then one row per subtitle track. "Off" reads as selected
 * whenever none of ExoPlayer's own tracks report selected - the same signal covers both "user
 * chose Off" and "nothing available yet", which is the correct display either way.
 */
@Composable
private fun SubtitlePicker(controller: com.redsurf.tv.player.PlayerController, modifier: Modifier = Modifier) {
    var refreshTick by remember { mutableStateOf(0) }
    val rows = remember(refreshTick) {
        controller.getSubtitleTracks().flatMap { group ->
            (0 until group.length).map { index -> Triple(group, index, group.isTrackSelected(index)) }
        }
    }
    val offSelected = rows.none { it.third }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .padding(24.dp)
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(12.dp),
    ) {
        Text("Subtitles", style = RedSurfType.sectionTitle, color = TextPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        TvSurface(
            onClick = {
                controller.disableSubtitles()
                refreshTick++
            },
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            shape = RedSurfFocus.shape(8.dp),
            colors = RedSurfFocus.rowColors(),
            scale = RedSurfFocus.scale(),
            border = RedSurfFocus.border(),
            glow = RedSurfFocus.glow(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Off",
                    style = RedSurfType.rowTitle,
                    color = if (offSelected) TextPrimary else TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (offSelected) Text("Selected", style = RedSurfType.badge, color = TextPrimary)
            }
        }
        if (rows.isEmpty()) {
            Text("No subtitle tracks available", style = RedSurfType.rowSecondary, color = TextSecondary, modifier = Modifier.padding(8.dp))
        }
        rows.forEach { (group, trackIndex, isSelected) ->
            val format = group.getTrackFormat(trackIndex)
            val label = languageLabelOf(format, trackIndex)
            TvSurface(
                onClick = {
                    controller.selectSubtitleTrack(group, trackIndex)
                    refreshTick++
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RedSurfFocus.shape(8.dp),
                colors = RedSurfFocus.rowColors(),
                scale = RedSurfFocus.scale(),
                border = RedSurfFocus.border(),
                glow = RedSurfFocus.glow(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = RedSurfType.rowTitle,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) Text("Selected", style = RedSurfType.badge, color = TextPrimary)
                }
            }
        }
    }
}

private fun formatBitrate(bps: Long): String = "%.2f Mbps".format(bps / 1_000_000.0)
private fun formatBitrate(bps: Int): String = formatBitrate(bps.toLong())

/**
 * Every Xtream-imported channel's `streamId` already IS its full playback URL
 * (`server/live/user/pass/id.ts`, `MainViewModel.loadXtreamCodes`) - this pulls server/user/pass
 * back out of it rather than the app persisting the password a second time just for this screen.
 * Null for anything that doesn't match (M3U/Stalker channels have no fixed shape here - correctly
 * skipped, not guessed at).
 */
private fun parseXtreamCredentials(streamUrl: String): Triple<String, String, String>? {
    val match = Regex("^(https?://[^/]+)/live/([^/]+)/([^/]+)/").find(streamUrl) ?: return null
    val (server, user, pass) = match.destructured
    return Triple(server, user, pass)
}

/**
 * Decision 9/10's Video info screen, redesigned full-screen 2026-09-17 per direct user feedback
 * on the first pass ("that tiny box in the bottom right... think about the VLC player when it's
 * streaming video information" / "some live network data like speed, buffer size... technicals
 * about the provider themselves... 1/1 active connections"). A semi-transparent scrim over the
 * still-visible video, not a modal panel - the VLC reference the user pointed at.
 *
 * Provider connection count is fetched once per open (not polled - it's a live request to the
 * user's own provider, and decision 9 doesn't need it to be real-time), only for Xtream channels
 * (`parseXtreamCredentials` returns null for anything else) - omitted entirely on failure or for
 * non-Xtream playlists, the same "never a placeholder" rule as every other badge on this screen.
 */
@Composable
private fun VideoInfoOverlay(
    channel: ChannelEntity?,
    streamInfo: StreamInfo,
    networkStats: NetworkStats,
    repository: ChannelRepository,
    modifier: Modifier = Modifier,
) {
    var providerInfo by remember(channel?.streamId) { mutableStateOf<XtreamUserInfo?>(null) }
    LaunchedEffect(channel?.streamId) {
        providerInfo = null
        val ch = channel ?: return@LaunchedEffect
        val playlist = repository.getPlaylist(ch.playlistId) ?: return@LaunchedEffect
        if (playlist.type != "xtream") return@LaunchedEffect
        val (server, user, pass) = parseXtreamCredentials(ch.streamId) ?: return@LaunchedEffect
        providerInfo = XtreamApi.getUserInfo(server, user, pass, playlist.userAgent)
    }

    // Both the class and the literal pixel size, always (user request, 2026-09-17: "real
    // resolution size... whatever else could be helpful and advance") - unlike the zap-banner's
    // brief badges, this is the deep-dive screen, so the Appearance toggle only decides the zap
    // banner's own badge now, not what's available here.
    val streamRows = listOfNotNull(
        streamInfo.resolutionClass?.let { "Resolution" to it },
        streamInfo.rawResolution?.let { "Pixel size" to it },
        streamInfo.frameRate?.let { "Frame rate" to "$it FPS" },
        streamInfo.videoCodec?.let { "Video codec" to it },
        streamInfo.videoBitrateBps?.let { "Video bitrate" to formatBitrate(it) },
        streamInfo.audioCodec?.let { "Audio codec" to it },
        streamInfo.audioChannels?.let { "Audio channels" to it },
        streamInfo.audioBitrateBps?.let { "Audio bitrate" to formatBitrate(it) },
    )
    val networkRows = listOfNotNull(
        networkStats.bitrateEstimateBps?.let { "Network speed" to formatBitrate(it) },
        "Buffer" to "${networkStats.bufferedMs / 1000}s (${networkStats.bufferedPercentage}%)",
    )
    // Server host only, deliberately - not the full stream URL (it embeds the provider password,
    // and this is an on-screen overlay, not a copy-to-clipboard field; showing it plaintext isn't
    // worth the exposure for what it'd add here).
    val providerRows = buildList {
        parseXtreamCredentials(channel?.streamId ?: "")?.first?.let { add("Server" to it.removePrefix("https://").removePrefix("http://")) }
        providerInfo?.let { info ->
            if (info.activeConnections != null && info.maxConnections != null) {
                add("Connections" to "${info.activeConnections}/${info.maxConnections}")
            }
        }
    }

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.72f)).padding(48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(modifier = Modifier.width(420.dp)) {
            Text(channel?.name ?: "", style = RedSurfType.heroTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(20.dp))
            InfoSection("Stream", streamRows)
            if (networkRows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                InfoSection("Network", networkRows)
            }
            if (providerRows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                InfoSection("Provider", providerRows)
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, rows: List<Pair<String, String>>) {
    Text(title, style = RedSurfType.sectionTitle, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
    rows.forEach { (label, value) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = RedSurfType.rowSecondary, color = TextSecondary)
            // Thinner than rowTitle's Medium weight (user request, 2026-09-17) - this screen is
            // dense data, not a row of channel names competing for attention.
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
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
private fun PlayerInfoBlock(
    channel: ChannelEntity?,
    streamInfo: StreamInfo,
    showRawResolution: Boolean = false,
    modifier: Modifier = Modifier,
) {
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
            // BACKLOG_SWEEP.md #12: literal WxH instead of the derived SD/HD/FHD/4K class when the
            // Appearance toggle is on - StreamInfo already captures rawResolution unconditionally
            // (see its own doc comment), so this is purely which field the badge row reads.
            val badges = listOfNotNull(
                if (showRawResolution) streamInfo.rawResolution else streamInfo.resolutionClass,
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
