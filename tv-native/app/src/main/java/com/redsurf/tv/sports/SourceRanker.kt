package com.redsurf.tv.sports

/**
 * Evidence tiers for "this channel shows this game", best first (`docs/plans/SPORTS_HUB.md` §3,
 * "Ranking", Lume-style). Lower ordinal = stronger evidence.
 */
enum class MatchTier {
    USER_PICK,        // 0: the user's own pick for this game, or their saved network mapping
    EVENT_CHANNEL,    // 1: an event channel naming both sides, time inside the window
    EPG_TITLE,        // 2: the EPG names both teams in the title or subtitle
    NATIONAL,         // 3: a national network the leagues list for this game
    IN_MARKET,        // 4: an RSN or affiliate in the user's market
    EPG_DESCRIPTION,  // 5: both teams appear only in the EPG description
    MAYBE,            // 6: out of market, or any "fox" fallback
}

/**
 * One channel that a probe thinks carries a game. [probes] counts how many independent probes
 * (alias, EPG, event name, remembered pick) found it; [lastSuccessMillis] is its newest entry in
 * `recent_channels`, if any; [kickoffDistanceMillis] is how far its matched listing starts from
 * kickoff (0 when the probe has no time).
 */
data class SourceCandidate(
    val playlistId: String,
    val streamId: Int,
    val tier: MatchTier,
    val probes: Int = 1,
    val isFavorite: Boolean = false,
    val playlistWorking: Boolean = true,
    val quality: Quality? = null,
    val lastSuccessMillis: Long? = null,
    val kickoffDistanceMillis: Long = 0,
)

/** The ranked sources for one game, and whether OK may play [best] without asking. */
data class RankedSources(val sources: List<SourceCandidate>, val isConfident: Boolean) {
    val best: SourceCandidate? get() = sources.firstOrNull()
}

/**
 * Orders candidates by tier, then the spec's tie-breaks in order: probe agreement, favourite,
 * working playlist, quality, recent success, proximity to kickoff. Duplicate (playlist, stream)
 * entries keep their strongest evidence. Pure; filtering (language, TEST/BACKUP) happens before.
 */
object SourceRanker {

    const val MAX_SOURCES = 20

    private val order = compareBy<SourceCandidate> { it.tier.ordinal }
        .thenByDescending { it.probes }
        .thenByDescending { it.isFavorite }
        .thenByDescending { it.playlistWorking }
        .thenByDescending { it.quality?.ordinal ?: -1 }
        .thenByDescending { it.lastSuccessMillis ?: Long.MIN_VALUE }
        .thenBy { kotlin.math.abs(it.kickoffDistanceMillis) }

    fun rank(candidates: List<SourceCandidate>): RankedSources {
        val sorted = candidates
            .groupBy { it.playlistId to it.streamId }
            .map { (_, same) -> same.sortedWith(order).first().copy(probes = same.sumOf { it.probes }) }
            .sortedWith(order)
            .take(MAX_SOURCES)
        return RankedSources(sorted, isConfident(sorted))
    }

    /**
     * Lume's rule: OK plays immediately only when tiers 0-2 hold a single winner, or when the top
     * tier has exactly one source. Otherwise OK opens the sources sheet.
     */
    private fun isConfident(sorted: List<SourceCandidate>): Boolean {
        if (sorted.isEmpty()) return false
        val strong = sorted.count { it.tier <= MatchTier.EPG_TITLE }
        if (strong == 1) return true
        return sorted.count { it.tier == sorted[0].tier } == 1
    }
}
