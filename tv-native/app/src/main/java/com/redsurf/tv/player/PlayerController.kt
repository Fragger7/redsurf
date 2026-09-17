package com.redsurf.tv.player

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.redsurf.tv.network.IptvNetworkModule
import com.redsurf.tv.player.tracks.TrackManager
import com.redsurf.tv.player.tuning.AfrManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "PlayerController"

/**
 * The live stream's current characteristics, for the zap-banner/info-block badges
 * (PHASE_2.md #2.2, decision 7). A field is null - never a placeholder value - whenever ExoPlayer
 * hasn't reported it yet; decision 7 is explicit that an unknown badge is omitted, not shown
 * empty. Moved here from PlayerHost.kt with the PlayerController hoist
 * (PLAYER_ENGINEERING_BRIEF.md §9) - this is where the format-derivation logic now lives.
 */
data class StreamInfo(
    val resolutionClass: String? = null, // "SD" | "HD" | "FHD" | "4K"
    val rawResolution: String? = null, // "1920x1080" - user request, 2026-09-12: a badge showing
    // the actual detected pixel resolution as an alternative to the SD/HD/FHD/4K class, toggled
    // in Settings. Captured now since it's free alongside resolutionClass; the toggle itself
    // waits on Settings having a real place to put it (AGENTS.md backlog).
    val frameRate: Int? = null,
    val audioChannels: String? = null, // "STEREO" | "5.1" | "N ch"
    val audioCodec: String? = null, // "AAC" | "AC3" | "EAC3" | "MP3"
    val videoCodec: String? = null, // "H.264" | "H.265" | "VP9" - not in decision 7's badge list,
    // captured anyway since it's free here and decision 9's "Video info" picker wants it later.
)

private fun resolutionClassOf(height: Int): String? = when {
    height <= 0 -> null
    height < 720 -> "SD"
    height < 1080 -> "HD"
    height < 2160 -> "FHD"
    else -> "4K"
}

private fun audioChannelsLabelOf(channelCount: Int): String? = when {
    channelCount <= 0 -> null
    channelCount == 2 -> "STEREO"
    channelCount == 6 -> "5.1"
    else -> "$channelCount ch"
}

private fun audioCodecOf(mimeType: String?): String? = when (mimeType) {
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "EAC3"
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
    else -> null
}

private fun videoCodecOf(mimeType: String?): String? = when (mimeType) {
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_H265 -> "H.265"
    MimeTypes.VIDEO_VP9 -> "VP9"
    else -> null
}

