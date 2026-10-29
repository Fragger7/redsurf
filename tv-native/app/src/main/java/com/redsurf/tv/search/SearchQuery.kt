package com.redsurf.tv.search

import java.text.Normalizer

/**
 * Turns user-typed search text into an FTS4 prefix query for Room's FTS table,
 * matching one prefix per word (e.g. `sky spo` -> `sky* spo*`). Prefix-per-word
 * allows fast search-as-you-type on TV remotes while stripping FTS operator syntax.
 *
 * Example: `sky spo` → `sky* spo*`
 */
object SearchQuery {

    private val wordBoundary = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Converts raw search input to an FTS4 match query with prefix matching on each word,
     * keeping at most the first 6 words, or returns null if no valid alphanumeric words remain.
     */
    fun toFtsMatch(raw: String): String? {
        // Lowercasing matters: FTS4 treats upper-case OR/AND/NOT/NEAR as operators.
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase()
        val words = normalized.split(wordBoundary).filter { it.isNotEmpty() }.take(6)
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "$it*" }
    }
}
