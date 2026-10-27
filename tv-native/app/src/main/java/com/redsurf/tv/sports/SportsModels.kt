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
