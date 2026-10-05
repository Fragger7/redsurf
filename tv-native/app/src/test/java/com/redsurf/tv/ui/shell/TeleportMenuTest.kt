package com.redsurf.tv.ui.shell

import com.redsurf.tv.data.FAVORITES_GROUP
import com.redsurf.tv.db.GroupCount
import com.redsurf.tv.ui.livetv.GroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeleportMenuTest {

    private val rows = listOf(
        TeleportRow.Live("Nav-Strip") {},
        TeleportRow.Grey("Playlist Favorites"),
        TeleportRow.Live("Root Category") {},
        TeleportRow.Grey("Multi-View"),
        TeleportRow.Live("Exit RedSurf") {},
    )

    @Test fun downSkipsGreyRows() = assertEquals(2, nextLiveIndex(rows, 0, 1))

    @Test fun upSkipsGreyRows() = assertEquals(2, nextLiveIndex(rows, 4, -1))

    @Test fun deadEndsStayPut() {
        assertEquals(0, nextLiveIndex(rows, 0, -1))
        assertEquals(4, nextLiveIndex(rows, 4, 1))
    }

    @Test fun firstLiveSkipsLeadingGrey() =
        assertEquals(1, firstLiveIndex(listOf(TeleportRow.Grey("a"), TeleportRow.Live("b") {})))

    @Test fun prefixUsesEarliestSeparator() {
        assertEquals("US", categoryPrefix("US|24/7 ACTION/ADVENTURE Raw 60fps"))
        assertEquals("US", categoryPrefix("US - NFL"))
        assertEquals("VIP", categoryPrefix("VIP | CHRISTMAS"))
        assertNull(categoryPrefix("Sports"))
    }

    @Test fun favoritesIsNeverAFamily() = assertNull(categoryPrefix(FAVORITES_GROUP))

    @Test fun rootCategoryIsFirstOfFamilyInSamePlaylist() {
        val groups = listOf(
            GroupCount("a", "A", FAVORITES_GROUP, 3),
            GroupCount("a", "A", "UK| NEWS", 5),
            GroupCount("a", "A", "US|24/7 ACTION/ADVENTURE Raw 60fps", 5),
            GroupCount("a", "A", "US| CBS", 5),
            GroupCount("b", "B", "US| ABC", 5),
        )
        assertEquals(
            GroupKey("a", "US|24/7 ACTION/ADVENTURE Raw 60fps"),
            resolveRootCategoryTarget(groups, GroupKey("a", "US| CBS")),
        )
        assertNull(resolveRootCategoryTarget(groups, GroupKey("a", FAVORITES_GROUP)))
    }
}

class LastGroupTargetTest {
    @org.junit.Test fun lastCategoryOfCurrentPlaylist() {
        val groups = listOf(
            GroupCount("a", "A", "AF | AFRICA", 1),
            GroupCount("a", "A", "US| CBS", 1),
            GroupCount("b", "B", "UK| BBC", 1),
        )
        org.junit.Assert.assertEquals(GroupKey("a", "US| CBS"), resolveLastGroupTarget(groups, "a"))
        org.junit.Assert.assertEquals(GroupKey("b", "UK| BBC"), resolveLastGroupTarget(groups, "b"))
        org.junit.Assert.assertNull(resolveLastGroupTarget(groups, null))
    }
}