/**
 * Owns the ExoPlayer instance and everything around it, hoisted out of the Compose layer
 * (PLAYER_ENGINEERING_BRIEF.md §9) so `PlayerScreen`'s Actions row (PHASE_2.md #2.3) can reach
 * `TrackManager` - previously constructed and immediately discarded inside `PlayerHost`'s
 * `remember {}`, unreachable from anywhere else. `PlayerHost` is now a thin `AndroidView` binding
 * to [exoPlayer]; this class is where the brief's engine-level decisions actually live.
 *
 * One instance per fullscreen session (brief §3.3's "keep exactly this lifetime" - reuse within a
 * session via [play], recreate across sessions via [release] + a fresh instance). Not a
 * `@Composable` itself - constructed and remembered by [rememberPlayerController].
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerController(
    context: Context,
    playlistUserAgent: String? = null,
) {
    // Main.immediate, not the default background dispatcher (user-found crash, 2026-09-17):
    // every retry path below (the stall watchdog, error backoff, the 456 race retry) eventually
    // calls exoPlayer.prepare(), and ExoPlayer hard-crashes if that happens off its own creation
    // thread ("Player is accessed on the wrong thread") - confirmed via the device's own crash
    // dropbox, not guessed. A bare `CoroutineScope(SupervisorJob())` defaults to
    // `Dispatchers.Default` (a background pool), which every one of those calls was silently
    // violating.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val trackManager = TrackManager(context)

    /**
     * §2.2 (renderer fallback) + §2.6 (TS extractor flag) + §3.1 (buffer byte ceiling) + §2.4
     * (audio attributes/focus) + §6/§9 (per-playlist User-Agent threaded into the *media* data
     * source, not just the API one - previously `PlayerHost` called
     * `IptvNetworkModule.getDataSourceFactory()` with no argument, so a playlist with a custom UA
     * got the global one for its actual stream requests while its API requests got the right one).
     */
    val exoPlayer: ExoPlayer = run {
        val dataSourceFactory = IptvNetworkModule.getDataSourceFactory(playlistUserAgent)

        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val renderersFactory = DefaultRenderersFactory(context)
            // ON, never PREFER (brief §2.2) - platform MediaCodec decoders (and AC3/E-AC3 HDMI
            // passthrough) stay first choice; extension renderers are only reached when the
            // platform has nothing, so this can never regress hardware-decode-first performance.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            // The cheapest real compatibility win available (brief §2.2): when the primary
            // decoder fails to init or throws, try the next one instead of ending playback.
            .setEnableDecoderFallback(true)

        // §3.1: the time-based buffer values were already reasonable and are pending #2.6's real
        // measurement before being retuned further; the two changes made here don't need that
        // measurement first. bufferForPlaybackAfterRebufferMs 1500->2500 - a rebuffer means the
        // network just failed once already, so resuming after 1.5s usually rebuffers again within
        // seconds; trading 1s once against a rebuffer loop is the right trade. targetBufferBytes
        // was unset, which left DefaultLoadControl's own default budget (~125MB) as the effective
        // ceiling on a device with only ~449MB free - ~6x too loose to be a real ceiling at all.
        // If maxBufferMs is ever raised, this byte ceiling must rise with it.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_500, 15_000, 500, 2_500)
            .setTargetBufferBytes(20 * 1024 * 1024)
            .build()

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackManager.trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
            .apply { playWhenReady = true }
    }

    // AFR wiring, unchanged in shape from the pre-hoist PlayerHost (brief §5's full rebuild is
    // explicitly P2, not this pass) - just relocated here so it lives alongside the player it
    // controls instead of inside the Compose layer. [onAfrModeFound] is set by the caller
    // (PlayerHost) to apply the mode onto the Activity's Window, which is a platform/Compose
    // concern this controller shouldn't reach into directly.
    private val afrManager = AfrManager(context, exoPlayer)
    var onAfrModeFound: ((Int) -> Unit)?
        get() = afrManager.onModeFound
        set(value) { afrManager.onModeFound = value }

    fun setFullscreen(enabled: Boolean) {
        afrManager.isEnabled = enabled
        if (!enabled) afrManager.restoreOriginalMode()
    }

    // PHASE_2.md decision 9's Actions row (#2.3) - the brief's own note that this needed
    // "TrackManager access inside PlayerScreen and a new command path into PlayerHost" is exactly
    // what this hoist was for. Thin wrappers rather than exposing `trackManager` itself: every
    // TrackManager method already takes the player it acts on as a parameter (a holdover from
    // before this class existed to own that relationship), so wrapping it here means call sites
    // in PlayerScreen never need to know `exoPlayer` exists.
    fun getAudioTracks() = trackManager.getAudioTracks(exoPlayer)
    fun getSubtitleTracks() = trackManager.getSubtitleTracks(exoPlayer)
    fun selectAudioTrack(group: Tracks.Group, trackIndex: Int) = trackManager.selectAudioTrack(exoPlayer, group, trackIndex)
    fun selectSubtitleTrack(group: Tracks.Group, trackIndex: Int) = trackManager.selectSubtitleTrack(exoPlayer, group, trackIndex)
    fun disableSubtitles() = trackManager.disableSubtitles(exoPlayer)

    // Decision 9's Aspect action - cycles FIT -> FILL -> ZOOM, remembers per session (this
    // controller's own lifetime, which is exactly one fullscreen session - brief §3.3). Exposed
    // as state (not a direct PlayerView call) since the View lives in PlayerHost, one layer away;
    // PlayerHost's own AndroidView `update` block applies whatever this says.
    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    fun cycleResizeMode() {
        _resizeMode.value = when (_resizeMode.value) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private val _streamInfo = MutableStateFlow(StreamInfo())
    val streamInfo: StateFlow<StreamInfo> = _streamInfo.asStateFlow()

    // §4.4/§11: null = no error/reconnect state to show. §4.5's stall watchdog also writes here.
    private val _errorPresentation = MutableStateFlow<PlayerErrorPresentation?>(null)
    val errorPresentation: StateFlow<PlayerErrorPresentation?> = _errorPresentation.asStateFlow()

    private var retryJob: Job? = null
    private var retryAttempt = 0
    private val retryDelaysMs = longArrayOf(500, 1_000, 2_000, 4_000)

    private var stallWatchdogJob: Job? = null
    private var stallWindowStartMs = 0L
    private var stallCountInWindow = 0

    private var lastUrl: String? = null

    // A counter, not a Boolean: PlayerHost's black-overlay-between-zaps effect (brief §9's hoist
    // moved this signal out of a directly-registered listener, but it must stay the exact same
    // semantic - "a real frame of the *new* stream just rendered," not "tracks changed" or "size
    // changed," which can fire before a real frame is actually on screen) needs to observe every
    // occurrence, including a second zap whose destination channel happens to report identical
    // StreamInfo to the first - a plain data-equality check on [streamInfo] would miss that.
    private val _firstFrameRenderedTick = MutableStateFlow(0)
    val firstFrameRenderedTick: StateFlow<Int> = _firstFrameRenderedTick.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) = reportStreamInfo()
            override fun onVideoSizeChanged(videoSize: VideoSize) = reportStreamInfo()

            override fun onRenderedFirstFrame() {
                _errorPresentation.value = null
                _firstFrameRenderedTick.value++
                retryAttempt = 0
                cancelStallWatchdog()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    armStallWatchdog()
                } else {
                    cancelStallWatchdog()
                }
            }

            override fun onPlayerError(error: PlaybackException) = handlePlaybackError(error)
        })
    }

    private fun reportStreamInfo() {
        val video = exoPlayer.videoFormat
        val audio = exoPlayer.audioFormat
        _streamInfo.value = StreamInfo(
            resolutionClass = video?.height?.let(::resolutionClassOf),
            rawResolution = video?.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width}x${it.height}" },
            frameRate = video?.frameRate?.takeIf { it > 0f }?.roundToInt(),
            audioChannels = audio?.channelCount?.let(::audioChannelsLabelOf),
            audioCodec = audioCodecOf(audio?.sampleMimeType),
            videoCodec = videoCodecOf(video?.sampleMimeType),
        )
    }

    /**
     * §4.3: always `stop()` before swapping, on every zap, unconditionally - not just when
     * `blackScreenBetweenZaps` is on. This amends PHASE_2.md decision 13's *mechanism* ("never
     * stop()") while preserving its *user-visible outcome*: `setKeepContentOnPlayerReset(true)`
     * (set in `PlayerHost`, unchanged) is precisely what makes `stop()` visually free - the
     * shutter holds the last frame through the reset regardless. The reason this matters:
     * `IPTV_DOMAIN_KNOWLEDGE.md` §10's `max_connections: 1` - the old socket must be severed
     * before the new one opens, or the provider returns HTTP 456. `setMediaItem()` already resets
     * playback and re-initialises the codec regardless of `stop()`, so this costs nothing extra.
     */
    fun play(streamUrl: String) {
        if (streamUrl == lastUrl) return
        lastUrl = streamUrl
        retryJob?.cancel()
        retryAttempt = 0
        _errorPresentation.value = null
        cancelStallWatchdog()
        exoPlayer.stop()
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
    }

    /** §4.4: classify, retry with bounded backoff for retryable classes only, surface a friendly
     * message via [PlayerErrorMapper] - never a raw code. */
    private fun handlePlaybackError(error: PlaybackException) {
        val httpStatus = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        Log.d(TAG, "onPlayerError errorCode=${error.errorCode} httpStatus=$httpStatus attempt=$retryAttempt")

        if (error.cause is BehindLiveWindowException) {
            exoPlayer.seekToDefaultPosition()
            exoPlayer.prepare()
            return
        }

        // 456 (§4.3's max_connections race): one bounded delayed retry, not the general backoff
        // ladder - this is a known race on the *previous* socket's asynchronous close, not a
        // stream that's actually failing.
        if (httpStatus == 456 && retryAttempt == 0) {
            retryAttempt = 1
            _errorPresentation.value = PlayerErrorMapper.reconnecting()
            retryJob = scope.launch {
                delay(1_200)
                exoPlayer.prepare()
            }
            return
        }

        val nonRetryable = httpStatus == 403 || httpStatus == 404 || httpStatus == 884 ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        if (nonRetryable) {
            _errorPresentation.value = PlayerErrorMapper.present(error, isTerminal = true)
            return
        }

        if (retryAttempt < retryDelaysMs.size) {
            val delayMs = retryDelaysMs[retryAttempt]
            retryAttempt++
            _errorPresentation.value = PlayerErrorMapper.present(error, isTerminal = false)
            retryJob = scope.launch {
                delay(delayMs)
                exoPlayer.prepare()
            }
        } else {
            _errorPresentation.value = PlayerErrorMapper.present(error, isTerminal = true)
        }
    }

    /** §4.5: continuous buffering for 15s -> one `prepare()`; a second stall within 60s of the
     * first -> give up and say so, rather than looping forever. Covers, generically, PTS
     * wraparound, provider encoder discontinuities, a half-dead TCP connection, and DNS flapping
     * - one mechanism instead of chasing each separately. */
    private fun armStallWatchdog() {
        if (stallWatchdogJob?.isActive == true) return
        stallWatchdogJob = scope.launch {
            delay(15_000)
            val now = System.currentTimeMillis()
            if (now - stallWindowStartMs > 60_000) {
                stallWindowStartMs = now
                stallCountInWindow = 0
            }
            stallCountInWindow++
            if (stallCountInWindow >= 2) {
                _errorPresentation.value = PlayerErrorMapper.stalled()
            } else {
                _errorPresentation.value = PlayerErrorMapper.reconnecting()
                exoPlayer.prepare()
            }
        }
    }

    private fun cancelStallWatchdog() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
    }

    fun release() {
        retryJob?.cancel()
        cancelStallWatchdog()
        afrManager.restoreOriginalMode()
        exoPlayer.release()
    }
}
