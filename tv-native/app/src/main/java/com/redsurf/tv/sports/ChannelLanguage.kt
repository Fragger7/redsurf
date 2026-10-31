package com.redsurf.tv.sports

/**
 * Guesses a channel's language from its parsed name and applies user language preferences
 * (`SPORTS_HUB.md` §3, Q6). Pure and deterministic: unknown languages are never hidden.
 */
object ChannelLanguage {

    private val spanishKeywords = setOf("deportes", "espanol", "español", "tudn", "telemundo", "univision")

    /**
     * Guesses the ISO language code (`"en"`, `"es"`, `"fr"`) for a [name], or null if unknown.
     * Spanish network keywords in the channel key take precedence over the country prefix.
     */
    fun of(name: ChannelName): String? {
        val words = name.key.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        if (words.any { it in spanishKeywords }) return "es"
        return when (name.country?.uppercase()) {
            "US", "UK", "CA", "AU" -> "en"
            "LAT", "MX", "ES" -> "es"
            "FR" -> "fr"
            else -> null
        }
    }

    /**
     * Checks whether [name] satisfies the [allowed] language set. Returns true if the language
     * is unknown (null), if [allowed] is empty, or if the detected language is in [allowed].
     */
    fun passes(name: ChannelName, allowed: Set<String>): Boolean {
        val lang = of(name)
        return lang == null || allowed.isEmpty() || lang in allowed
    }
}
