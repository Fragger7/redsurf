package com.redsurf.tv.player.tracks

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.TrackSelectionParameters
import com.redsurf.tv.settings.AppPreferences

/**
 * PRODUCTION TRACK MANAGER:
 * Bypasses basic ExoPlayer limits to extract embedded HLS/MPEG-TS audio and subtitle bitstreams.
 * Essential for IPTV feeds that pack multiple languages (e.g. English, Spanish) in a single stream.
 *
 * Defaults corrected (PLAYER_ENGINEERING_BRIEF.md §2.8): the hardcoded `setPreferredTextLanguage
 * ("en")` was a real defect - a preferred *text* language causes ExoPlayer to select and *render*
 * a matching subtitle track automatically, so any channel carrying an English CEA-608 track
 * showed subtitles nobody asked for. Subtitles are opt-in now (PHASE_2.md decision 9's picker),
 * and the hardcoded `"en"` audio preference is the system's actual locale list instead, overridden
 * by whatever the user last explicitly picked (persisted via [AppPreferences], since a
 * `TrackSelectionOverride` is scoped to a specific track group's identity and doesn't survive a
 * channel change - without persisting the *language*, "give me the English feed" would need
 * re-picking after every single zap).
 */
class TrackManager(private val context: Context, maxBufferMs: Int = 15_000) {
    // Brief §4.2/item 10: Media3's ABR defaults (10s up / 25s down / 25s retain) assume a buffer
    // deeper than most Buffer size choices here, so two of the three thresholds could never be
    // reached and step-downs under jitter fired late or not at all. Each threshold now sits
    // strictly inside the chosen maxBufferMs (Small's 15s gives the brief's own 6s/10s/10s), and
    // never exceeds Media3's defaults on the deep buffers.
    val trackSelector = DefaultTrackSelector(
        context,
        AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ minOf(10_000, maxBufferMs * 2 / 5),
            /* maxDurationForQualityDecreaseMs = */ minOf(25_000, maxBufferMs * 2 / 3),
            /* minDurationToRetainAfterDiscardMs = */ minOf(25_000, maxBufferMs * 2 / 3),
            /* bandwidthFraction = */ 0.7f,
        ),
    )
    private val appPreferences = AppPreferences(context)

    // Brief §2.5/item 11: rank video codecs by what this device decodes in *hardware*, probed
    // once at runtime rather than hardcoded. Android 14's software AV1 decoder would otherwise let
    // ABR pick an AV1 rendition the Chromecast's A55 cores can't keep up with; a future
    // AV1-capable box gets it ranked automatically. A preference only orders the choice - an
    // AV1-only stream still plays (or fails honestly via PlayerErrorMapper.videoFormatUnsupported).
    private val hardwareVideoMimes: List<String> by lazy {
        listOf(MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_VP9, MimeTypes.VIDEO_AV1).filter { mime ->
            runCatching {
                MediaCodecUtil.getDecoderInfos(mime, false, false).any { it.hardwareAccelerated }
            }.getOrDefault(false)
        }.also { android.util.Log.d("RedSurf", "hardware video decoders, preferred order: $it") }
    }

    init {
        trackSelector.setParameters(withPersistedLanguages(trackSelector.buildUponParameters()).build())
    }

    /** Re-reads the persisted audio/subtitle languages - called on every [PlayerController.play]
     * because the player is shared and long-lived: a Settings reset made after it was built has
     * to reach the next channel, not wait for a process restart. */
    fun applyPersistedLanguages(player: ExoPlayer) {
        player.trackSelectionParameters =
            withPersistedLanguages(player.trackSelectionParameters.buildUpon()).build()
    }

    private fun <B : TrackSelectionParameters.Builder> withPersistedLanguages(builder: B): B {
        val prefs = AppPreferences(context)
        if (hardwareVideoMimes.isNotEmpty()) builder.setPreferredVideoMimeTypes(*hardwareVideoMimes.toTypedArray())
        val audio = prefs.preferredAudioLanguage.value
        val subtitle = prefs.preferredSubtitleLanguage.value
        builder
            .setPreferredAudioLanguages(*(if (audio != null) arrayOf(audio) else Util.getSystemLanguageCodes()))
            .setPreferredTextLanguage(subtitle)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle == null)
        return builder
    }

    fun getAudioTracks(player: ExoPlayer): List<Tracks.Group> {
        return player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    }

    fun getSubtitleTracks(player: ExoPlayer): List<Tracks.Group> {
        return player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    }

    /** Persists the chosen track's language (§2.8) so it's still the preference on the next
     * channel, not just an override on a track group that won't exist after the zap. */
    fun selectAudioTrack(player: ExoPlayer, group: Tracks.Group, trackIndex: Int) {
        val language = group.getTrackFormat(trackIndex).language
        if (!language.isNullOrBlank()) appPreferences.setPreferredAudioLanguage(language)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .apply { if (!language.isNullOrBlank()) setPreferredAudioLanguage(language) }
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    fun disableSubtitles(player: ExoPlayer) {
        appPreferences.setPreferredSubtitleLanguage(null)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /** Persists the language too, like [selectAudioTrack], so the next channel opens with it. */
    fun selectSubtitleTrack(player: ExoPlayer, group: Tracks.Group, trackIndex: Int) {
        val language = group.getTrackFormat(trackIndex).language
        if (!language.isNullOrBlank()) appPreferences.setPreferredSubtitleLanguage(language)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .apply { if (!language.isNullOrBlank()) setPreferredTextLanguage(language) }
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }
}
