package com.redsurf.tv.ui.livetv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.R
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.EpgProgramEntity
import com.redsurf.tv.player.PreviewPlayerHost
import com.redsurf.tv.player.rememberPreviewPlayerController
import com.redsurf.tv.ui.player.PlayerScreen
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfDensity
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * `docs/plans/LIVE_TV_GUIDE_MERGE.md` - the real merge. Live TV and Guide are no longer two nav
 * pills toggling between two unrelated layouts (`guideMode`'s old two-branch split, PHASE_3.md
 * decision 3, is gone) - there is exactly one Live TV screen now: a hero preview band under the
 * nav-strip (TiviMate-parity info, `HeroPreviewBand` below), and categories + the EPG timeline
 * grid beneath it. This is what the user meant by "two different sections" even though both
 * `guideMode` branches already shared one composable - sharing a Kotlin function isn't the same
 * as feeling like one screen when the two branches render completely different layouts.
 *
 * The plain paged channel-list column (`ChannelsColumn`) is no longer used *here* - it's still a
 * real, separate composable used by the fullscreen player's own LEFT-edge channel-list overlay
 * (`ChannelListOverlay.kt`), untouched by this change.
 *
 * Browsing state survives entering/exiting fullscreen: the browse Column (hero band + categories
 * + grid) is always composed, as a sibling of the fullscreen overlay in one Box, never behind an
 * if/early-return that would skip composing it - the same Compose conditional-composition trap
 * documented at length in this file's git history (found live, 2026-09-12) applies here exactly
 * as it always has; nothing about the merge changes that discipline.
 */
