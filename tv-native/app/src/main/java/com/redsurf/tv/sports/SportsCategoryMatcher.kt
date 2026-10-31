package com.redsurf.tv.sports

import java.text.Normalizer

/**
 * Search priority tiers for matching provider channel categories against sports fixtures,
 * prioritizing dedicated league feeds over general sports channels and skipping non-sports.
 */
enum class CategoryPriority {
    DEDICATED_LEAGUE,
    GENERAL_SPORTS,
    NON_SPORTS,
}

/**
 * Classifies raw IPTV playlist category names (group-title) by sports relevance and league affiliation
 * to guide channel discovery and source ranking per SPORTS_HUB.md §10.
 */
object SportsCategoryMatcher {

    private val PUNCTUATION_REGEX = Regex("""[|\-:_/•]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private val LEAGUE_PATTERNS: List<Pair<League, List<Regex>>> = listOf(
        League.NFL to listOf("nfl", "sunday ticket", "super bowl"),
        League.NBA to listOf("nba", "league pass"),
        League.MLB to listOf("mlb", "extra innings"),
        League.NHL to listOf("nhl", "center ice"),
        League.UFC to listOf("ufc", "mma", "fight pass"),
    ).map { (league, phrases) ->
        league to phrases.map { phrase ->
            Regex("""\b${Regex.escape(phrase)}\b""")
        }
    }

    private val SPORTS_INDICATOR_PATTERNS: List<Regex> = listOf(
        "sports", "sport", "deportes", "espn", "fox sports", "sky sports",
        "dazn", "bein", "ppv", "events", "box office", "match day",
        "tnt sports", "super sport", "racing", "football", "basketball",
        "baseball", "hockey", "wwe", "afl", "rugby", "cricket"
    ).map { phrase ->
        Regex("""\b${Regex.escape(phrase)}\b""")
    }

    private fun normalize(raw: String): String {
        return Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .lowercase()
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    /**
     * Identifies the specific [League] dedicated to [rawCategory] by inspecting normalized tokens
     * and known phrases, returning null if the category does not target a single recognized league.
     */
    fun leagueFor(rawCategory: String): League? {
        if (rawCategory.isBlank()) return null
        val normalized = normalize(rawCategory)
        if (normalized.isEmpty()) return null

        for ((league, patterns) in LEAGUE_PATTERNS) {
            if (patterns.any { it.containsMatchIn(normalized) }) {
                return league
            }
        }
        return null
    }

    /**
     * Determines whether [rawCategory] represents sports programming by checking for league affiliation
     * or general sports keywords, filtering out non-sports categories such as news and movies.
     */
    fun isSportsCategory(rawCategory: String): Boolean {
        if (rawCategory.isBlank()) return false
        if (leagueFor(rawCategory) != null) return true

        val normalized = normalize(rawCategory)
        if (normalized.isEmpty()) return false

        return SPORTS_INDICATOR_PATTERNS.any { it.containsMatchIn(normalized) }
    }

    /**
     * Evaluates the search priority of [rawCategory] when resolving streams for a specific [league],
     * returning [CategoryPriority.DEDICATED_LEAGUE], [CategoryPriority.GENERAL_SPORTS], or [CategoryPriority.NON_SPORTS].
     */
    fun priority(rawCategory: String, league: League): CategoryPriority {
        return when {
            leagueFor(rawCategory) == league -> CategoryPriority.DEDICATED_LEAGUE
            isSportsCategory(rawCategory) -> CategoryPriority.GENERAL_SPORTS
            else -> CategoryPriority.NON_SPORTS
        }
    }
}
