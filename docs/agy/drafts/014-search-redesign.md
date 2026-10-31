# Search Redesign — Build Brief

**Task:** 014 (Draft)  
**Author:** Builder (Gemini)  
**Target:** Search Screen Redesign (`docs/plans/SEARCH.md`, `TIVIMATE_SEARCH_STUDY.md`, `TESTING_OUTSTANDING.md` §O)  
**Date:** 2026-10-31  

---

## 1. TiviMate Search-Related Strings & Architectural Implications

Every string below was extracted from `/Users/Shared/tivimate-res/strings.xml` and corroborated against `TIVIMATE_SEARCH_STUDY.md` and the decompiled TiviMate APK:

| Resource Name / ID | English Text | Context & Implication for RedSurf |
|---|---|---|
| `_4nb_res_0x7f13022e` | `Search` | Main navigation destination title and search screen top header. |
| `_3s4_res_0x7f130012` | `Search…` | Action prompt in menu items (e.g. Teleport row or contextual action). |
| `_45f_res_0x7f130015` | `Search` | Action button / submit verb. |
| `t1` | `Voice search` | Accessibility description and modal header for voice search speech dialog. |
| `lb_search_bar_hint` | `Search` | Leanback search bar hint text displayed when input field is active and focused. |
| `_2hq_res_0x7f130109` | `Speak to search` | Idle hint inside search bar before typing, encouraging voice remote usage. |
| `_1bn_res_0x7f13010a` | `Search %1$s` | Scoped search within a specific playlist or category (future extension). |
| `_4le_res_0x7f13010b` | `Speak to search %1$s` | Scoped voice search within a specific playlist or category. |
| `_3k_res_0x7f13019e` | `Search Action` | Remote key mapping description (`KEYCODE_SEARCH`). |
| `_4mr_res_0x7f1301cc` | `Search and add` | Used in channel/playlist management dialogues. |
| `_3e3_res_0x7f13022f` | `Channels` | Section title for Live TV channel results. |
| `_3k6_res_0x7f130234` | `Programs` | Section title for EPG program results. |
| `_3tq_res_0x7f13047e` | `Movies` | Section title for VOD Movies (**Deferred**: No VOD support in RedSurf yet). |
| `_3v4_res_0x7f130485` | `Shows` | Section title for VOD TV Shows (**Deferred**: No VOD support in RedSurf yet). |
| `_3gu_res_0x7f130230` | `Search history` | Header label above recent search query chips when query input is blank. |
| `_33p_res_0x7f130232` | `Nothing found` | Empty state notice displayed when a non-blank query yields 0 matches. |
| `_2ss_res_0x7f1301af` | `Clear history?` | Dialog/prompt header for wiping search history chips. |
| `_2s2_res_0x7f1301ca` | `Remove from History` | Context action / tooltip on removing an individual recent query chip. |
| `_2bi_res_0x7f130233` | `Prefer voice search` | User preference: lands focus on the mic button instead of text field on entry. |
| `_4ji_res_0x7f130236` | `Show search history` | User preference: toggle whether recent search chips appear when query is blank. |
| `ci` (0x7f130238) | `Stay on search screen when switching channels` | User preference: selecting a channel result with OK starts playback in a translucent/preview panel while retaining the search results UI and card focus. |
| `_7v_res_0x7f130235` | `Show favorite channels first` | Ranking preference: prioritizes favorited channels in candidate ranking. |
| `_2jc_res_0x7f130237` | `Show past programs without catch-up` | Filter setting for EPG search results. |
| `p6` | `Displaying first %d channels. Use Groups or Search to find more.` | Result bounding notice enforcing memory discipline (RedSurf caps at 50). |
| `_3im_res_0x7f130412` | `Add to Favorites` | Result card long-press menu action when channel is not currently favorited. |
| `_43p_res_0x7f130446` | `Remove from Favorites` | Result card long-press menu action when channel is already favorited. |
| `nh` | `Open in external player` | Result card long-press secondary action in TiviMate. |

---

## 2. Scope & Feature Specifications

### 2.1 Branded Mic Icon Replacing "Voice" Pill
- **Current State:** `SearchScreen.kt:227` uses `CrestButton(label = "Voice", onClick = ...)` — a wide rectangular pill with text.
- **Design:** Circular icon button (~44x44dp) using RedSurf's Crest focus system.
  - Resting: Dark elevated surface (`SurfaceRaised`), containing a crisp vector microphone icon (`ic_mic`) tinted `TextPrimary`.
  - Focused: Solid red fill (`Accent`), matching the Crest button focus token, no scale or halo.
  - Availability: If `RecognizerIntent` is not resolved by `PackageManager`, the mic icon is omitted cleanly without leaving blank space.
