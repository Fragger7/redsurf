package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lead-written spec for task 009 (SPORTS_HUB.md §3b). Do not edit; make it pass. */
class EpgTeamMatchTest {

    private val eagles = "Philadelphia Eagles"
    private val cowboys = "Dallas Cowboys"

    @Test fun tokensAreLowercaseWordsOfAtLeastFourLetters() {
        assertEquals(setOf("philadelphia", "eagles"), TeamTokens.of(eagles, cowboys))
        assertEquals(setOf("boston"), TeamTokens.of("Boston Red Sox", "New York Yankees"))
    }

    @Test fun tokensDropWordsSharedWithTheOpponent() {
        assertEquals(setOf("lakers"), TeamTokens.of("Los Angeles Lakers", "Los Angeles Clippers"))
        assertEquals(setOf("yankees"), TeamTokens.of("New York Yankees", "New York Mets"))
    }

    @Test fun tokensDropStopwords() {
        assertEquals(setOf("kansas", "chiefs"), TeamTokens.of("Kansas City Chiefs", "Denver Broncos"))
        assertEquals(setOf("minnesota"), TeamTokens.of("Minnesota United FC", "Seattle Sounders"))
    }

    @Test fun tokensFoldPunctuationAndCase() {
        assertEquals(setOf("blazers", "portland", "trail"), TeamTokens.of("PORTLAND TRAIL-BLAZERS", "Utah Jazz"))
    }

    @Test fun bothTeamsInTitleIsEpgTitle() {
        assertEquals(MatchTier.EPG_TITLE, EpgTeamMatch.match("NFL Football: Eagles at Cowboys", null, null, eagles, cowboys))
    }

    @Test fun bothTeamsInSubtitleIsEpgTitle() {
        assertEquals(
            MatchTier.EPG_TITLE,
            EpgTeamMatch.match("NFL Football", "Philadelphia Eagles at Dallas Cowboys", "Week 9.", eagles, cowboys),
        )
    }

    @Test fun teamsSplitAcrossTitleAndSubtitleIsEpgTitle() {
        assertEquals(MatchTier.EPG_TITLE, EpgTeamMatch.match("Eagles Football", "vs. Dallas", null, eagles, cowboys))
    }

    @Test fun bothTeamsOnlyWithDescriptionIsEpgDescription() {
        assertEquals(
            MatchTier.EPG_DESCRIPTION,
            EpgTeamMatch.match("NFL Football", null, "The Eagles visit the Cowboys in Arlington.", eagles, cowboys),
        )
    }

    @Test fun oneTeamOnlyIsNoMatch() {
        assertNull(EpgTeamMatch.match("Eagles Postgame Live", "Eagles news", "All about the Eagles.", eagles, cowboys))
    }

    @Test fun tokensMatchWholeWordsOnly() {
        assertNull(EpgTeamMatch.match("Eaglesnest Cowboys Show", null, null, eagles, cowboys))
    }

    @Test fun matchFoldsCaseAndPunctuation() {
        assertEquals(MatchTier.EPG_TITLE, EpgTeamMatch.match("EAGLES-COWBOYS", null, null, eagles, cowboys))
    }

    @Test fun sharedCityAloneDoesNotMatch() {
        val lakers = "Los Angeles Lakers"
        val clippers = "Los Angeles Clippers"
        assertNull(EpgTeamMatch.match("Los Angeles Basketball Tonight", null, null, lakers, clippers))
        assertEquals(MatchTier.EPG_TITLE, EpgTeamMatch.match("NBA: Clippers vs Lakers", null, null, lakers, clippers))
    }

    @Test fun teamWithNoTokensNeverMatches() {
        assertNull(EpgTeamMatch.match("Red Sox vs Jays", null, null, "Red Sox", "Jays"))
    }
}
