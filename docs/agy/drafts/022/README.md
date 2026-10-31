# Section O Polish Tasks — Breakdown & Sequencing Plan

**Document:** `docs/agy/drafts/022/README.md`  
**Source Brief:** `docs/agy/drafts/015-polish-o.md`  
**Source Decisions:** `docs/plans/TESTING_OUTSTANDING.md` §O (v0.65.9 feel-check received 2026-10-31)  
**Author:** Gemini (Builder, Level 3)  
**Date:** 2026-10-31  

---

## 1. Suggested Execution Order

Tasks are ordered from smallest self-contained fixes to higher-leverage UI and architectural passes. Pure logic tasks can be implemented and unit-tested in parallel by Gemini builders before UI integration.

| Step | Task | Name | Category | Primary Target | Verification Mode |
|---|---|---|---|---|---|
| **1** | **026** | Preview ↔ Fullscreen Duration | UI (param) | `LiveTvScreen.kt:960` | Lead device check (`redsurf-tv`) |
| **2** | **027** | Settings Backdrop Grain Removal | UI / Render | `SurfBackdrop.kt:56-83` | Lead screenshot (`redsurf-tv shot`) |
| **3** | **028** | Recents Strip Scrollable Row & Focus Recovery | Hybrid | `PlayerScreen.kt:1009-1046` | Unit test (`spec/RecentsLayoutGeometryTest.kt`) + Lead device check |
| **4** | **029** | Network Speed Filter (0.5s & EMA) | Pure Logic | `NetworkSpeedFilter.kt`, `PlayerController.kt` | Unit test (`spec/NetworkSpeedFilterTest.kt`) |
| **5** | **030** | Consolidate Video Info & Remove Stats For Nerds | UI | `PlayerScreen.kt:1452-1572` | Lead device check |
| **6** | **031** | Player Long-Press OK Menu Latch & Action Tiles | UI | `PlayerScreen.kt:567-621, 919-998` | Lead device check |
| **7** | **032** | Category Column Scroll Preservation | Pure Logic + UI | `CategoryVisibility.kt`, `GroupsColumn.kt` | Unit test (`spec/CategoryVisibilityTest.kt`) + Lead device check |
| **8** | **033** | Playlist Import Progress Model & Progress Bar | Pure Logic + UI | `ImportProgress.kt`, `MainViewModel.kt`, `MainActivity.kt` | Unit test (`spec/ImportProgressFormatterTest.kt`) + Lead device check |
| **9** | **034** | EPG Public Guide Legend & Persistent Sync Indicator | UI | `EpgGridColumn.kt`, `NavStrip.kt` | Lead device check |
| **10** | **035** | Playlist Settings Drill-Down Rows & Input Dialog | UI / Architecture | `SettingsScreen.kt:999-1213` | Lead device check (Keyboard dismissal) |

---

## 2. Category & Dependency Matrix

### Pure Logic Tasks (Unit-Testable by Builder)
- **Task 029 (`NetworkSpeedFilter`):** Implements instantaneous Mbps calculation from byte samples over 500ms intervals, exponential moving average (alpha = 0.4), and 3.0s (6 quiet windows) idle reset. Spec test: `docs/agy/drafts/022/spec/NetworkSpeedFilterTest.kt`.
- **Task 032 (`CategoryVisibility` logic):** Implements visible item bounds checking and mid-viewport scroll calculation (`holdMidList`) so categories already on-screen retain their exact position without vertical jumping. Spec test: `docs/agy/drafts/022/spec/CategoryVisibilityTest.kt`.
- **Task 033 (`ImportProgress` logic):** Implements determinate and indeterminate percentage calculations and user-facing channel count strings. Spec test: `docs/agy/drafts/022/spec/ImportProgressFormatterTest.kt`.
- **Task 028 (`RecentsGeometry` logic):** Implements width and viewport capacity calculations showing why 11 tiles (1,228.dp) overflow a 960.dp viewport and necessitate horizontal scrolling. Spec test: `docs/agy/drafts/022/spec/RecentsLayoutGeometryTest.kt`.

### UI Tasks (Require TV Checks via `redsurf-tv`)
- **Task 026:** Verifies ~290ms smooth expand/shrink transition timing between preview and fullscreen.
- **Task 027:** Verifies smooth deep crimson radial gradient without noisy film grain / TV static artifacts.
- **Task 030:** Verifies dismissal of Video Info on OK or one Back directly back to fullscreen playback, and validates removal of Stats for Nerds.
- **Task 031:** Verifies that long-press OK does not prematurely dismiss when releasing the physical remote button, and displays action tiles ("Add/Remove favourites", "Video info", "Hide channel").
- **Task 034:** Verifies public guide dot visibility (8.dp) and persistence of EPG sync progress after the NavStrip auto-hides.
- **Task 035:** Verifies that navigating down the playlist page never triggers the virtual on-screen keyboard.

---

## 3. Decisions & Open Questions for the Lead / User

1. **Task 035 (Settings Text Fields):**
   - *Problem:* Inline `TvTextField`s cause the virtual keyboard to pop on every row traversed while scrolling down to "Remove playlist".
   - *Fix:* Replace inline fields with clickable rows opening a dialog.
   - *User Decision:* Should text entry use a modal TV input dialog (recommended, compact and low disruption) or a dedicated sub-screen (TiviMate style)? Task 035 drafts the dialog pattern first to immediately solve the keyboard pop bug.
2. **Task 031 (Long-Press OK Styling):**
   - *User Decision:* User requested TiviMate's "OK+DOWN tiles". Task 031 implements this as a bottom action tile strip using `RedSurfFocus` surface cards with clear focus reclaim to fullscreen on Back.
3. **Task 034 (EPG Sync Position):**
   - When `NavStrip` auto-hides after 3 seconds, should the sync status show as a subtle indicator in the `EpgGridColumn` time ruler header, or should `NavStrip` delay auto-hiding while sync is in progress? Task 034 places the indicator in the persistent grid header.

---

## 4. Verification & Focus Model Conformance

Every drafted task explicitly follows `docs/vision/FOCUS_MODEL.md`:
- **Rule 1 & 2 (Explicit focus claims & exact item restoration):** Handled in Tasks 028, 030, 031, 032, 035.
- **Rule 3 (Bounded retry loops):** Applied on any focus claim where content may be composing asynchronously.
- **Rule 4 (Keep composed, do not size to 0.dp):** Applied in Task 031 context menu transitions.
- **Rule 7 & 8 (Boundary guards & modal reclaim on close):** Fullscreen overlays in Tasks 030 and 031 reclaim root playback focus on dismissal.
