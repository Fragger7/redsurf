package com.redsurf.tv.search

import java.text.Normalizer

/**
 * Pure relevance ranker for search candidate items.
 *
 * Why: Room's FTS4 virtual table does not provide relevance ranking, so candidate channels
 * matching a search query must be ordered by the application.
 *
 * Tiers (best first):
 * - Tier 0: exact (folded name == folded query)
 * - Tier 1: name starts with the query (folded name starts with folded query)
 * - Tier 2: every query word starts some name word, in order
 * - Tier 3: every query word starts some name word, any order
 * - Tier 4: everything else (candidates already filtered by FTS)
 *
 * Inside a tier, candidates are ordered by shorter folded name first, then folded name
 * alphabetically, preserving original list order for remaining ties (stable sort).
 */
object SearchRanker {

    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")

    internal fun fold(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        return normalized.replace(NON_ALNUM, " ").trim()
    }

    internal fun tier(query: String, name: String): Int {
        val foldedQuery = fold(query)
        val queryWords = foldedQuery.split(' ').filter { it.isNotEmpty() }
        val foldedName = fold(name)
        val nameWords = foldedName.split(' ').filter { it.isNotEmpty() }
        return tier(foldedQuery, queryWords, foldedName, nameWords)
    }

    private fun tier(
        foldedQuery: String,
        queryWords: List<String>,
        foldedName: String,
        nameWords: List<String>,
    ): Int {
        if (foldedName == foldedQuery) return 0
        if (foldedName.startsWith(foldedQuery)) return 1
        if (matchesInOrder(queryWords, nameWords)) return 2
        if (matchesAnyOrder(queryWords, nameWords)) return 3
        return 4
    }

    private fun matchesInOrder(queryWords: List<String>, nameWords: List<String>): Boolean {
        if (queryWords.isEmpty()) return true
        var nameIdx = 0
        for (qWord in queryWords) {
            var matched = false
            while (nameIdx < nameWords.size) {
                if (nameWords[nameIdx].startsWith(qWord)) {
                    matched = true
                    nameIdx++
                    break
                }
                nameIdx++
            }
            if (!matched) return false
        }
        return true
    }

    private fun matchesAnyOrder(queryWords: List<String>, nameWords: List<String>): Boolean {
        if (queryWords.isEmpty()) return true
        if (queryWords.size > nameWords.size) return false
        val used = BooleanArray(nameWords.size)
        fun match(qIdx: Int): Boolean {
            if (qIdx == queryWords.size) return true
            val qWord = queryWords[qIdx]
            for (i in nameWords.indices) {
                if (!used[i] && nameWords[i].startsWith(qWord)) {
                    used[i] = true
                    if (match(qIdx + 1)) return true
                    used[i] = false
                }
            }
            return false
        }
        return match(0)
    }

    fun <T> rank(query: String, items: List<T>, name: (T) -> String): List<T> {
        if (query.isBlank() || items.isEmpty()) return items.toList()
        val foldedQuery = fold(query)
        if (foldedQuery.isEmpty()) return items.toList()
        val queryWords = foldedQuery.split(' ').filter { it.isNotEmpty() }

        data class Candidate<T>(
            val item: T,
            val tier: Int,
            val foldedName: String,
        )

        val candidates = items.map { item ->
            val rawName = name(item)
            val foldedName = fold(rawName)
            val nameWords = foldedName.split(' ').filter { it.isNotEmpty() }
            val itemTier = tier(foldedQuery, queryWords, foldedName, nameWords)
            Candidate(item, itemTier, foldedName)
        }

        val comparator = compareBy<Candidate<T>> { it.tier }
            .thenBy { it.foldedName.length }
            .thenBy { it.foldedName }

        return candidates.sortedWith(comparator).map { it.item }
    }
}
