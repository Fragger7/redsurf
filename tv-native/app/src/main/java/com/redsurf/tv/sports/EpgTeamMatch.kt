package com.redsurf.tv.sports

import java.text.Normalizer

private val WORD_BOUNDARY_REGEX = Regex("""[^\p{L}\p{N}]+""")
private val STOPWORDS = setOf("city", "united", "club", "team", "state")

private fun foldWords(text: String): Set<String> {
    val folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase()
        .replace(WORD_BOUNDARY_REGEX, " ")
        .trim()
    if (folded.isEmpty()) return emptySet()
    return folded.split(' ').filter { it.isNotEmpty() }.toSet()
}

/**
 * Extracts distinctive match tokens for a sports team against an opponent, per
 * `docs/plans/SPORTS_HUB.md` §3b. Words are folded, filtered to at least 4 letters,
 * stripped of generic stopwords, and pruned of words shared with the opponent.
 */
object TeamTokens {

    /**
     * Extracts distinctive tokens for [team] in the context of [opponent].
     * Returns lowercase words of at least 4 letters, omitting stopwords and shared words.
     */
    fun of(team: String, opponent: String): Set<String> {
        val opponentWords = foldWords(opponent)
        return foldWords(team)
            .filter { word ->
                word.length >= 4 && word !in STOPWORDS && word !in opponentWords
            }
            .toSet()
    }
}

/**
 * Matches EPG programme text (title, subtitle, and description) to a sports game by checking
 * for whole-word presence of both teams, per `docs/plans/SPORTS_HUB.md` §3b. Pure and deterministic.
 */
object EpgTeamMatch {

    /**
     * Determines whether an EPG entry corresponds to a matchup between [home] and [away].
     * Returns [MatchTier.EPG_TITLE] when both teams appear across title and subtitle,
     * [MatchTier.EPG_DESCRIPTION] when both appear once description is included, or null otherwise.
     */
    fun match(
        title: String,
        subtitle: String?,
        description: String?,
        home: String,
        away: String,
    ): MatchTier? {
        val homeTokens = TeamTokens.of(home, away)
        val awayTokens = TeamTokens.of(away, home)
        if (homeTokens.isEmpty() || awayTokens.isEmpty()) return null

        val titleWords = foldWords(title).toMutableSet()
        if (subtitle != null) {
            titleWords.addAll(foldWords(subtitle))
        }

        val homeInTitle = homeTokens.any { it in titleWords }
        val awayInTitle = awayTokens.any { it in titleWords }
        if (homeInTitle && awayInTitle) {
            return MatchTier.EPG_TITLE
        }

        val allWords = titleWords.toMutableSet()
        if (description != null) {
            allWords.addAll(foldWords(description))
        }

        val homeInAll = homeTokens.any { it in allWords }
        val awayInAll = awayTokens.any { it in allWords }
        if (homeInAll && awayInAll) {
            return MatchTier.EPG_DESCRIPTION
        }

        return null
    }
}
