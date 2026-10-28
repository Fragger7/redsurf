package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Test

class EspnScoreboardUrlTest {

    @Test
    fun dayAndMonthUrls() {
        assertEquals(
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=20261028",
            EspnScoreboardUrl.forDay(League.NFL, "20261028"),
        )
        assertEquals(
            "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=202610&limit=500",
            EspnScoreboardUrl.forMonth(League.NBA, "202610"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rangeIsRejected() {
        EspnScoreboardUrl.forDay(League.NHL, "20261028-20261103")
    }
}