@Composable
fun LiveTvScreen(
    viewModel: MainViewModel,
    onFullscreenChanged: (Boolean) -> Unit,
    // BACKLOG_SWEEP.md #8: lets AppShell exclude its own Home-jump BackHandler while real focus
    // is in the grid, mirroring the existing fullscreen mutual-exclusivity mechanism (see
    // AppShell.kt's own doc comment) rather than introducing a second, competing pattern.
    onChannelsFocusChanged: (Boolean) -> Unit = {},
    // BACKLOG_SWEEP.md #11/#12 - threaded straight through to PlayerScreen; LiveTvScreen has no
    // use for either value itself, it's just the composable in between AppShell (which owns
    // AppPreferences) and PlayerScreen (which actually reads them).
    blackScreenBetweenZaps: Boolean = false,
    // PREVIEW.md, 2026-09-20 - default true. When true, a single OK on the current-airing cell of
    // a focused channel starts it playing for real in the hero band instead of jumping straight
    // to fullscreen; a second OK on that same still-previewing channel promotes it. When false,
    // restores the original single-OK-jumps-straight-to-fullscreen behavior exactly (see
    // [openChannel] below).
    previewOnSelect: Boolean = true,
    showRawResolution: Boolean = false,
    // Hoisted to AppShell (found live, 2026-09-15, user report) - same conditional-composition
    // trap SettingsScreen's own selectedCategory already had to escape: this composable is torn
    // down and recomposed fresh every time `destination` switches away from Live TV and back, so
    // a plain `remember` here reset the category, the focused channel, and the recent-tiles row
    // to nothing on every re-entry instead of keeping where the user actually was. Plain
    // value/`onXChanged` hoisting (Kotlin local `var`s can't take custom accessors, unlike a
    // class property) - every assignment site below calls the matching `onXChanged` instead of
    // reassigning a local `var` directly; reads use the parameter as-is.
    selectedGroup: GroupKey? = null,
    onSelectedGroupChanged: (GroupKey?) -> Unit = {},
    focusedChannel: ChannelEntity? = null,
    onFocusedChannelChanged: (ChannelEntity?) -> Unit = {},
    // AGENTS.md backlog, corrected 2026-09-15 - "Auto-play last channel on launch." An explicit
    // one-shot signal from AppShell, not inferred from focusedChannel changing (which also fires
    // on a user's first ordinary browse-focus after a genuinely fresh launch, with nothing to
    // restore - inferring from that would wrongly auto-play whatever they merely focused). Only
    // ever set true once, by AppShell's own cold-launch restore effect; consumed back to false via
    // [onAutoPlayTriggerConsumed] so it can't refire later in the session.
    autoPlayTrigger: Boolean = false,
    onAutoPlayTriggerConsumed: () -> Unit = {},
    // AGENTS.md backlog, 2026-09-16: the cold-launch resume restore (above) sets `selectedGroup`/
    // `focusedChannel` as *data* and correctly pre-selects the right area, but nothing ever
    // claimed real D-pad focus onto that channel's row - the two existing focus-claim mechanisms
    // are both purely reactive, triggered by focus *arriving* from somewhere else or by *leaving*
    // fullscreen - neither ever fires on a cold launch, where nothing has focus yet at all. An
    // explicit one-shot trigger, same shape as [autoPlayTrigger] and for the same reason.
    claimInitialFocusTrigger: Boolean = false,
    onClaimInitialFocusTriggerConsumed: () -> Unit = {},
) {
    val groups by viewModel.repository.liveGroups().collectAsState(initial = emptyList())
    // PLAYER_ENGINEERING_BRIEF.md §6/§9 - resolved here, reactively, well before any fullscreen
    // entry is possible (the user has to browse/focus/press OK first), not inside PlayerScreen
    // itself - see PlayerScreen.kt's own doc comment for why the timing matters.
    val playlists by viewModel.repository.playlists().collectAsState(initial = emptyList())
    // PHASE_2.md decision 14's real table now, not the old in-memory stand-in - reactive, and
    // (unlike the stand-in) survives a real relaunch, not just switching destinations within one
    // session.
    val recentChannels by viewModel.repository.recentChannels().collectAsState(initial = emptyList())
    var isFullscreen by remember { mutableStateOf(false) }
    var channelsHasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    fun recordRecent(channel: ChannelEntity) {
        scope.launch { viewModel.repository.recordRecentChannel(channel.playlistId, channel.streamId) }
    }

    LaunchedEffect(groups) {
        if ((selectedGroup == null || groups.none { it.key() == selectedGroup }) && groups.isNotEmpty()) {
            onSelectedGroupChanged(groups.first().key())
        }
    }

    LaunchedEffect(channelsHasFocus) {
        onChannelsFocusChanged(channelsHasFocus)
    }

    // Debounced: a D-pad flying across the grid must not start a stream per cell it passes over.
    // Feeds the fullscreen player's own streamUrl once isFullscreen is true; every explicit
    // promotion path below also sets this directly, bypassing the debounce for a deliberate open.
    var previewUrl by remember { mutableStateOf<String?>(null) }

    // PREVIEW.md - the channel currently playing for real in the hero band (distinct from
    // [focusedChannel], which just tracks where the D-pad is; this only changes on an explicit
    // OK). Local `remember` state, not hoisted to AppShell like selectedGroup/focusedChannel are
    // - deliberately: PREVIEW.md point 6 wants this to persist across category switches *within*
    // Live TV but reset on leaving the screen entirely, which is exactly what plain `remember`
    // state already does here (this whole composable is torn down and recomposed fresh on
    // destination change).
    var previewingChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    // autoPlayTrigger/claimInitialFocusTrigger both want "go straight to fullscreen," never the
    // two-step preview - they're "resume where I left off," not a fresh browse-and-select action
    // (PREVIEW.md point 5).
    fun promoteToFullscreen(channel: ChannelEntity) {
        onFocusedChannelChanged(channel)
        previewUrl = channel.streamId
        recordRecent(channel)
        previewingChannel = null
        isFullscreen = true
        onFullscreenChanged(true)
    }

    // The grid's own onTuneChannel for its current-airing cell (LIVE_TV_GUIDE_MERGE.md - the
    // grid is now the only browse surface, so it drives the same two-step preview the old plain
    // list used to). PREVIEW.md's two-step: first OK previews and stays in browse; a second OK on
    // that exact still-previewing channel promotes. OK on a *different* channel while one is
    // already previewing swaps the preview immediately, never requiring a step back out first
    // (PREVIEW.md point 3, confirmed user behavior, not a guess).
    fun openChannel(channel: ChannelEntity) {
        if (!previewOnSelect) {
            promoteToFullscreen(channel)
            return
        }
        val alreadyPreviewing = previewingChannel?.let {
            it.playlistId == channel.playlistId && it.streamId == channel.streamId
        } == true
        if (alreadyPreviewing) {
            promoteToFullscreen(channel)
        } else {
            onFocusedChannelChanged(channel)
            previewingChannel = channel
        }
    }

    // Auto-play last channel on launch, the trigger's actual effect - see the parameter doc
    // above for why this reacts to the dedicated trigger, not to focusedChannel itself.
    LaunchedEffect(autoPlayTrigger) {
        if (autoPlayTrigger) {
            val channel = focusedChannel
            if (channel != null) {
                previewUrl = channel.streamId
                recordRecent(channel)
                previewingChannel = null
                isFullscreen = true
                onFullscreenChanged(true)
            }
            onAutoPlayTriggerConsumed()
        }
    }

    LaunchedEffect(focusedChannel) {
        val channel = focusedChannel
        if (channel == null) {
            previewUrl = null
        } else {
            delay(500)
            previewUrl = channel.streamId
        }
    }

    // Debounced the same way, and for the same reason (found live, 2026-09-12 - reported as
    // "choppy," "loss of scrolling," "random behavior" holding UP/DOWN through Categories):
    // GroupsColumn's own highlight tracks selectedGroup instantly so the UI still feels
    // responsive, but actually re-querying the grid's channel/EPG data is real work that a fast
    // D-pad key-repeat was triggering once per row flown over. queriedGroup is what actually
    // drives the grid; only the group the user settles on for 200ms gets a real query.
    var queriedGroup by remember { mutableStateOf<GroupKey?>(null) }
    LaunchedEffect(selectedGroup) {
        val group = selectedGroup
        if (group == null) {
            queriedGroup = null
        } else {
            delay(200)
            queriedGroup = group
        }
    }

    // EPG_GRID_REDESIGN.md G.7 - one ticking clock for the whole screen, not a `remember`ed
    // timestamp that silently went stale (the previous build's now-line, "current" cell, and
    // "N min remaining" all decayed the longer the screen stayed open). Two recompositions a
    // minute; at ~84dp per half-hour that moves the now-line ~1.4dp per tick.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    // G.2 - the grid's window starts on the previous :00/:30, and only changes value at those
    // boundaries even though `now` ticks every 30s (same Long → nothing keyed on it recomposes).
    val windowStart = remember(now) { floorToHalfHour(now) }
    val windowEnd = windowStart + WINDOW_MINUTES * MINUTE_MS

    // The grid's own data (PHASE_3.md decision 4) - now unconditional, the grid is the only
    // browse surface. Non-paged and debounced the same 200ms as queriedGroup, for the same
    // reason - a fast D-pad flight through Categories shouldn't fire one grid query per row flown
    // over. Re-queried when the window re-snaps (every 30 minutes), so the far edge stays filled.
    var gridChannels by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var gridPrograms by remember { mutableStateOf<Map<String, List<EpgProgramEntity>>>(emptyMap()) }
    LaunchedEffect(queriedGroup, windowStart) {
        val group = queriedGroup
        if (group == null) {
            gridChannels = emptyList()
            gridPrograms = emptyMap()
            return@LaunchedEffect
        }
        val channelsInGroup = viewModel.repository.channelsInGroup(group.playlistId, group.groupName)
        gridChannels = channelsInGroup
        val epgIds = channelsInGroup.mapNotNull { it.epgChannelId?.takeIf { id -> id.isNotBlank() } }.distinct()
        gridPrograms = if (epgIds.isEmpty()) {
            emptyMap()
        } else {
            viewModel.repository.programsForChannels(group.playlistId, epgIds, windowStart, windowEnd)
                .groupBy { it.channelEpgId }
        }
    }

    // LIVE_TV_GUIDE_MERGE.md M.3 - the hero band's own programme lookup for the *previewing*
    // channel, independent of queriedGroup: PREVIEW.md point 6 means [previewingChannel] can
    // belong to a different category than the one browsed (preview persists across category
    // switches), so it can't read from [gridPrograms]. Fetched once per channel/window; which
    // one is "current" is derived from the ticking clock below, so it rolls over on its own.
    var heroProgrammes by remember { mutableStateOf<List<EpgProgramEntity>>(emptyList()) }
    LaunchedEffect(previewingChannel, windowStart) {
        val channel = previewingChannel
        val epgId = channel?.epgChannelId?.takeIf { it.isNotBlank() }
        heroProgrammes = if (channel == null || epgId == null) {
            emptyList()
        } else {
            viewModel.repository.programsForChannels(channel.playlistId, listOf(epgId), windowStart, windowEnd)
        }
    }
    // G.3 - "browsing" detection for the hero band's collapsed state: a burst of cursor moves
    // closer than 700ms apart that has lasted 1.2s means the user is scanning, not reading - the
    // band collapses to its 56dp bar and the grid gains rows; 800ms after the last move it comes
    // back with the channel they landed on. Previewing always keeps it expanded (video's in it).
    var isBrowsing by remember { mutableStateOf(false) }
    var browseStartedAt by remember { mutableLongStateOf(0L) }
    var lastCursorMoveAt by remember { mutableLongStateOf(0L) }
    // TiviMate parity: the hero band's text follows the D-pad cursor - the channel and the
    // specific slot under it - not just the OK'd channel. Kept separate from [focusedChannel],
    // which carries restore/resume semantics (set on OK, zap, cold-launch) that a mere cursor
    // pass-over must never overwrite. Cleared when focus leaves the grid so the band falls back
    // to the category bar while browsing Categories.
    var cursorChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var cursorSlot by remember { mutableStateOf<EpgSlot?>(null) }
    fun onCursorChanged(channel: ChannelEntity, slot: EpgSlot) {
        cursorChannel = channel
        cursorSlot = slot
        val t = System.currentTimeMillis()
        if (t - lastCursorMoveAt > 700L) browseStartedAt = t
        lastCursorMoveAt = t
        if (t - browseStartedAt >= 1_200L) isBrowsing = true
    }
    LaunchedEffect(lastCursorMoveAt) {
        if (lastCursorMoveAt == 0L) return@LaunchedEffect
        delay(800)
        isBrowsing = false
    }
    val heroChannel = cursorChannel ?: focusedChannel
    val heroExpanded = previewingChannel != null || (heroChannel != null && !isBrowsing)

    // What the hero band's text shows (see [cursorChannel] below): the programme under the
    // cursor when the cursor is on a real listing; else the cursor channel's on-now programme
    // from the grid's own data; else, with focus off the grid, the previewing channel's on-now.
    val heroProgramme = remember(previewingChannel, cursorChannel, cursorSlot, focusedChannel, heroProgrammes, gridPrograms, now) {
        val slot = cursorSlot
        when {
            cursorChannel != null && slot is AiredSlot -> slot.programme
            cursorChannel != null -> gridPrograms[cursorChannel?.epgChannelId].orEmpty()
                .firstOrNull { now >= it.startTime && now < it.endTime }
            previewingChannel != null -> heroProgrammes.firstOrNull { now >= it.startTime && now < it.endTime }
            else -> gridPrograms[focusedChannel?.epgChannelId].orEmpty()
                .firstOrNull { now >= it.startTime && now < it.endTime }
        }
    }


    val fullscreenFocus = remember { FocusRequester() }
    // The grid's own entry/return point - the sole "return to browse" focus target now that the
    // plain list (and its exact-channel-aware channelReturnFocus) is gone from this screen.
    // Coarser on purpose (lands on the grid's first row, not the exact channel just watched) -
    // a real, logged scope trim carried over from PHASE_3.md's own EpgGridColumn doc comment,
    // now applying more broadly since the grid is the only browse surface. What matters per this
    // project's own state/focus discipline rule is that this never falls through to nothing and
    // lets focus land on the NavStrip by accident; landing one row off is a much smaller miss
    // than that. Worth a real follow-up (give the grid a focusedChannelId-aware row match, the
    // same pattern ChannelsColumn already has) - not attempted in this already-large pass.
    val gridFocus = remember { FocusRequester() }
    LaunchedEffect(gridChannels) {
        if (gridChannels.isNotEmpty()) {
            repeat(10) {
                delay(150)
                if (runCatching { gridFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            // User-found bug, 2026-09-18: "Auto-play last channel on launch" jumps straight into
            // fullscreen with no browse-screen detour, and unlike every other focus-claim in this
            // codebase, this one call had no `runCatching`/retry at all. Same bounded retry shape
            // as the cold-launch channel-focus fix (PHASE_2.md, 2026-09-17).
            repeat(20) {
                delay(150)
                if (runCatching { fullscreenFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        } else if (focusedChannel != null) {
            delay(100)
            runCatching { gridFocus.requestFocus() }
        }
    }

    // Cold-launch resume: claim real focus (see [claimInitialFocusTrigger]'s own doc comment for
    // why the two mechanisms above never do this on their own).
    LaunchedEffect(claimInitialFocusTrigger) {
        if (claimInitialFocusTrigger) {
            if (focusedChannel != null) {
                var claimed = false
                repeat(20) {
                    if (claimed) return@repeat
                    delay(300)
                    claimed = runCatching { gridFocus.requestFocus() }.isSuccess
                }
            }
            onClaimInitialFocusTriggerConsumed()
        }
    }

    // Back retraces this screen's own path instead of flat-hopping straight to Home
    // (BACKLOG_SWEEP.md #8, user-verified against real TiviMate 2026-09-14): the grid -> back to
    // Categories first, Home only on the next Back from there.
    BackHandler(enabled = !isFullscreen && channelsHasFocus) {
        focusManager.moveFocus(FocusDirection.Left)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Always composed (see class doc above) - never torn down by the fullscreen overlay that
        // sits on top of it.
        Column(modifier = Modifier.fillMaxSize()) {
            // LIVE_TV_GUIDE_MERGE.md M.2 - full-width, directly under the nav-strip (AppShell
            // renders that above this whole composable). This is the one deliberate RedSurf-vs-
            // TiviMate placement difference named by the user: TiviMate's own top-level nav lives
            // hidden in a left panel, so its hero band can sit at the very top of the screen;
            // RedSurf's nav-strip lives at the top instead, so the hero band sits directly below
            // it - same prominence, same position relative to everything else.
            val selectedGroupInfo = remember(groups, selectedGroup) { groups.firstOrNull { it.key() == selectedGroup } }
            HeroPreviewBand(
                focusedChannel = heroChannel,
                previewingChannel = previewingChannel,
                programme = heroProgramme,
                now = now,
                expanded = heroExpanded,
                groupLabel = selectedGroupInfo?.let { formatGroupName(it.groupName) },
                groupCount = selectedGroupInfo?.count,
                playlistUserAgent = remember(previewingChannel?.playlistId, playlists) {
                    playlists.firstOrNull { it.id == previewingChannel?.playlistId }?.userAgent
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = RedSurfDensity.ColumnGap),
            )

            // EPG_GRID_REDESIGN.md G.2 - a fixed categories column and a tighter gutter, so the
            // grid's timeline viewport (and therefore its minute scale) is as wide as the canvas
            // allows rather than a proportional share.
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(RedSurfDensity.ColumnGap)) {
                GroupsColumn(
                    groups = groups,
                    selectedGroup = selectedGroup,
                    onGroupFocused = { group ->
                        if (group != selectedGroup) {
                            onSelectedGroupChanged(group)
                            onFocusedChannelChanged(null)
                        }
                    },
                    modifier = Modifier.width(RedSurfDensity.CategoriesWidth),
                )

                EpgGridColumn(
                    channels = gridChannels,
                    programsByChannel = gridPrograms,
                    groupName = selectedGroup?.groupName,
                    now = now,
                    windowStart = windowStart,
                    onTuneChannel = { channel -> openChannel(channel) },
                    firstCellFocusRequester = gridFocus,
                    onCursorChanged = { channel, slot -> onCursorChanged(channel, slot) },
                    onFocusStateChanged = { hasFocus ->
                        channelsHasFocus = hasFocus
                        if (!hasFocus) {
                            cursorChannel = null
                            cursorSlot = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (isFullscreen) {
            // Extracted to ui/player/PlayerScreen.kt (PHASE_2.md #2.1) - it now owns the overlay
            // state machine and all Back handling while fullscreen, calling onExitFullscreen only
            // once there's nothing left to peel (decision 4).
            val breadcrumb = remember(focusedChannel, groups) {
                val channel = focusedChannel
                val info = channel?.let { c ->
                    groups.firstOrNull { it.playlistId == c.playlistId && it.groupName == c.groupName }
                }
                if (info != null) "${info.playlistName} › ${formatGroupName(info.groupName)}" else ""
            }
            PlayerScreen(
                streamUrl = previewUrl,
                focusRequester = fullscreenFocus,
                onExitFullscreen = {
                    isFullscreen = false
                    onFullscreenChanged(false)
                },
                // LIVE_TV_GUIDE_MERGE.md M.1 - the player's "Guide" quick-action used to switch
                // AppShell's destination to a separate Guide pill; that pill no longer exists, and
                // this screen's browse view already always shows the grid, so the action is just
                // "exit fullscreen" now - PlayerScreen's own default (onOpenGuide = onExitFullscreen)
                // already does exactly that; no override needed here any more.
                currentChannel = focusedChannel,
                repository = viewModel.repository,
                onChannelChanged = { channel ->
                    onFocusedChannelChanged(channel)
                    // Bypass the browse-debounce, same reason as openChannel above: zapping is
                    // a deliberate action, not a D-pad fly-by.
                    previewUrl = channel.streamId
                    recordRecent(channel)
                    // BACKLOG_SWEEP.md #7: a channel change originating from inside the player
                    // must also move selectedGroup, not just focusedChannel - otherwise Categories
                    // keeps whatever group was selected before fullscreen, and Back after a
                    // cross-category tile pick lands on the wrong category.
                    val newGroup = GroupKey(channel.playlistId, channel.groupName)
                    if (newGroup != selectedGroup) onSelectedGroupChanged(newGroup)
                },
                breadcrumb = breadcrumb,
                recentChannels = recentChannels.filter { it.streamId != focusedChannel?.streamId },
                blackScreenBetweenZaps = blackScreenBetweenZaps,
                showRawResolution = showRawResolution,
                playlistUserAgent = remember(focusedChannel?.playlistId, playlists) {
                    playlists.firstOrNull { it.id == focusedChannel?.playlistId }?.userAgent
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun isSameChannel(a: ChannelEntity?, b: ChannelEntity?): Boolean =
    a != null && b != null && a.playlistId == b.playlistId && a.streamId == b.streamId

private val heroTimeFormat = SimpleDateFormat("h:mm a", Locale.US)
private val heroDateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)

/**
 * EPG_GRID_REDESIGN.md G.3 (consultation Part 6, "The Ledge") - the hero band, rebuilt from the
 * 190dp version whose empty state was a grey rectangle and a grey play triangle occupying ~29% of
 * the screen. Two heights, one `animateDpAsState`:
 *
 * - **Expanded (136dp)** while a channel is previewing (video's in it, always) or merely focused
 *   and the user isn't mid-scan: the channel's real logo fills the thumbnail slot until a
 *   preview replaces it (zero new network cost - the grid row already loaded it), the channel
 *   name with the live crescent beside it when it's the one playing, the on-now programme with
 *   a ticking progress row, a two-line description, the OK hint. The grey favourite star sits
 *   inline beside the name (no real Favorites feature yet - `TELEPORT_MENU.md` backlog; grey-row
 *   convention as a glyph) rather than in its own full-height column.
 * - **Collapsed (56dp)** when nothing's focused or the user is actively scanning the grid: the
 *   mark, the selected category and its count, the OK hint at its right size, and a live clock -
 *   real information earning its height, never an empty box. With the nav-strip's auto-hide the
 *   category label here is the only thing on screen saying where you are.
 */
@Composable
private fun HeroPreviewBand(
    focusedChannel: ChannelEntity?,
    previewingChannel: ChannelEntity?,
    programme: EpgProgramEntity?,
    now: Long,
    expanded: Boolean,
    groupLabel: String?,
    groupCount: Int?,
    playlistUserAgent: String?,
    modifier: Modifier = Modifier,
) {
    val height by animateDpAsState(
        targetValue = if (expanded) RedSurfDensity.HeroExpanded else RedSurfDensity.HeroCollapsed,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "heroHeight",
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(RedSurfDensity.PanelRadius))
            .background(Surface),
    ) {
        if (expanded) {
            ExpandedHero(focusedChannel, previewingChannel, programme, now, playlistUserAgent)
        } else {
            CollapsedHero(groupLabel, groupCount, now)
        }
    }
}

@Composable
private fun CollapsedHero(groupLabel: String?, groupCount: Int?, now: Long) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painter = painterResource(R.drawable.ic_mark), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            groupLabel ?: "Live TV",
            style = RedSurfType.gridChannel,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (groupCount != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("$groupCount channels", style = RedSurfType.gridMeta, color = TextSecondary, maxLines = 1)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Press OK to preview  ·  OK again for fullscreen",
            style = RedSurfType.gridMeta,
            color = TextSecondary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(heroTimeFormat.format(Date(now)), style = RedSurfType.gridChannel, color = TextPrimary, maxLines = 1)
        Spacer(modifier = Modifier.width(8.dp))
        Text(heroDateFormat.format(Date(now)), style = RedSurfType.gridMeta, color = TextSecondary, maxLines = 1)
    }
}

@Composable
private fun ExpandedHero(
    focusedChannel: ChannelEntity?,
    previewingChannel: ChannelEntity?,
    programme: EpgProgramEntity?,
    now: Long,
    playlistUserAgent: String?,
) {
    // Re-armed per distinct previewing channel, so switching the preview to a different channel
    // always gets a fresh attempt rather than staying stuck failed.
    var previewFailed by remember(previewingChannel?.streamId, previewingChannel?.playlistId) {
        mutableStateOf(false)
    }
    val showingRealPreview = previewingChannel != null && !previewFailed
    // Text follows the cursor (or the OK'd channel), video follows what's previewing - TiviMate's
    // own split: the thumbnail is clearly "playing," the text is clearly "what you're looking at."
    val displayChannel = focusedChannel ?: previewingChannel

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(RedSurfDensity.PanelRadius))
                .background(SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (showingRealPreview) {
                val previewController = rememberPreviewPlayerController(playlistUserAgent)
                val hasError by previewController.hasError.collectAsState()
                LaunchedEffect(hasError) { if (hasError) previewFailed = true }
                PreviewPlayerHost(
                    controller = previewController,
                    streamUrl = previewingChannel!!.streamId,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(RedSurfDensity.PanelRadius)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("LIVE", style = RedSurfType.badge, color = TextPrimary)
                }
            } else if (displayChannel != null) {
                val icon = displayChannel.streamIcon
                if (icon.isNullOrBlank()) {
                    Text(
                        displayChannel.name.take(1).uppercase(),
                        style = RedSurfType.heroTitle.copy(fontSize = 32.sp),
                        color = TextSecondary.copy(alpha = 0.5f),
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        error = {
                            Text(
                                displayChannel.name.take(1).uppercase(),
                                style = RedSurfType.heroTitle.copy(fontSize = 32.sp),
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        loading = { },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            if (displayChannel == null) return@Column
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayChannel.name,
                    style = RedSurfType.heroTitle.copy(fontSize = 20.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSameChannel(previewingChannel, displayChannel)) {
                    Spacer(modifier = Modifier.width(8.dp))
                    LiveCrescentGlyph(size = 14.dp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                // Grey favourite star - no real Favorites feature exists yet (TELEPORT_MENU.md
                // backlog): a plain decorative Icon, nothing to press, the grey-row convention.
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    formatGroupName(displayChannel.groupName),
                    style = RedSurfType.gridMeta,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            if (programme != null) {
                Text(
                    programme.title.ifBlank { "Untitled" },
                    style = RedSurfType.rowTitle,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgrammeProgressRow(programme, now)
                Text(
                    programme.description.ifBlank { "No description available." },
                    style = RedSurfType.rowSecondary,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text("No schedule information", style = RedSurfType.rowSecondary, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(2.dp))
            val hint = if (isSameChannel(previewingChannel, focusedChannel)) {
                "Press OK again to open fullscreen"
            } else {
                "Press OK to preview this channel"
            }
            Text(hint, style = RedSurfType.gridMeta, color = Accent, maxLines = 1)
        }
    }
}

/** The red progress track + "N min remaining" (TiviMate parity, `RedThemedEPGLiveTVScreen.jpg`),
 * driven by the screen's ticking clock (G.7) rather than a timestamp captured once at
 * composition, which is how the previous version quietly stopped counting down. */
@Composable
private fun ProgrammeProgressRow(programme: EpgProgramEntity, now: Long) {
    val total = (programme.endTime - programme.startTime).coerceAtLeast(1L)
    val elapsed = (now - programme.startTime).coerceIn(0L, total)
    val fraction = elapsed.toFloat() / total.toFloat()
    val minRemaining = ((programme.endTime - now) / 60_000L).coerceAtLeast(0L)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${heroTimeFormat.format(Date(programme.startTime))} – ${heroTimeFormat.format(Date(programme.endTime))}",
            style = RedSurfType.rowSecondary,
            color = TextSecondary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TextSecondary.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(Accent),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text("$minRemaining min remaining", style = RedSurfType.rowSecondary, color = TextSecondary, maxLines = 1)
    }
}
