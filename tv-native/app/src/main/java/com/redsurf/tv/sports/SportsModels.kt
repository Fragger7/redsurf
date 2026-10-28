package com.redsurf.tv.sports

/** The leagues Sports Hub covers, with the ESPN path segment each one scoreboard lives under. */
enum class League(val espnPath: String) {
    NFL("football/nfl"),
    NBA("basketball/nba"),
    MLB("baseball/mlb"),
    NHL("hockey/nhl"),
    UFC("mma/ufc"),
}

/** ESPN's `competitions[0].status.type.state`. Anything unrecognised is [UNKNOWN], never guessed. */
enum class GameState { PRE, IN, POST, UNKNOWN }

/**
 * One scheduled game as ESPN reports it. [networks] is the de-duplicated list of broadcast names
 * (national TV and any geo-specific listings), exactly as ESPN spells them; matching normalises
 * them later. [kickoffMillis] is UTC epoch milliseconds.
 */
data class Game(
    val id: String,
    val league: League,
    val kickoffMillis: Long,
    val name: String,
    val homeTeam: String,
    val awayTeam: String,
    val state: GameState,
    val networks: List<String>,
)

/**
 * ESPN scoreboard URLs. Date ranges (`YYYYMMDD-YYYYMMDD`) return HTTP 400 for every league
 * (tested 2026-10-28, `SPORTS_HUB.md` open questions), so only a single day or a whole month is
 * built; the month query needs `limit` or ESPN stops at 100 events.
 */
object EspnScoreboardUrl {

    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports"
    const val MONTH_LIMIT = 500

    /** One day's games, [day] as `yyyyMMdd` in US Eastern (ESPN's own day boundary). */
    fun forDay(league: League, day: String): String {
        require(day.length == 8 && day.all { it.isDigit() }) { "day must be yyyyMMdd: $day" }
        return "$BASE/${league.espnPath}/scoreboard?dates=$day"
    }

    /** A whole month's games, [month] as `yyyyMM`. */
    fun forMonth(league: League, month: String): String {
        require(month.length == 6 && month.all { it.isDigit() }) { "month must be yyyyMM: $month" }
        return "$BASE/${league.espnPath}/scoreboard?dates=$month&limit=$MONTH_LIMIT"
    }
}