- **Focus Rule:** When present, spatial navigation between the search text box and mic button is horizontal: `DPAD_RIGHT` from mic moves into the search text field; `DPAD_LEFT` from the search text field moves to the mic button. `DPAD_DOWN` from either moves into the first recent chip or first result row.

### 2.2 Live Results Grouped into Typed Sections
- **VOD Status Check:**
  - Audited `ChannelEntity.kt`, `RedSurfDatabase.kt`, `M3uParser.kt`, and `NavStrip.kt`.
  - Result: RedSurf enforces `streamType = 'live'` everywhere in the database. `epg_programs` only covers live channels. Nav destinations `Movies` and `Series` display `PlaceholderScreen("Coming in a later phase")`.
  - **Verdict:** Do NOT show `Movies` or `Shows` sections yet. The section data model must use a sealed hierarchy so VOD sections can be activated when VOD tables arrive in Phase 9.
- **Typed Sections to Render:**
  1. `Channels`: Live TV channels matching the query (ranked by `SearchRanker`).
  2. `Programs`: Airing / upcoming EPG programmes matching the query (joined with live channels via `searchDao.searchPrograms`).
- **Section Structure:**
  - Header: Section title ("Channels", "Programs") styled with `RedSurfType.sectionTitle`.
  - Content: Per-playlist chip tabs (when multiple playlists exist), followed by the result cards/rows.

### 2.3 Per-Playlist Chip Tabs
- **Behavior:**
  - Displayed directly under the section title if matching results span more than one playlist (`SearchLogic.showPlaylistHeaders(channels)`).
  - Tabs include an "All" chip (or first playlist) followed by individual playlist names (`playlistNames[playlistId] ?: "Playlist"`).
  - Resting tab: Pill shape, `SurfaceRaised` background, `TextSecondary`.
  - Focused tab: `Accent` solid red fill, white text (`TextPrimary`).
  - Selected tab: Subtle underline, distinct border, or brighter text when unfocused.
- **Navigation:**
  - `DPAD_LEFT` / `DPAD_RIGHT` navigates between playlist tabs.
  - `DPAD_DOWN` from a tab moves into the first card of that playlist.
  - `DPAD_UP` from a tab returns focus to the search field.

### 2.4 Channel Cards
- **Visual Design (TiviMate Parity):**
  - Dimensions: ~220dp wide × ~130dp high (or ~236×180dp at 1080p density).
  - Logo Box: Large channel icon rendered with `LogoOrLetter` (aspect fit or centered letter fallback).
  - Channel Name: Prominent title (`RedSurfType.rowTitle`), single line with ellipsis, `basicMarquee()` on focus.
  - Current Programme: Subtitle showing the currently airing program title from EPG, or "No information" if missing.
  - Progress Track: Thin 3dp bar at the card's bottom edge (accent red fill over 30% translucent track) calculating elapsed time fraction when a programme is currently airing.
- **Focus Styling:**
  - Focused: 1.05x subtle scale or solid Crest focus border (`RedSurfFocus.border()`), card background lightens to `SurfaceRaised`.

### 2.5 Settings Row: "Stay on search screen when switching channels"
- **Setting Location:** Added to `SettingsScreen.kt` under "Playback" (or "General" / "Search") category.
- **Persistence:** Stored in `AppPreferences` as `KEY_STAY_ON_SEARCH_WHEN_SWITCHING_CHANNELS` (default: `true`).
- **Interaction Model:**
  - **When ON:**
    - First OK on a channel card starts playback in the background/translucent surface without leaving `NavDestination.Search`.
    - Focus remains on the channel card.
    - Second OK on the *currently playing* channel card promotes playback to fullscreen Live TV (`teleportToFullscreen`).
  - **When OFF:**
    - Single OK immediately calls `teleportToFullscreen(channel)`.

### 2.6 Long-Press OK Menu on a Result (Add/Remove Favorite)
- **Trigger:** Long-press OK (`onLongClick`) on any channel result card.
- **Presentation:** A lightweight context overlay anchored at the card or rendered as a popup menu (using `BrowseContextMenu` style from `LiveTvScreen.kt`).
- **Rows:**
  - "Add to Favorites" / "Remove from Favorites" (dynamically derived from `favoriteKey(playlistId, streamId) in favoriteKeys`).
