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
    // The provider's own channel number (M3U's tvg-chno attribute), when present - null falls
    // back to a per-group scan-order counter at the call site (MainViewModel.loadPlaylist). Found
    // live, 2026-09-12: this was never parsed at all, so every M3U import silently discarded the
    // provider's real numbering in favor of raw file-scan order - correct only when a group's
    // channels happen to already be laid out contiguously and in-order in the source file.
    val chno: Int? = null,
)

data class ChannelGroup(
    val name: String,
    val channels: List<Channel>
)
