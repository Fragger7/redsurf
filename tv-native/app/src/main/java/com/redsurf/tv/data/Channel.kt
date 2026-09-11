package com.redsurf.tv.data

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val group: String = "Uncategorized",
    val epgId: String = "",
    // "live", "vod", or "series" - classified from the stream URL by M3uParser.
    // See docs/plans/PHASE_1.md #2b.
    val streamType: String = "live",
)

data class ChannelGroup(
    val name: String,
    val channels: List<Channel>
)
