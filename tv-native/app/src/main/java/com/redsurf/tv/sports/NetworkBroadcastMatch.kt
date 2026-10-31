package com.redsurf.tv.sports

/**
 * Matches linear IPTV channel names against a game's broadcast networks and the user's home
 * market, per `docs/plans/SPORTS_HUB.md` §3a and §9.7. Awards [MatchTier.IN_MARKET] for home-market
 * RSNs and broadcast affiliates (including streaming-exclusive simulcasts), [MatchTier.NATIONAL]
 * for dedicated national sports cable feeds, or [MatchTier.MAYBE] for out-of-market or non-market
 * broadcast network feeds.
 */
object NetworkBroadcastMatch {

    private val BROADCAST_NETWORKS = setOf("fox", "cbs", "nbc", "abc")
    private val STREAMING_EXCLUSIVES = setOf("prime video", "peacock")

    /**
     * Determines whether [rawChannelName] carries [game] based on its network listings and
     * the optional user [market], returning the appropriate [MatchTier] or null if no match.
     */
    fun match(rawChannelName: String, game: Game, market: String?): MatchTier? {
        val channelName = ChannelNameNormalizer.normalize(rawChannelName)
        if (channelName.flags.isNotEmpty()) return null
        if (game.networks.isEmpty()) return null

        val gameCanonicals = game.networks.mapNotNull { NationalNetworks.canonicalFor(it) }.toSet()

        if (market != null) {
            val marketChannel = MarketNetworks.channelFor(rawChannelName, market)
            if (marketChannel != null) {
                if (marketChannel.kind == MarketKind.RSN) {
                    if (game.networks.any { MarketNetworks.channelFor(it, market) == marketChannel }) {
                        return MatchTier.IN_MARKET
                    }
                } else if (marketChannel.kind == MarketKind.AFFILIATE) {
                    if (marketChannel.network != null && marketChannel.network in gameCanonicals) {
                        return MatchTier.IN_MARKET
                    }
                    if (gameCanonicals.any { it in STREAMING_EXCLUSIVES }) {
                        return MatchTier.IN_MARKET
                    }
                }
            }
        }

        val nationalNetwork = NationalNetworks.canonicalFor(rawChannelName)
            ?: BROADCAST_NETWORKS.firstOrNull { net ->
                channelName.key.split(' ').contains(net) && !channelName.key.contains("$net sports")
            }

        if (nationalNetwork != null && nationalNetwork in gameCanonicals) {
            return if (nationalNetwork in BROADCAST_NETWORKS) {
                MatchTier.MAYBE
            } else {
                MatchTier.NATIONAL
            }
        }

        return null
    }
}
