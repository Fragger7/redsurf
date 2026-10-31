package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec test for Task 023 (SPORTS_HUB.md §10, §3b). Do not edit; make it pass. */
class SportsCategoryMatcherTest {

    @Test
    fun dedicatedNflCategoriesRecognized() {
        assertEquals(League.NFL, SportsCategoryMatcher.leagueFor("USA | NFL GAME PASS HD"))
        assertEquals(League.NFL, SportsCategoryMatcher.leagueFor("US: NFL SUNDAY TICKET"))
        assertEquals(League.NFL, SportsCategoryMatcher.leagueFor("SPORTS - NFL PPV"))
        assertEquals(CategoryPriority.DEDICATED_LEAGUE, SportsCategoryMatcher.priority("USA | NFL GAME PASS HD", League.NFL))
    }

    @Test
    fun dedicatedNbaCategoriesRecognized() {
        assertEquals(League.NBA, SportsCategoryMatcher.leagueFor("US | NBA LEAGUE PASS"))
        assertEquals(League.NBA, SportsCategoryMatcher.leagueFor("VIP - NBA ᴴᴰ"))
        assertEquals(CategoryPriority.DEDICATED_LEAGUE, SportsCategoryMatcher.priority("US | NBA LEAGUE PASS", League.NBA))
    }

    @Test
    fun dedicatedMlbCategoriesRecognized() {
        assertEquals(League.MLB, SportsCategoryMatcher.leagueFor("USA: MLB EXTRA INNINGS"))
        assertEquals(League.MLB, SportsCategoryMatcher.leagueFor("US | MLB PACK"))
        assertEquals(CategoryPriority.DEDICATED_LEAGUE, SportsCategoryMatcher.priority("USA: MLB EXTRA INNINGS", League.MLB))
    }

    @Test
    fun dedicatedNhlCategoriesRecognized() {
        assertEquals(League.NHL, SportsCategoryMatcher.leagueFor("CA | NHL CENTER ICE 4K"))
        assertEquals(League.NHL, SportsCategoryMatcher.leagueFor("US - NHL HOCKEY"))
        assertEquals(CategoryPriority.DEDICATED_LEAGUE, SportsCategoryMatcher.priority("CA | NHL CENTER ICE 4K", League.NHL))
    }

    @Test
    fun dedicatedUfcCategoriesRecognized() {
        assertEquals(League.UFC, SportsCategoryMatcher.leagueFor("PPV / UFC FIGHT PASS"))
        assertEquals(League.UFC, SportsCategoryMatcher.leagueFor("US | MMA LIVE"))
        assertEquals(CategoryPriority.DEDICATED_LEAGUE, SportsCategoryMatcher.priority("PPV / UFC FIGHT PASS", League.UFC))
    }

    @Test
    fun generalSportsCategories() {
        val nfl = League.NFL
        assertNull(SportsCategoryMatcher.leagueFor("US | NBC SPORTS REGIONALS"))
        assertTrue(SportsCategoryMatcher.isSportsCategory("US | NBC SPORTS REGIONALS"))
        assertEquals(CategoryPriority.GENERAL_SPORTS, SportsCategoryMatcher.priority("US | NBC SPORTS REGIONALS", nfl))

        assertNull(SportsCategoryMatcher.leagueFor("UK | SKY SPORTS HD"))
        assertTrue(SportsCategoryMatcher.isSportsCategory("UK | SKY SPORTS HD"))
        assertEquals(CategoryPriority.GENERAL_SPORTS, SportsCategoryMatcher.priority("UK | SKY SPORTS HD", nfl))

        assertNull(SportsCategoryMatcher.leagueFor("ES | DEPORTES"))
        assertTrue(SportsCategoryMatcher.isSportsCategory("ES | DEPORTES"))
        assertEquals(CategoryPriority.GENERAL_SPORTS, SportsCategoryMatcher.priority("ES | DEPORTES", League.NBA))

        assertNull(SportsCategoryMatcher.leagueFor("LIVE EVENTS & PPV"))
        assertTrue(SportsCategoryMatcher.isSportsCategory("LIVE EVENTS & PPV"))
        assertEquals(CategoryPriority.GENERAL_SPORTS, SportsCategoryMatcher.priority("LIVE EVENTS & PPV", League.MLB))

        assertNull(SportsCategoryMatcher.leagueFor("BEIN SPORTS FHD"))
        assertTrue(SportsCategoryMatcher.isSportsCategory("BEIN SPORTS FHD"))
        assertEquals(CategoryPriority.GENERAL_SPORTS, SportsCategoryMatcher.priority("BEIN SPORTS FHD", League.NHL))
    }

    @Test
    fun crossLeaguePriorityIsGeneralSports() {
        // NFL dedicated group evaluated for NBA search is GENERAL_SPORTS, not DEDICATED_LEAGUE or NON_SPORTS
        assertEquals(CategoryPriority.GENERAL_SPORTS, SportsCategoryMatcher.priority("USA | NFL SUNDAY TICKET", League.NBA))
        assertEquals(CategoryPriority.GENERAL_SPORTS, SportsCategoryMatcher.priority("US | NBA LEAGUE PASS", League.NHL))
    }

    @Test
    fun nonSportsCategoriesClassifiedAsNonSports() {
        assertFalse(SportsCategoryMatcher.isSportsCategory("USA | CINEMA & MOVIES"))
        assertEquals(CategoryPriority.NON_SPORTS, SportsCategoryMatcher.priority("USA | CINEMA & MOVIES", League.NFL))

        assertFalse(SportsCategoryMatcher.isSportsCategory("US | LOCAL NEWS HD"))
        assertEquals(CategoryPriority.NON_SPORTS, SportsCategoryMatcher.priority("US | LOCAL NEWS HD", League.NBA))

        assertFalse(SportsCategoryMatcher.isSportsCategory("FR | DOCUMENTAIRES"))
        assertEquals(CategoryPriority.NON_SPORTS, SportsCategoryMatcher.priority("FR | DOCUMENTAIRES", League.MLB))

        assertFalse(SportsCategoryMatcher.isSportsCategory("KIDS & FAMILY"))
        assertEquals(CategoryPriority.NON_SPORTS, SportsCategoryMatcher.priority("KIDS & FAMILY", League.NHL))

        assertFalse(SportsCategoryMatcher.isSportsCategory("VIP | MUSIC HITS"))
        assertEquals(CategoryPriority.NON_SPORTS, SportsCategoryMatcher.priority("VIP | MUSIC HITS", League.UFC))
    }

    @Test
    fun emptyOrBlankCategoryIsNonSports() {
        assertNull(SportsCategoryMatcher.leagueFor(""))
        assertFalse(SportsCategoryMatcher.isSportsCategory(""))
        assertEquals(CategoryPriority.NON_SPORTS, SportsCategoryMatcher.priority("", League.NFL))

        assertNull(SportsCategoryMatcher.leagueFor("   "))
        assertFalse(SportsCategoryMatcher.isSportsCategory("   "))
        assertEquals(CategoryPriority.NON_SPORTS, SportsCategoryMatcher.priority("   ", League.NFL))
    }
}
