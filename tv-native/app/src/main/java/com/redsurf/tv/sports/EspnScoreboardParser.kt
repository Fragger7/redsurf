package com.redsurf.tv.sports

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses one day's ESPN scoreboard (`site.api.espn.com/apis/site/v2/sports/{league}/scoreboard?dates=YYYYMMDD`)
 * into [Game]s. Pure: no Android types, so it runs on the JVM in unit tests.
 *
 * Events that lack an id, a parseable kickoff time or two competitors are skipped rather than
 * guessed at. Malformed JSON throws, so the caller can tell "no games" from "bad response".
 */
object EspnScoreboardParser {

    private val kickoffPatterns = listOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")

    fun parse(league: League, json: String): List<Game> {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return buildList {
            for (i in 0 until events.length()) {
                parseEvent(league, events.getJSONObject(i))?.let(::add)
            }
        }
    }

    private fun parseEvent(league: League, event: JSONObject): Game? {
        val id = event.optString("id").ifBlank { return null }
        val kickoff = parseKickoff(event.optString("date")) ?: return null
        val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val competitors = competition.optJSONArray("competitors") ?: return null

        var home: String? = null
        var away: String? = null
        for (i in 0 until competitors.length()) {
            val c = competitors.getJSONObject(i)
            val team = c.optJSONObject("team")?.optString("displayName").orEmpty()
            when (c.optString("homeAway")) {
                "home" -> home = team
                "away" -> away = team
            }
        }
        // Individual sports (UFC, boxing) have fighters, not home/away; they are handled in S2.
        if (home.isNullOrBlank() || away.isNullOrBlank()) return null

        val state = when (competition.optJSONObject("status")?.optJSONObject("type")?.optString("state")) {
            "pre" -> GameState.PRE
            "in" -> GameState.IN
            "post" -> GameState.POST
            else -> GameState.UNKNOWN
        }

        return Game(
            id = id,
            league = league,
            kickoffMillis = kickoff,
            name = event.optString("name"),
            homeTeam = home,
            awayTeam = away,
            state = state,
            networks = networksOf(competition),
        )
    }

    /** Broadcast names from both `broadcasts[].names[]` and `geoBroadcasts[].media.shortName`, de-duplicated in order. */
    private fun networksOf(competition: JSONObject): List<String> {
        val names = LinkedHashSet<String>()
        competition.optJSONArray("broadcasts")?.let { broadcasts ->
            for (i in 0 until broadcasts.length()) {
                broadcasts.getJSONObject(i).optJSONArray("names")?.let { addAll(names, it) }
            }
        }
        competition.optJSONArray("geoBroadcasts")?.let { geo ->
            for (i in 0 until geo.length()) {
                geo.optJSONObject(i)?.optJSONObject("media")?.optString("shortName")
                    ?.trim()?.takeIf { it.isNotEmpty() }?.let(names::add)
            }
        }
        return names.toList()
    }

    private fun addAll(into: MutableSet<String>, array: JSONArray) {
        for (i in 0 until array.length()) {
            array.optString(i).trim().takeIf { it.isNotEmpty() }?.let(into::add)
        }
    }

    /** ESPN sends `2026-10-25T13:30Z`; some endpoints add seconds. Formats are built per call: SimpleDateFormat is not thread-safe. */
    internal fun parseKickoff(raw: String): Long? {
        for (pattern in kickoffPatterns) {
            val format = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            format.isLenient = false
            try {
                return format.parse(raw)?.time
            } catch (_: java.text.ParseException) {
                // Try the next shape.
            }
        }
        return null
    }
}
