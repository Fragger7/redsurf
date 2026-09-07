package com.redsurf.tv.player

enum class PlayerEngineType {
    EXO_PLAYER, // Best for modern HLS/Dash, hardware acceleration
    LIB_VLC     // Best fallback for heavily interlaced MPEG-TS, weird AC3 audio codecs
}

interface RedSurfPlayer {
    fun play(url: String)
    fun pause()
    fun stop()
    fun release()
    fun setVolume(volume: Float)
    fun setAudioTrack(trackId: String)
}
