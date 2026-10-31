package com.redsurf.tv.sports

import java.text.Normalizer

/**
 * Canonical metadata for a sports team in a covered league, holding its ESPN abbreviation [code],
 * parent [league], home [city], short [name], and full franchise [fullName].
 */
data class Team(
    val code: String,
    val league: League,
    val city: String,
    val name: String,
    val fullName: String,
)

/**
 * Directory of canonical sports teams across the major covered leagues (NFL, NBA, MLB, NHL)
 * and home market rosters, providing name and abbreviation lookup, ESPN CDN logo URLs, and
 * favorite-team matching per SPORTS_HUB.md §2, §5 C, §9.1, and §10.
 */
object TeamDirectory {

    private data class TeamEntry(
        val team: Team,
        val aliases: List<String> = emptyList(),
    )

    private val philadelphiaEagles = Team("phi", League.NFL, "Philadelphia", "Eagles", "Philadelphia Eagles")
    private val philadelphia76ers = Team("phi", League.NBA, "Philadelphia", "76ers", "Philadelphia 76ers")
    private val philadelphiaPhillies = Team("phi", League.MLB, "Philadelphia", "Phillies", "Philadelphia Phillies")
    private val philadelphiaFlyers = Team("phi", League.NHL, "Philadelphia", "Flyers", "Philadelphia Flyers")

    private val nflTeams = listOf(
        TeamEntry(philadelphiaEagles, listOf("birds")),
        TeamEntry(Team("kc", League.NFL, "Kansas City", "Chiefs", "Kansas City Chiefs")),
        TeamEntry(Team("dal", League.NFL, "Dallas", "Cowboys", "Dallas Cowboys")),
        TeamEntry(Team("sf", League.NFL, "San Francisco", "49ers", "San Francisco 49ers"), listOf("niners", "forty niners")),
        TeamEntry(Team("buf", League.NFL, "Buffalo", "Bills", "Buffalo Bills")),
        TeamEntry(Team("gb", League.NFL, "Green Bay", "Packers", "Green Bay Packers")),
        TeamEntry(Team("ari", League.NFL, "Arizona", "Cardinals", "Arizona Cardinals"), listOf("cards")),
        TeamEntry(Team("atl", League.NFL, "Atlanta", "Falcons", "Atlanta Falcons")),
        TeamEntry(Team("bal", League.NFL, "Baltimore", "Ravens", "Baltimore Ravens")),
        TeamEntry(Team("car", League.NFL, "Carolina", "Panthers", "Carolina Panthers")),
        TeamEntry(Team("chi", League.NFL, "Chicago", "Bears", "Chicago Bears")),
        TeamEntry(Team("cin", League.NFL, "Cincinnati", "Bengals", "Cincinnati Bengals")),
        TeamEntry(Team("cle", League.NFL, "Cleveland", "Browns", "Cleveland Browns")),
        TeamEntry(Team("den", League.NFL, "Denver", "Broncos", "Denver Broncos")),
        TeamEntry(Team("det", League.NFL, "Detroit", "Lions", "Detroit Lions")),
        TeamEntry(Team("hou", League.NFL, "Houston", "Texans", "Houston Texans")),
        TeamEntry(Team("ind", League.NFL, "Indianapolis", "Colts", "Indianapolis Colts")),
        TeamEntry(Team("jax", League.NFL, "Jacksonville", "Jaguars", "Jacksonville Jaguars"), listOf("jac")),
        TeamEntry(Team("lv", League.NFL, "Las Vegas", "Raiders", "Las Vegas Raiders"), listOf("oak", "oakland raiders")),
        TeamEntry(Team("lac", League.NFL, "Los Angeles", "Chargers", "Los Angeles Chargers"), listOf("sd", "san diego chargers")),
        TeamEntry(Team("lar", League.NFL, "Los Angeles", "Rams", "Los Angeles Rams"), listOf("la rams")),
        TeamEntry(Team("mia", League.NFL, "Miami", "Dolphins", "Miami Dolphins")),
        TeamEntry(Team("min", League.NFL, "Minnesota", "Vikings", "Minnesota Vikings")),
        TeamEntry(Team("ne", League.NFL, "New England", "Patriots", "New England Patriots"), listOf("pats")),
        TeamEntry(Team("no", League.NFL, "New Orleans", "Saints", "New Orleans Saints")),
        TeamEntry(Team("nyg", League.NFL, "New York", "Giants", "New York Giants")),
        TeamEntry(Team("nyj", League.NFL, "New York", "Jets", "New York Jets")),
        TeamEntry(Team("pit", League.NFL, "Pittsburgh", "Steelers", "Pittsburgh Steelers")),
        TeamEntry(Team("sea", League.NFL, "Seattle", "Seahawks", "Seattle Seahawks")),
        TeamEntry(Team("tb", League.NFL, "Tampa Bay", "Buccaneers", "Tampa Bay Buccaneers"), listOf("bucs")),
        TeamEntry(Team("ten", League.NFL, "Tennessee", "Titans", "Tennessee Titans")),
        TeamEntry(Team("wsh", League.NFL, "Washington", "Commanders", "Washington Commanders"), listOf("was", "washington football team", "redskins")),
    )

