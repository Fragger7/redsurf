package com.redsurf.tv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Creates and remembers one [PlayerController] for as long as the caller stays in composition
 * (PHASE_1.md #1.5, Decisions #5 - unchanged lifetime from before the hoist, see
 * PLAYER_ENGINEERING_BRIEF.md §3.3: reuse within a fullscreen session, recreate across sessions).
 * Wires the Activity window's AFR mode application, the app's own pause/resume-on-background
 * behavior, and release-on-dispose - the pieces of the old `PlayerHost` that are genuinely
 * Compose/Activity concerns, not player-engine ones, so they stay here rather than moving into
 * [PlayerController] itself.
 */
@Composable
fun rememberPlayerController(playlistUserAgent: String? = null): PlayerController {
    val context = LocalContext.current
    // Deliberately keyed on nothing but Unit (brief §3.3: one controller per fullscreen session,
    // never recreated within it) - [playlistUserAgent] is usually resolved from an async Room
    // lookup by the caller, which can land a frame or two after this composable's first
    // composition; keying `remember` on it would tear down and rebuild the whole ExoPlayer the
    // instant that lookup resolves. Whatever value is current on the *first* call is what this
    // session's data source uses - matches the pre-hoist behavior's own risk profile (which had
    // no per-playlist User-Agent support for the media path at all), just narrower now.
    val controller = remember { PlayerController(context, playlistUserAgent) }

    DisposableEffect(controller) {
        val activity = context.findActivity()
        controller.onAfrModeFound = { modeId ->
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                preferredDisplayModeId = modeId
            }
        }
        onDispose { controller.release() }
    }

    // Home button during playback left audio running until the app was force-closed (found
    // live, 2026-09-12) - Compose composition doesn't track the Activity going to the
    // background on its own, so nothing told ExoPlayer to stop. This app has no background-
    // playback feature (no MediaSession, no foreground service) - pause is the correct,
    // conservative behavior here, not a workaround. Resumes automatically on return, from
    // wherever the buffer left off, since pause() (unlike release()) doesn't drop position.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> controller.exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return controller
}

/**
 * The thin `PlayerView` binding (PLAYER_ENGINEERING_BRIEF.md §9's hoist) - [controller] owns the
 * `ExoPlayer` instance, track selection, AFR, error handling and the stall watchdog; this
 * composable's only remaining jobs are rendering it, driving zaps via [streamUrl], and the
 * black-screen-between-zaps overlay (a genuinely Compose/View concern - an opaque layer drawn on
 * top of the video surface - not something that belongs inside the engine layer).
 *
 * [fullscreen] enables AFR (Decisions #6 - never mid-scroll, only when actually watching).
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerHost(
    controller: PlayerController,
    streamUrl: String?,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    // BACKLOG_SWEEP.md #11 (Settings -> Playback -> "Black screen between zaps", AppPreferences-
    // backed) - false (default) is this composable's existing "no-black-screen zapping" trick,
    // unchanged; true skips it, matching the old-cable-box behavior the user asked for.
    blackScreenBetweenZaps: Boolean = false,
) {
    val exoPlayer = controller.exoPlayer
    val context = LocalContext.current

    LaunchedEffect(fullscreen, controller) {
        controller.setFullscreen(fullscreen)
    }

    // Real black-screen-between-zaps (found not-working live, 2026-09-15 - user compared
    // directly against TiviMate: RedSurf showed a "smoky gradient" mid-zap, not an immediate hard
    // cut). Root cause: `setKeepContentOnPlayerReset(false)` below only stops the *old* frame
    // from lingering - it says nothing about the *new* stream's first few frames, which on a live
    // IPTV source are often blocky/smeared while the decoder syncs to a keyframe. Relying on
    // ExoPlayer's own reset/shutter timing gives no control over that window at all. This state,
    // not the shutter, is now the actual black screen: a real opaque Compose layer, independent
    // of the video surface, held from the moment a zap is initiated until this player confirms a
    // frame of the *new* stream actually rendered ([Player.Listener.onRenderedFirstFrame], via
    // [PlayerController.streamInfo] changing - see below).
    var showBlackOverlay by remember { mutableStateOf(false) }

    val lastUrl = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(streamUrl, blackScreenBetweenZaps, controller) {
        if (streamUrl != null && streamUrl != lastUrl.value) {
            // Only between zaps, not the very first tune-in this session (lastUrl.value == null)
            // - matches the setting's own name, and the screen is already blank before a first
            // frame ever arrives so there's nothing to hide.
            if (blackScreenBetweenZaps && lastUrl.value != null) showBlackOverlay = true
            controller.play(streamUrl)
            lastUrl.value = streamUrl
        }
    }

    // Belt-and-suspenders timeout: if the new stream never renders a frame (dead channel, no
    // signal), don't leave the user staring at permanent black with no explanation - that would
    // be a worse outcome than today's behavior, not a wash. Cleared normally by the streamInfo
    // effect below long before this fires on a healthy stream.
    LaunchedEffect(showBlackOverlay) {
        if (showBlackOverlay) {
            delay(5_000)
            showBlackOverlay = false
        }
    }

    // The real "lift the black overlay" signal - see [PlayerController.firstFrameRenderedTick]'s
    // own doc comment for why this is a counter, not the streamInfo content itself.
    val firstFrameTick by controller.firstFrameRenderedTick.collectAsState()
    LaunchedEffect(firstFrameTick) {
        showBlackOverlay = false
    }

    // Decision 9's Aspect action (#2.3) - the View itself lives here, one layer away from the
    // controller that owns the state it cycles through.
    val resizeMode by controller.resizeMode.collectAsState()

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                // Qualified `this.` - found by the compiler, not guessed: an unqualified
                // `resizeMode` here resolved to the outer `val resizeMode` (decision 9's Aspect
                // state, line 163) instead of this View's own settable property, once that val
                // was added to this scope.
                this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // No-black-screen zapping (PHASE_2.md #2.2, decision 13,
                // PRODUCT_VISION.md #3's "black screen minimizer") by default: hold the outgoing
                // channel's last frame - on a solid black shutter, never a stale video frame from
                // whatever was on screen even earlier - until the next stream's first frame is
                // ready, instead of clearing to a blank/default surface the instant media is
                // reset. [blackScreenBetweenZaps] (BACKLOG_SWEEP.md #11) inverts this.
                setKeepContentOnPlayerReset(!blackScreenBetweenZaps)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                // Screensaver/screen-off kicking in mid-playback (found live, 2026-09-12) -
                // decoding/rendering video isn't "user activity" as far as Android's idle timer
                // is concerned, so the OS has no reason not to sleep the screen. This is the
                // standard fix (same as ExoPlayer's own demo app): tied to the View, so it goes
                // away on its own when this View is torn down on exiting fullscreen - no
                // separate cleanup needed, unlike a Window-level flag.
                keepScreenOn = true
            }
        },
        update = { view ->
            view.setKeepContentOnPlayerReset(!blackScreenBetweenZaps)
            view.resizeMode = resizeMode
        },
        modifier = modifier.fillMaxSize(),
    )

    // The real black screen (see showBlackOverlay's doc comment above) - an immediate, hard cut
    // to solid black, no fade/crossfade, matching what was asked for ("just an immediate insert
    // of a full black screen until the next channel loads"). Drawn on top of the AndroidView
    // above, same size, so it fully occludes any in-progress decode artifacts underneath.
    if (showBlackOverlay) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
    }
}
