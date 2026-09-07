#!/bin/bash
set -e

# Fix IptvNetworkModule.kt
sed -i 's/val bootstrapDns = Dns { hostname ->/val bootstrapDns = object : Dns {\n            override fun lookup(hostname: String): List<InetAddress> {\n                return when (hostname) {/' tv-native/app/src/main/java/com/redsurf/tv/network/IptvNetworkModule.kt
sed -i 's/else -> Dns.SYSTEM.lookup(hostname)\n            }\n        }/else -> Dns.SYSTEM.lookup(hostname)\n                }\n            }\n        }/' tv-native/app/src/main/java/com/redsurf/tv/network/IptvNetworkModule.kt

# Fix TrackManager.kt
# e: TrackManager.kt:9:49 Unresolved reference: TrackSelectionParameters
sed -i 's/import androidx.media3.common.TrackSelectionParameters/import androidx.media3.common.TrackSelectionParameters\nimport androidx.media3.common.TrackSelectionOverride/' tv-native/app/src/main/java/com/redsurf/tv/player/tracks/TrackManager.kt

# Fix MainViewModel.kt
# 45: Channel(it.id, it.name, it.streamUrl, it.logoUrl, it.groupName, it.epgId)
# 97: ChannelEntity(it.id, it.name, it.streamUrl, it.logoUrl, it.group, it.epgId)
# 99: localDb?.channelDao()?.clearAll() -> deleteChannelsByPlaylist("default")
sed -i 's/Channel(it.id, it.name, it.streamUrl, it.logoUrl, it.groupName, it.epgId)/Channel(it.streamId, it.name, it.streamId, it.streamIcon ?: "", it.groupName, it.epgChannelId ?: "")/g' tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt

sed -i 's/ChannelEntity(it.id, it.name, it.streamUrl, it.logoUrl, it.group, it.epgId)/ChannelEntity(streamId = it.streamUrl, playlistId = "default", groupId = "default", num = 0, name = it.name, streamType = "live", streamIcon = it.logoUrl, epgChannelId = it.epgId, groupName = it.group)/g' tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt

sed -i 's/localDb?.channelDao()?.clearAll()/localDb?.channelDao()?.deleteChannelsByPlaylist("default")/g' tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt

git add .
git commit -m "build: fix kotlin compilation errors in network, tracks and viewmodel"
git push origin main