    private val nbaTeams = listOf(
        TeamEntry(philadelphia76ers, listOf("sixers")),
        TeamEntry(Team("bos", League.NBA, "Boston", "Celtics", "Boston Celtics")),
        TeamEntry(Team("lal", League.NBA, "Los Angeles", "Lakers", "Los Angeles Lakers")),
        TeamEntry(Team("gsw", League.NBA, "Golden State", "Warriors", "Golden State Warriors"), listOf("dubs")),
        TeamEntry(Team("mil", League.NBA, "Milwaukee", "Bucks", "Milwaukee Bucks")),
        TeamEntry(Team("atl", League.NBA, "Atlanta", "Hawks", "Atlanta Hawks")),
        TeamEntry(Team("bkn", League.NBA, "Brooklyn", "Nets", "Brooklyn Nets"), listOf("nj nets", "new jersey nets")),
        TeamEntry(Team("cha", League.NBA, "Charlotte", "Hornets", "Charlotte Hornets"), listOf("bobcats")),
        TeamEntry(Team("chi", League.NBA, "Chicago", "Bulls", "Chicago Bulls")),
        TeamEntry(Team("cle", League.NBA, "Cleveland", "Cavaliers", "Cleveland Cavaliers"), listOf("cavs")),
        TeamEntry(Team("dal", League.NBA, "Dallas", "Mavericks", "Dallas Mavericks"), listOf("mavs")),
        TeamEntry(Team("den", League.NBA, "Denver", "Nuggets", "Denver Nuggets")),
        TeamEntry(Team("det", League.NBA, "Detroit", "Pistons", "Detroit Pistons")),
        TeamEntry(Team("hou", League.NBA, "Houston", "Rockets", "Houston Rockets")),
        TeamEntry(Team("ind", League.NBA, "Indiana", "Pacers", "Indiana Pacers")),
        TeamEntry(Team("lac", League.NBA, "Los Angeles", "Clippers", "Los Angeles Clippers"), listOf("la clippers")),
        TeamEntry(Team("mem", League.NBA, "Memphis", "Grizzlies", "Memphis Grizzlies")),
        TeamEntry(Team("mia", League.NBA, "Miami", "Heat", "Miami Heat")),
        TeamEntry(Team("min", League.NBA, "Minnesota", "Timberwolves", "Minnesota Timberwolves"), listOf("t-wolves", "wolves")),
        TeamEntry(Team("no", League.NBA, "New Orleans", "Pelicans", "New Orleans Pelicans"), listOf("nop", "pels")),
        TeamEntry(Team("nyk", League.NBA, "New York", "Knicks", "New York Knicks"), listOf("ny knicks", "knickerbockers")),
        TeamEntry(Team("okc", League.NBA, "Oklahoma City", "Thunder", "Oklahoma City Thunder")),
        TeamEntry(Team("orl", League.NBA, "Orlando", "Magic", "Orlando Magic")),
        TeamEntry(Team("phx", League.NBA, "Phoenix", "Suns", "Phoenix Suns")),
        TeamEntry(Team("por", League.NBA, "Portland", "Trail Blazers", "Portland Trail Blazers"), listOf("blazers")),
        TeamEntry(Team("sac", League.NBA, "Sacramento", "Kings", "Sacramento Kings")),
        TeamEntry(Team("sa", League.NBA, "San Antonio", "Spurs", "San Antonio Spurs"), listOf("sas")),
        TeamEntry(Team("tor", League.NBA, "Toronto", "Raptors", "Toronto Raptors")),
        TeamEntry(Team("uta", League.NBA, "Utah", "Jazz", "Utah Jazz")),
        TeamEntry(Team("wsh", League.NBA, "Washington", "Wizards", "Washington Wizards"), listOf("was")),
    )

