package com.redsurf.tv.sports

import kotlin.math.abs

/**
 * Represents an IPTV channel available for playback, holding its playlist identifier, stream
 * identifier, display name, user preferences (such as favourite status), and playback reliability metadata.
 */
data class PlayableChannel(
    val playlistId: String,
    val streamId: Int,
    val name: String,
    val isFavorite: Boolean = false,
    val playlistWorking: Boolean = true,
    val lastSuccessMillis: Long? = null,
)

/**
 * Represents an electronic programme guide (EPG) listing for a channel, capturing schedule bounds
 * and programme metadata (title, subtitle, description) used for sports fixture matching.
 */
data class EpgProgramEntry(
    val playlistId: String,
    val streamId: Int,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val startMillis: Long,
    val endMillis: Long,
)

private const val KICKOFF_WINDOW_BEFORE_MILLIS = 2 * 3600 * 1000L
private const val KICKOFF_WINDOW_AFTER_MILLIS = 4 * 3600 * 1000L

private data class ProbeHit(
    val tier: MatchTier,
    val kickoffDistanceMillis: Long = 0,
)

/**
 * Resolves and ranks playable candidate channels carrying a scheduled sports fixture by combining
 * multiple independent matching probes, applying language and channel filters, and ordering candidate sources.
 */
object GameSourceResolver {

    /**
     * Evaluates candidate channels against a scheduled sports game across all matching probes
     * (saved picks, event names, network affiliates, EPG listings), applies language and quality
     * deduplication filters, and returns ranked sources.
     */
    fun resolve(
        game: Game,
        channels: List<PlayableChannel>,
        epgEntries: List<EpgProgramEntry> = emptyList(),
        market: String? = null,
        allowedLanguages: Set<String> = emptySet(),
        savedPick: Pair<String, Int>? = null,
    ): RankedSources {
        val windowStart = game.kickoffMillis - KICKOFF_WINDOW_BEFORE_MILLIS
        val windowEnd = game.kickoffMillis + KICKOFF_WINDOW_AFTER_MILLIS
        val epgByChannel = epgEntries.groupBy { it.playlistId to it.streamId }

        val candidatesWithNames = mutableListOf<Pair<SourceCandidate, ChannelName>>()

        for (channel in channels) {
            val channelName = ChannelNameNormalizer.normalize(channel.name)
            val hits = mutableListOf<ProbeHit>()

            // Probe 3d (Saved Pick)
            if (savedPick != null && savedPick.first == channel.playlistId && savedPick.second == channel.streamId) {
                hits.add(ProbeHit(MatchTier.USER_PICK))
            }

            // Probe 3c (Event Channel)
            val eventTier = EventChannelMatch.match(channel.name, game)
            if (eventTier != null) {
                hits.add(ProbeHit(eventTier))
            }

            // Probe 3a (Network Broadcast)
            val broadcastTier = NetworkBroadcastMatch.match(channel.name, game, market)
            if (broadcastTier != null) {
                hits.add(ProbeHit(broadcastTier))
            }

            // Probe 3b (EPG Programs)
            val matchingEpg = epgByChannel[channel.playlistId to channel.streamId].orEmpty()
            for (entry in matchingEpg) {
                if (entry.endMillis > windowStart && entry.startMillis < windowEnd) {
                    val epgTier = EpgTeamMatch.match(
                        entry.title,
                        entry.subtitle,
                        entry.description,
                        game.homeTeam,
                        game.awayTeam,
                    ) ?: EpgTeamMatch.match(
                        entry.title,
                        entry.subtitle,
                        entry.description,
                        game.awayTeam,
                        game.homeTeam,
                    )
                    if (epgTier != null) {
                        hits.add(ProbeHit(epgTier, entry.startMillis - game.kickoffMillis))
                    }
                }
            }

            if (hits.isEmpty()) continue

            // Multi-probe agreement: combine matched probe tiers (strongest tier wins, sum probe count)
            val bestHit = hits.minWith(compareBy({ it.tier.ordinal }, { abs(it.kickoffDistanceMillis) }))
            val candidate = SourceCandidate(
                playlistId = channel.playlistId,
                streamId = channel.streamId,
                tier = bestHit.tier,
                probes = hits.size,
                isFavorite = channel.isFavorite,
                playlistWorking = channel.playlistWorking,
                quality = channelName.quality,
                lastSuccessMillis = channel.lastSuccessMillis,
                kickoffDistanceMillis = bestHit.kickoffDistanceMillis,
            )

            // Language filter: if candidate.tier != MatchTier.EVENT_CHANNEL && !ChannelLanguage.passes(channelName, allowedLanguages), discard candidate
            if (candidate.tier != MatchTier.EVENT_CHANNEL && !ChannelLanguage.passes(channelName, allowedLanguages)) {
                continue
            }

            candidatesWithNames.add(candidate to channelName)
        }

        val filtered = SourceFilter.apply(candidatesWithNames)
        return SourceRanker.rank(filtered)
    }
}