- **Action:**
  - Selecting the row toggles the favorite state via `viewModel.repository.setFavorite(...)`.
  - Dismisses the menu and restores focus back to the originating channel card.
  - Triggers a hint chip ("Added to favourites" / "Removed from favourites").
- **Back Dismissal:** Pressing `BACK` while the menu is open dismisses the menu and retains focus on the channel card.

### 2.7 Back & Focus Hierarchy
1. **Context Menu Open:** `BACK` closes context menu -> focus remains on the active result card.
2. **On-Screen Keyboard Open:** `BACK` closes keyboard -> focus remains on the search input field.
3. **Focused on Result Cards:** `BACK` moves focus up to the playlist chip tab of that section.
4. **Focused on Playlist Chip Tabs:** `BACK` moves focus up to the search input field.
5. **Focused on Search Field:** `BACK` moves focus up to the Search pill on `NavStrip` (`onEscapeUp()`).
6. **Focused on Search Nav Pill:** `BACK` triggers app-level exit peel (F17 double-back confirmation toast).

---

## 3. Implementation Plan: Files, Functions & Focus Rules

### 3.1 `tv-native/app/src/main/java/com/redsurf/tv/settings/AppPreferences.kt`
- **Functions to Change/Add:**
  - Add constant: `private const val KEY_STAY_ON_SEARCH_WHEN_SWITCHING = "stay_on_search_when_switching"`
  - Add state flow:
    ```kotlin
    private val _stayOnSearchWhenSwitching = MutableStateFlow(prefs.getBoolean(KEY_STAY_ON_SEARCH_WHEN_SWITCHING, true))
    val stayOnSearchWhenSwitching: StateFlow<Boolean> = _stayOnSearchWhenSwitching.asStateFlow()
    fun setStayOnSearchWhenSwitching(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STAY_ON_SEARCH_WHEN_SWITCHING, enabled).apply()
        _stayOnSearchWhenSwitching.value = enabled
    }
    ```
- **FOCUS_MODEL Rule:** **Rule 9** (State discipline: Preferences are reactive flows; state changes propagate immediately without restarting composables).

### 3.2 `tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt`
- **Functions to Change:**
  - `PlaybackSettingsPane` (or `GeneralSettingsPane`): Add toggle row for "Stay on search screen when switching channels" bound to `appPreferences.stayOnSearchWhenSwitching`.
  - `SettingsScreen`: Propagate `stayOnSearchWhenSwitching` and `onToggleStayOnSearchWhenSwitching`.
- **FOCUS_MODEL Rule:** **Rule 2** (Row-level exact focus restoration when backing out of Settings) and **Rule 7** (Pane boundary D-pad navigation).

### 3.3 `tv-native/app/src/main/java/com/redsurf/tv/db/EpgDao.kt` & `SearchDao`
- **Functions to Change:**
  - Add batch current-programme lookup in `EpgDao`:
    ```kotlin
    @Query(
        "SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelEpgId IN (:channelIds) " +
        "AND :now >= startTime AND :now < endTime"
    )
    suspend fun getCurrentProgramsForChannels(playlistId: String, channelIds: List<String>, now: Long): List<EpgProgramEntity>
    ```
- **FOCUS_MODEL Rule:** **Rule 3** (Async data loading must not disrupt spatial focus search or reset composed list state).

### 3.4 `tv-native/app/src/main/java/com/redsurf/tv/data/ChannelRepository.kt`
- **Functions to Change:**
  - Add data carrier `ChannelWithProgram(val channel: ChannelEntity, val currentProgram: EpgProgramEntity?)`.
  - Update `searchChannels(raw: String)` or add `searchChannelsWithAiring(raw: String)`:
    - Runs FTS search on `channels_fts`.
    - Ranks channels with `SearchRanker.rank(...)`.
    - Queries active EPG programmes for top 50 channels in one batch query via `epgDao.getCurrentProgramsForChannels`.
- **FOCUS_MODEL Rule:** **Rule 9** (Derived state survives navigation; batch query is fast and keeps UI responsive).

