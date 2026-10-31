package com.redsurf.tv.sports

/**
 * Summary of televised sports match results against available provider channels.
 * Measures how many nationally televised games have an available source, and records unmatched network names.
 */
data class MatchReport(
    val nationalGames: Int,
    val nationalGamesWithSource: Int,
    val unmatchedNetworks: Map<String, Int>,
) {
    /**
     * Fraction of nationally televised games with an available source, or 0.0 when there are no national games.
     */
    val rate: Double = if (nationalGames > 0) nationalGamesWithSource.toDouble() / nationalGames else 0.0
}

/**
 * Pure measurement of televised game coverage across IPTV channels, per SPORTS_HUB.md S0.
 * Determines how many national games have a playable source and collects unmatched network names.
 */
object MatchRate {

    /**
     * Measures source coverage for [games] against [channelNames] in the given [market].
     */
    fun measure(games: List<Game>, channelNames: List<String>, market: String): MatchReport {
        val availableNetworks = mutableSetOf<String>()
        for (name in channelNames) {
            if (ChannelNameNormalizer.normalize(name).flags.isEmpty()) {
                NationalNetworks.canonicalFor(name)?.let { availableNetworks += it }
                MarketNetworks.channelFor(name, market)?.network?.let { availableNetworks += it }
            }
        }

        val unmatched = mutableMapOf<String, Int>()
        var nationalGames = 0
        var nationalGamesWithSource = 0

        for (game in games) {
            val canonicals = mutableSetOf<String>()
            for (rawNetwork in game.networks) {
                val canonical = NationalNetworks.canonicalFor(rawNetwork)
                if (canonical != null) {
                    canonicals += canonical
                } else {
                    unmatched[rawNetwork] = (unmatched[rawNetwork] ?: 0) + 1
                }
            }

            if (canonicals.isNotEmpty()) {
                nationalGames++
                if (canonicals.any { it in availableNetworks }) {
                    nationalGamesWithSource++
                }
            }
        }

        return MatchReport(
            nationalGames = nationalGames,
            nationalGamesWithSource = nationalGamesWithSource,
            unmatchedNetworks = unmatched,
        )
    }
}