    private val mlbTeams = listOf(
        TeamEntry(philadelphiaPhillies, listOf("phils")),
        TeamEntry(Team("nyy", League.MLB, "New York", "Yankees", "New York Yankees"), listOf("bronx bombers")),
        TeamEntry(Team("lad", League.MLB, "Los Angeles", "Dodgers", "Los Angeles Dodgers")),
        TeamEntry(Team("bos", League.MLB, "Boston", "Red Sox", "Boston Red Sox"), listOf("sox")),
        TeamEntry(Team("atl", League.MLB, "Atlanta", "Braves", "Atlanta Braves")),
        TeamEntry(Team("ari", League.MLB, "Arizona", "Diamondbacks", "Arizona Diamondbacks"), listOf("dbacks", "d-backs")),
        TeamEntry(Team("bal", League.MLB, "Baltimore", "Orioles", "Baltimore Orioles"), listOf("o's", "os")),
        TeamEntry(Team("chc", League.MLB, "Chicago", "Cubs", "Chicago Cubs")),
        TeamEntry(Team("cws", League.MLB, "Chicago", "White Sox", "Chicago White Sox"), listOf("chw")),
        TeamEntry(Team("cin", League.MLB, "Cincinnati", "Reds", "Cincinnati Reds")),
        TeamEntry(Team("cle", League.MLB, "Cleveland", "Guardians", "Cleveland Guardians"), listOf("indians")),
        TeamEntry(Team("col", League.MLB, "Colorado", "Rockies", "Colorado Rockies")),
        TeamEntry(Team("det", League.MLB, "Detroit", "Tigers", "Detroit Tigers")),
        TeamEntry(Team("hou", League.MLB, "Houston", "Astros", "Houston Astros"), listOf("stros")),
        TeamEntry(Team("kc", League.MLB, "Kansas City", "Royals", "Kansas City Royals")),
        TeamEntry(Team("laa", League.MLB, "Los Angeles", "Angels", "Los Angeles Angels"), listOf("anaheim angels")),
        TeamEntry(Team("mia", League.MLB, "Miami", "Marlins", "Miami Marlins"), listOf("florida marlins")),
        TeamEntry(Team("mil", League.MLB, "Milwaukee", "Brewers", "Milwaukee Brewers"), listOf("brew crew")),
        TeamEntry(Team("min", League.MLB, "Minnesota", "Twins", "Minnesota Twins")),
        TeamEntry(Team("nym", League.MLB, "New York", "Mets", "New York Mets")),
        TeamEntry(Team("oak", League.MLB, "Oakland", "Athletics", "Oakland Athletics"), listOf("a's", "as")),
        TeamEntry(Team("pit", League.MLB, "Pittsburgh", "Pirates", "Pittsburgh Pirates")),
        TeamEntry(Team("sd", League.MLB, "San Diego", "Padres", "San Diego Padres")),
        TeamEntry(Team("sf", League.MLB, "San Francisco", "Giants", "San Francisco Giants")),
        TeamEntry(Team("sea", League.MLB, "Seattle", "Mariners", "Seattle Mariners"), listOf("m's", "ms")),
        TeamEntry(Team("stl", League.MLB, "St. Louis", "Cardinals", "St. Louis Cardinals"), listOf("cards")),
        TeamEntry(Team("tb", League.MLB, "Tampa Bay", "Rays", "Tampa Bay Rays"), listOf("devil rays")),
        TeamEntry(Team("tex", League.MLB, "Texas", "Rangers", "Texas Rangers")),
        TeamEntry(Team("tor", League.MLB, "Toronto", "Blue Jays", "Toronto Blue Jays"), listOf("jays")),
        TeamEntry(Team("wsh", League.MLB, "Washington", "Nationals", "Washington Nationals"), listOf("nats", "was")),
    )

