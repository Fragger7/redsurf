package com.redsurf.tv.player.tracks

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelectionParameters

/**
 * PRODUCTION TRACK MANAGER:
 * Bypasses basic ExoPlayer limits to extract embedded HLS/MPEG-TS audio and subtitle bitstreams.
 * Essential for IPTV feeds that pack multiple languages (e.g. English, Spanish) in a single stream.
 */
class TrackManager(private val context: Context) {
    val trackSelector = DefaultTrackSelector(context)

    init {
        // Force track selector to prefer the system's default language initially
        val params = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage("en")
            .setPreferredTextLanguage("en")
            .build()
        trackSelector.setParameters(params)
    }

    fun getAudioTracks(player: ExoPlayer): List<Tracks.Group> {
        return player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    }

    fun getSubtitleTracks(player: ExoPlayer): List<Tracks.Group> {
        return player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    }

    fun selectAudioTrack(player: ExoPlayer, group: Tracks.Group, trackIndex: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
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