### 3.5 `tv-native/app/src/main/java/com/redsurf/tv/search/SearchLogic.kt`
- **Functions to Change/Add:**
  - Define `SearchSection` sealed hierarchy: `ChannelsSection(val tabs: List<PlaylistTab>)`, `ProgramsSection(val hits: List<ProgramResult>)`.
  - Add pure functions:
    - `buildPlaylistTabs(channels: List<ChannelEntity>, playlistNames: Map<String, String>): List<PlaylistTab>`
    - `filterChannelsByTab(channels: List<ChannelEntity>, selectedTabId: String?): List<ChannelEntity>`
    - `calculateProgress(startTime: Long, endTime: Long, now: Long): Float`
- **FOCUS_MODEL Rule:** **Rule 9** (Pure logic isolation; deterministic ordering and keys for list state restoration).

### 3.6 `tv-native/app/src/main/java/com/redsurf/tv/ui/search/SearchScreen.kt`
- **Functions to Change/Replace:**
  - `SearchScreen(...)`:
    - Accept `stayOnSearchWhenSwitching: Boolean`, `favoriteKeys: Set<String>`, `onToggleFavorite: (ChannelEntity) -> Unit`, `activePlayingChannelId: String?`.
    - Replace `OutlinedTextField` row's `CrestButton(label = "Voice")` with `BrandedMicButton` (custom circular button with microphone vector icon and Crest focus styling).
    - Replace flat `ResultRow` items with `ChannelCard` (logo, name with marquee, current programme, progress bar).
    - Implement `PlaylistChipsRow` for filtering channels by playlist.
    - Implement `SearchContextMenu`: Anchored popup dialog on long-press OK, managing "Add to Favorites" / "Remove from Favorites".
    - Update `BackHandler` logic to implement the strict peel hierarchy (ContextMenu -> Cards -> Tabs -> Field -> Escape to NavStrip).
- **FOCUS_MODEL Rules:**
  - **Rule 1**: Explicit `FocusRequester` on search field, mic button, and card items.
  - **Rule 2**: `restoreKey` exact card focus restoration when returning from playback.
  - **Rule 3**: Bounded retry loop (`repeat(10) { delay(50); ... }`) when restoring focus on lazy result cards.
  - **Rule 5**: One-shot focus events, avoiding repeated focus theft on query flow changes.
  - **Rule 7**: Intercept D-pad keys with `onPreviewKeyEvent` at boundaries (e.g. UP from cards escapes to tabs, UP from tabs escapes to field, UP from field escapes to NavStrip pill).
  - **Rule 8**: Reclaim focus on the channel card immediately upon context menu dismissal.

### 3.7 `tv-native/app/src/main/java/com/redsurf/tv/ui/shell/AppShell.kt`
- **Functions to Change:**
  - Refactor SearchScreen rendering:
    - In `AppShell.kt:939`, replace conditional composition `when { destination == NavDestination.Search -> ... }` with `visible: Boolean = (destination == NavDestination.Search)` using `Modifier.hiddenButComposed(visible)` (**Rule 4** compliance, identical to `LiveTvScreen` and `SettingsScreen`).
  - Wire channel selection:
    - If `stayOnSearchWhenSwitching` is true:
      - First OK on card: Starts playback via shared player without flipping `destination = NavDestination.LiveTv`.
      - Second OK on the same card: Executes `teleportToFullscreen(channel)`.
    - If `stayOnSearchWhenSwitching` is false:
      - First OK on card: Immediately executes `teleportToFullscreen(channel)`.
- **FOCUS_MODEL Rules:**
  - **Rule 4**: Keep `SearchScreen` composed once visited; toggle visibility so query, active tab, scroll position, and focus are never destroyed.
  - **Rule 9**: State hoisted to `AppShell` survives navigation.

---

## 4. Pure Unit-Testable Modules & Proposed JUnit Tests

The logic for grouping, tab filtering, progress calculation, and Back peel resolution can be completely isolated into pure Kotlin functions in `SearchLogic.kt`.

### Proposed Pure Helper Methods (`SearchLogic.kt`):

