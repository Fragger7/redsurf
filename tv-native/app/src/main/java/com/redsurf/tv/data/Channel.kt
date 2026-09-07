package com.redsurf.tv.data

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val group: String = "Uncategorized",
    val epgId: String = ""
)

data class ChannelGroup(
    val name: String,
    val channels: List<Channel>
)