    private val nhlTeams = listOf(
        TeamEntry(philadelphiaFlyers),
        TeamEntry(Team("edm", League.NHL, "Edmonton", "Oilers", "Edmonton Oilers")),
        TeamEntry(Team("bos", League.NHL, "Boston", "Bruins", "Boston Bruins")),
        TeamEntry(Team("nyr", League.NHL, "New York", "Rangers", "New York Rangers")),
        TeamEntry(Team("tor", League.NHL, "Toronto", "Maple Leafs", "Toronto Maple Leafs"), listOf("leafs")),
        TeamEntry(Team("ana", League.NHL, "Anaheim", "Ducks", "Anaheim Ducks"), listOf("mighty ducks")),
        TeamEntry(Team("buf", League.NHL, "Buffalo", "Sabres", "Buffalo Sabres")),
        TeamEntry(Team("cgy", League.NHL, "Calgary", "Flames", "Calgary Flames")),
        TeamEntry(Team("car", League.NHL, "Carolina", "Hurricanes", "Carolina Hurricanes"), listOf("canes")),
        TeamEntry(Team("chi", League.NHL, "Chicago", "Blackhawks", "Chicago Blackhawks"), listOf("hawks")),
        TeamEntry(Team("col", League.NHL, "Colorado", "Avalanche", "Colorado Avalanche"), listOf("avs")),
        TeamEntry(Team("cbj", League.NHL, "Columbus", "Blue Jackets", "Columbus Blue Jackets"), listOf("jackets")),
        TeamEntry(Team("dal", League.NHL, "Dallas", "Stars", "Dallas Stars")),
        TeamEntry(Team("det", League.NHL, "Detroit", "Red Wings", "Detroit Red Wings"), listOf("wings")),
        TeamEntry(Team("fla", League.NHL, "Florida", "Panthers", "Florida Panthers"), listOf("cats")),
        TeamEntry(Team("la", League.NHL, "Los Angeles", "Kings", "Los Angeles Kings"), listOf("lak")),
        TeamEntry(Team("min", League.NHL, "Minnesota", "Wild", "Minnesota Wild")),
        TeamEntry(Team("mtl", League.NHL, "Montreal", "Canadiens", "Montreal Canadiens"), listOf("habs")),
        TeamEntry(Team("nsh", League.NHL, "Nashville", "Predators", "Nashville Predators"), listOf("preds")),
        TeamEntry(Team("nj", League.NHL, "New Jersey", "Devils", "New Jersey Devils"), listOf("njd")),
        TeamEntry(Team("nyi", League.NHL, "New York", "Islanders", "New York Islanders"), listOf("isles")),
        TeamEntry(Team("ott", League.NHL, "Ottawa", "Senators", "Ottawa Senators"), listOf("sens")),
        TeamEntry(Team("pit", League.NHL, "Pittsburgh", "Penguins", "Pittsburgh Penguins"), listOf("pens")),
        TeamEntry(Team("sj", League.NHL, "San Jose", "Sharks", "San Jose Sharks"), listOf("sjs")),
        TeamEntry(Team("sea", League.NHL, "Seattle", "Kraken", "Seattle Kraken")),
        TeamEntry(Team("stl", League.NHL, "St. Louis", "Blues", "St. Louis Blues")),
        TeamEntry(Team("tb", League.NHL, "Tampa Bay", "Lightning", "Tampa Bay Lightning"), listOf("tbl", "bolts")),
        TeamEntry(Team("uta", League.NHL, "Utah", "Hockey Club", "Utah Hockey Club"), listOf("coyotes", "arizona coyotes")),
        TeamEntry(Team("van", League.NHL, "Vancouver", "Canucks", "Vancouver Canucks"), listOf("nucks")),
        TeamEntry(Team("vgk", League.NHL, "Vegas", "Golden Knights", "Vegas Golden Knights"), listOf("las vegas golden knights", "knights")),
        TeamEntry(Team("wsh", League.NHL, "Washington", "Capitals", "Washington Capitals"), listOf("caps", "was")),
        TeamEntry(Team("wpg", League.NHL, "Winnipeg", "Jets", "Winnipeg Jets")),
    )

    private val allEntries = nflTeams + nbaTeams + mlbTeams + nhlTeams

    private val lookupMap: Map<Pair<League, String>, Team> = buildMap {
        for (entry in allEntries) {
            val league = entry.team.league
            val keys = mutableListOf(
                entry.team.code,
                entry.team.name,
                entry.team.fullName,
            )
            keys.addAll(entry.aliases)
            for (key in keys) {
                put(league to normalize(key), entry.team)
            }
        }
    }

    private fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase().trim()

    /**
     * Resolves a team within [league] from its abbreviation code, short nickname, or full franchise
     * name in [rawName] using NFKC normalization, returning null if unrecognized or for unsupported leagues.
     */
    fun find(league: League, rawName: String): Team? {
        if (league == League.UFC) return null
        val key = normalize(rawName)
        if (key.isEmpty()) return null
        return lookupMap[league to key]
    }

    /**
     * Returns the 500x500 PNG logo CDN URL from ESPN for [rawName] in [league],
     * or null if the team cannot be identified or belongs to an unsupported league.
     */
    fun logoUrl(league: League, rawName: String): String? {
        val team = find(league, rawName) ?: return null
        return "https://a.espncdn.com/i/teamlogos/${league.name.lowercase()}/500/${team.code}.png"
    }

    /**
     * Returns the list of canonical professional teams representing the specified [market]
     * (e.g. "philadelphia" returning the Eagles, 76ers, Phillies, and Flyers), or an empty list if unknown.
     */
    fun marketTeams(market: String): List<Team> {
        return when (normalize(market)) {
            "philadelphia" -> listOf(
                philadelphiaEagles,
                philadelphia76ers,
                philadelphiaPhillies,
                philadelphiaFlyers,
            )
            else -> emptyList()
        }
    }

    /**
     * Checks whether [rawName] in [league] corresponds to any team code, nickname, or full franchise name
     * present in the [followed] set (case-insensitively).
     */
    fun isFollowed(league: League, rawName: String, followed: Set<String>): Boolean {
        val team = find(league, rawName) ?: return false
        val normalizedFollowed = followed.map { normalize(it) }.toSet()
        if (normalize(team.code) in normalizedFollowed) return true
        if (normalize(team.name) in normalizedFollowed) return true
        if (normalize(team.fullName) in normalizedFollowed) return true
        return followed.any { item ->
            find(league, item) == team
        }
    }
}