```kotlin
data class PlaylistTab(val id: String?, val name: String, val count: Int)

object SearchSectionLogic {

    /** Builds playlist tabs for channels. If matches are in only 1 playlist, returns empty list (no tabs needed). */
    fun buildTabs(channels: List<ChannelEntity>, playlistNames: Map<String, String>): List<PlaylistTab> {
        val distinctPlaylists = channels.map { it.playlistId }.distinct()
        if (distinctPlaylists.size <= 1) return emptyList()
        val allTab = PlaylistTab(id = null, name = "All", count = channels.size)
        val perPlaylistTabs = distinctPlaylists.map { pid ->
            val count = channels.count { it.playlistId == pid }
            PlaylistTab(id = pid, name = playlistNames[pid] ?: "Playlist", count = count)
        }
        return listOf(allTab) + perPlaylistTabs
    }

    /** Filters channels by selected playlist tab (null = "All"). */
    fun filterByTab(channels: List<ChannelEntity>, selectedTabId: String?): List<ChannelEntity> {
        if (selectedTabId == null) return channels
        return channels.filter { it.playlistId == selectedTabId }
    }

    /** Calculates airing progress fraction (0.0f .. 1.0f). Returns null if not currently airing. */
    fun calculateAiringProgress(startTime: Long, endTime: Long, now: Long): Float? {
        if (now < startTime || now >= endTime) return null
        val total = (endTime - startTime).coerceAtLeast(1L)
        val elapsed = (now - startTime).coerceIn(0L, total)
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** Computes remaining minutes for an active programme. */
    fun minutesRemaining(endTime: Long, now: Long): Long {
        if (now >= endTime) return 0L
        return (endTime - now) / 60_000L
    }

    /** Resolves Back button action target based on current focus and UI state. */
    enum class BackTarget { CLOSE_MENU, FOCUS_TABS, FOCUS_FIELD, ESCAPE_TO_NAV }

    fun resolveBackAction(
        isContextMenuOpen: Boolean,
        isFocusOnCards: Boolean,
        hasPlaylistTabs: Boolean,
        isFocusOnTabs: Boolean,
        isFocusOnField: Boolean,
    ): BackTarget {
        return when {
            isContextMenuOpen -> BackTarget.CLOSE_MENU
            isFocusOnCards && hasPlaylistTabs -> BackTarget.FOCUS_TABS
            isFocusOnCards && !hasPlaylistTabs -> BackTarget.FOCUS_FIELD
            isFocusOnTabs -> BackTarget.FOCUS_FIELD
            isFocusOnField -> BackTarget.ESCAPE_TO_NAV
            else -> BackTarget.FOCUS_FIELD
        }
    }
}
```

### Proposed JUnit Test Suite (`SearchSectionLogicTest.kt`):

