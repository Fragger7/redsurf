package com.redsurf.tv.sports

import java.text.Normalizer

private val WORD_BOUNDARY_REGEX = Regex("""[^\p{L}\p{N}]+""")

private fun foldWords(text: String): Set<String> {
    val folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase()
        .replace(WORD_BOUNDARY_REGEX, " ")
        .trim()
    if (folded.isEmpty()) return emptySet()
    return folded.split(' ').filter { it.isNotEmpty() }.toSet()
}

/**
 * Matches raw event and pay-per-view channel names against scheduled fixtures to award
 * [MatchTier.EVENT_CHANNEL], enabling discovery of temporary game broadcasts (§3c).
 */
object EventChannelMatch {

    /**
     * Determines whether [rawChannelName] corresponds to [game] by parsing the title into competing
     * sides, verifying both teams match distinctly, and checking kickoff time alignment when present.
     */
    fun match(rawChannelName: String, game: Game): MatchTier? {
        val parsed = EventNameParser.parse(rawChannelName) ?: return null

        val homeTokens = TeamTokens.of(game.homeTeam, game.awayTeam)
        val awayTokens = TeamTokens.of(game.awayTeam, game.homeTeam)
        if (homeTokens.isEmpty() || awayTokens.isEmpty()) return null

        val sideAWords = foldWords(parsed.sideA)
        val sideBWords = foldWords(parsed.sideB)

        val sideAMatchesHome = homeTokens.any { it in sideAWords }
        val sideAMatchesAway = awayTokens.any { it in sideAWords }
        val sideBMatchesHome = homeTokens.any { it in sideBWords }
        val sideBMatchesAway = awayTokens.any { it in sideBWords }

        val bothMatchHome = sideAMatchesHome && sideBMatchesHome
        val bothMatchAway = sideAMatchesAway && sideBMatchesAway
        if (bothMatchHome || bothMatchAway) return null

        val homeVsAway = sideAMatchesHome && sideBMatchesAway
        val awayVsHome = sideAMatchesAway && sideBMatchesHome
        if (!homeVsAway && !awayVsHome) return null

        if (parsed.minutesEt != null) {
            if (!EventNameParser.timeFits(parsed.minutesEt, game.kickoffMillis)) {
                return null
            }
        }

        return MatchTier.EVENT_CHANNEL
    }
}
