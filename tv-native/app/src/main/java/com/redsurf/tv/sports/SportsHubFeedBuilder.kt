package com.redsurf.tv.sports

/** Compatibility alias mapping [QualityTier] to [Quality]. */
typealias QualityTier = Quality

/**
 * A display-ready card representing a scheduled or live sports [game] with its resolved playback
 * source candidates, confidence status, team follow status, and team logo URLs per SPORTS_HUB.md §5 and §9.6.
 */
data class SportsGameCard(
    val game: Game,
    val topSource: SourceCandidate?,
    val sourceCount: Int,
    val isConfident: Boolean,
    val isFollowedTeam: Boolean,
    val homeLogoUrl: String?,
    val awayLogoUrl: String?,
)

/**
 * A titled section of cards within the sports hub feed (e.g. Live Now, a home market or followed
 * teams row, or a specific league section), optionally bound to a parent [league].
 */
data class SportsFeedSection(
    val id: String,
    val title: String,
    val league: League? = null,
    val cards: List<SportsGameCard>,
)

/**
 * Structured feed containing the sections to display on the Sports Hub screen: an optional [liveSection]
 * for currently playing games, an optional [followedSection] for user or market teams, and [leagueSections]
 * ordered by priority per SPORTS_HUB.md §5 and §10.
 */
data class SportsHubFeed(
    val liveSection: SportsFeedSection?,
    val followedSection: SportsFeedSection?,
    val leagueSections: List<SportsFeedSection>,
) {
    /** Total number of game cards across all active sections in the feed. */
    val totalCards: Int get() = (liveSection?.cards?.size ?: 0) + (followedSection?.cards?.size ?: 0) + leagueSections.sumOf { it.cards.size }
}

/**
 * Transforms resolved games and their ranked playback candidates into structured hub feed sections
 * (Live now, followed/market teams, and per-league sections) with logo URLs and confidence metadata.
 */
object SportsHubFeedBuilder {

    private val leagueOrder = listOf(
        League.NFL,
        League.NBA,
        League.MLB,
        League.NHL,
        League.UFC,
    )

    private val sectionComparator = Comparator<SportsGameCard> { a, b ->
        val stateRank = fun(state: GameState): Int = when (state) {
            GameState.IN -> 0
            GameState.PRE -> 1
            GameState.POST -> 2
            GameState.UNKNOWN -> 3
        }
        val rankA = stateRank(a.game.state)
        val rankB = stateRank(b.game.state)
        if (rankA != rankB) {
            return@Comparator rankA.compareTo(rankB)
        }
        when (a.game.state) {
            GameState.IN -> a.game.kickoffMillis.compareTo(b.game.kickoffMillis)
            GameState.PRE -> a.game.kickoffMillis.compareTo(b.game.kickoffMillis)
            GameState.POST -> b.game.kickoffMillis.compareTo(a.game.kickoffMillis)
            GameState.UNKNOWN -> a.game.kickoffMillis.compareTo(b.game.kickoffMillis)
        }
    }

    /**
     * Builds a [SportsHubFeed] from [resolvedGames], populating live games, followed teams for the given
     * [market] or [followedTeams] set, and ordered league sections with non-empty games.
     */
    fun buildFeed(
        resolvedGames: List<Pair<Game, RankedSources>>,
        market: String? = null,
        followedTeams: Set<String> = emptySet(),
    ): SportsHubFeed {
        val marketTeams = if (market != null) TeamDirectory.marketTeams(market) else emptyList()
        val effectiveFollowed = followedTeams + marketTeams.map { it.code } + marketTeams.map { it.name }

        val cards = resolvedGames.map { (game, ranked) ->
            val isFollowed = TeamDirectory.isFollowed(game.league, game.homeTeam, effectiveFollowed) ||
                TeamDirectory.isFollowed(game.league, game.awayTeam, effectiveFollowed)
            val homeLogo = TeamDirectory.logoUrl(game.league, game.homeTeam)
            val awayLogo = TeamDirectory.logoUrl(game.league, game.awayTeam)

            SportsGameCard(
                game = game,
                topSource = ranked.best,
                sourceCount = ranked.sources.size,
                isConfident = ranked.isConfident,
                isFollowedTeam = isFollowed,
                homeLogoUrl = homeLogo,
                awayLogoUrl = awayLogo,
            )
        }

        val liveCards = cards
            .filter { it.game.state == GameState.IN }
            .sortedBy { it.game.kickoffMillis }
        val liveSection = if (liveCards.isNotEmpty()) {
            SportsFeedSection(id = "live", title = "Live Now", cards = liveCards)
        } else {
            null
        }

        val followedCards = cards
            .filter { it.isFollowedTeam }
            .sortedWith(sectionComparator)
        val followedTitle = if (!market.isNullOrBlank()) {
            market.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            "My Teams"
        }
        val followedSection = if (followedCards.isNotEmpty()) {
            SportsFeedSection(id = "followed", title = followedTitle, cards = followedCards)
        } else {
            null
        }

        val cardsByLeague = cards.groupBy { it.game.league }
        val leagueSections = mutableListOf<SportsFeedSection>()
        for (league in leagueOrder) {
            val leagueCards = cardsByLeague[league]?.sortedWith(sectionComparator)
            if (!leagueCards.isNullOrEmpty()) {
                leagueSections.add(
                    SportsFeedSection(
                        id = "league_${league.name.lowercase()}",
                        title = league.name,
                        league = league,
                        cards = leagueCards,
                    ),
                )
            }
        }

        return SportsHubFeed(
            liveSection = liveSection,
            followedSection = followedSection,
            leagueSections = leagueSections,
        )
    }
}