```kotlin
package com.redsurf.tv.search

import com.redsurf.tv.db.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSectionLogicTest {

    private fun testChannel(playlistId: String, streamId: String) = ChannelEntity(
        streamId = streamId,
        playlistId = playlistId,
        groupId = "g1",
        num = 1,
        name = "Channel $streamId",
        streamType = "live",
        streamIcon = null,
        epgChannelId = null,
        groupName = "Sports",
    )

    @Test
    fun buildTabs_singlePlaylistReturnsEmpty() {
        val channels = listOf(testChannel("p1", "s1"), testChannel("p1", "s2"))
        val tabs = SearchSectionLogic.buildTabs(channels, mapOf("p1" to "Playlist 1"))
        assertTrue("No tabs needed when results belong to a single playlist", tabs.isEmpty())
    }

    @Test
    fun buildTabs_multiplePlaylistsGeneratesAllAndPerPlaylistTabs() {
        val channels = listOf(
            testChannel("p1", "s1"),
            testChannel("p2", "s2"),
            testChannel("p1", "s3"),
        )
        val names = mapOf("p1" to "US Sports", "p2" to "UK Streams")
        val tabs = SearchSectionLogic.buildTabs(channels, names)

        assertEquals(3, tabs.size)
        assertEquals(PlaylistTab(id = null, name = "All", count = 3), tabs[0])
        assertEquals(PlaylistTab(id = "p1", name = "US Sports", count = 2), tabs[1])
        assertEquals(PlaylistTab(id = "p2", name = "UK Streams", count = 1), tabs[2])
    }

    @Test
    fun filterByTab_filtersCorrectly() {
        val channels = listOf(
            testChannel("p1", "s1"),
            testChannel("p2", "s2"),
            testChannel("p1", "s3"),
        )
        val all = SearchSectionLogic.filterByTab(channels, null)
        assertEquals(3, all.size)

        val p1Only = SearchSectionLogic.filterByTab(channels, "p1")
        assertEquals(listOf("s1", "s3"), p1Only.map { it.streamId })

        val p2Only = SearchSectionLogic.filterByTab(channels, "p2")
        assertEquals(listOf("s2"), p2Only.map { it.streamId })
    }

    @Test
    fun calculateAiringProgress_activeProgramReturnsFraction() {
        val now = 1000L
        val start = 500L
        val end = 1500L
        val progress = SearchSectionLogic.calculateAiringProgress(start, end, now)
        assertEquals(0.5f, progress ?: 0f, 0.001f)
    }

    @Test
    fun calculateAiringProgress_pastOrFutureProgramReturnsNull() {
        val now = 2000L
        val pastProgress = SearchSectionLogic.calculateAiringProgress(500L, 1000L, now)
        assertNull(pastProgress)

        val futureProgress = SearchSectionLogic.calculateAiringProgress(2500L, 3000L, now)
        assertNull(futureProgress)
    }

    @Test
    fun minutesRemaining_computesAccurateMinutes() {
        val now = 0L
        val end = 120_000L // 2 minutes later
        assertEquals(2L, SearchSectionLogic.minutesRemaining(end, now))

        val pastEnd = -10_000L
        assertEquals(0L, SearchSectionLogic.minutesRemaining(pastEnd, now))
    }

    @Test
    fun resolveBackAction_peelsStrictlyByState() {
        // Context menu open -> Close menu
        assertEquals(
            SearchSectionLogic.BackTarget.CLOSE_MENU,
            SearchSectionLogic.resolveBackAction(
                isContextMenuOpen = true,
                isFocusOnCards = true,
                hasPlaylistTabs = true,
                isFocusOnTabs = false,
                isFocusOnField = false,
            )
        )

        // Cards with tabs -> Focus tabs
        assertEquals(
            SearchSectionLogic.BackTarget.FOCUS_TABS,
            SearchSectionLogic.resolveBackAction(
                isContextMenuOpen = false,
                isFocusOnCards = true,
                hasPlaylistTabs = true,
                isFocusOnTabs = false,
                isFocusOnField = false,
            )
        )

        // Cards without tabs -> Focus field
        assertEquals(
            SearchSectionLogic.BackTarget.FOCUS_FIELD,
            SearchSectionLogic.resolveBackAction(
                isContextMenuOpen = false,
                isFocusOnCards = true,
                hasPlaylistTabs = false,
                isFocusOnTabs = false,
                isFocusOnField = false,
            )
        )

        // Tabs focused -> Focus field
        assertEquals(
            SearchSectionLogic.BackTarget.FOCUS_FIELD,
            SearchSectionLogic.resolveBackAction(
                isContextMenuOpen = false,
                isFocusOnCards = false,
                hasPlaylistTabs = true,
                isFocusOnTabs = true,
                isFocusOnField = false,
            )
        )

        // Field focused -> Escape to nav
        assertEquals(
            SearchSectionLogic.BackTarget.ESCAPE_TO_NAV,
            SearchSectionLogic.resolveBackAction(
                isContextMenuOpen = false,
                isFocusOnCards = false,
                hasPlaylistTabs = true,
                isFocusOnTabs = false,
                isFocusOnField = true,
            )
        )
    }
}
```

---

## 5. Open Questions for the User

1. **Translucent Preview Presentation:** When "Stay on search screen when switching channels" is active and a channel is playing in preview mode:
   - Option A: Video plays full-screen directly behind the Search screen, covered by a semi-translucent scrim (e.g. 70% black overlay), with search UI overlaid on top.
   - Option B: Video plays in a dedicated 16:9 preview box (similar to the Live TV hero box) docked at the top right of the screen.
2. **Channel Cards Arrangement:** TiviMate arranges channel matches as a horizontally scrolling row of cards per playlist tab. When a query matches many channels (up to 50):
   - Option A: Horizontal single row of cards (`LazyRow`) per playlist tab (exact TiviMate parity).
   - Option B: Multi-row 2D grid (`TvLazyVerticalGrid` / wrapped rows) displaying more channels on screen simultaneously.
3. **Voice Search Initial Focus Preference:** TiviMate provides a setting "Prefer voice search" (which places initial focus on the Mic button instead of the search text box). Should RedSurf add this setting now, or default to focusing the search text box on entry?
4. **Context Menu Actions:** When long-pressing OK on a channel result, TiviMate offers "Add to Favorites" and "Open in external player". Does RedSurf only need "Add to / Remove from Favorites" for now, or should external player launching be included in this sprint?
