package com.redsurf.tv.player.tracks

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
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
class TrackManager(private val context: Context) {
    val trackSelector = DefaultTrackSelector(context)
    private val appPreferences = AppPreferences(context)

    init {
        val persistedAudioLanguage = appPreferences.preferredAudioLanguage.value
        val audioLanguages = if (persistedAudioLanguage != null) {
            arrayOf(persistedAudioLanguage)
        } else {
            Util.getSystemLanguageCodes()
        }
        val params = trackSelector.buildUponParameters()
            .setPreferredAudioLanguages(*audioLanguages)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        trackSelector.setParameters(params)
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
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun selectSubtitleTrack(player: ExoPlayer, group: Tracks.Group, trackIndex: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }
}
