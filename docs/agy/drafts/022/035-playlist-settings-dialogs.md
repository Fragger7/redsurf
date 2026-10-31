# Task 035 - Polish: Playlist settings drill-down rows & input dialog (stop keyboard pop)

**Builder:** Gemini, level 2   **Branch:** `agy/035-playlist-settings-dialogs`   **Written by:** lead, 2026-10-31

**Goal:** The user described the current playlist settings page as "horrible: text boxes inline, the on-screen keyboard pops for every field passed while scrolling to Remove. Wants TiviMate's pattern (row -> own screen with the field)." Eliminate inline `TvTextField`s from the scrollable list. Replace them with clickable `LiveRow`s displaying the current value; clicking a row opens an input dialog, completely eliminating virtual keyboard activation during scrolling.

**Read first (only these):** `GEMINI.md`; `docs/vision/FOCUS_MODEL.md` (Rules 1, 2, 7, 8); `docs/plans/TESTING_OUTSTANDING.md` §O (lines 44–50: "Playlist page: 'horrible' - text boxes inline, the on-screen keyboard pops for every field passed while scrolling to Remove. Wants TiviMate's pattern (row -> own screen with the field)..."); `tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt` (lines 999–1039, 1099–1213).

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. In `tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt`:
   - Create a reusable `SettingsInputDialog`:
     ```kotlin
     @Composable
     private fun SettingsInputDialog(
         title: String,
         initialValue: String,
         isPassword: Boolean = false,
         onSave: (String) -> Unit,
         onDismiss: () -> Unit,
     ) {
         // Displays modal input box with TvTextField, Save and Cancel CrestButtons.
         // Claims focus immediately onto the text field; onSave or onDismiss closes dialog.
     }
     ```
   - In `PlaylistDetail` (lines 1099–1213):
     - Remove the inline `TvTextField` and `ActionChip` row for "Name" (lines 1152–1172).
     - Replace with a standard `LiveRow`:
       ```kotlin
       LiveRow(
           label = "Playlist name",
           value = playlist.name,
           onClick = { activeDialog = DialogType.Name },
       )
       ```
   - In `ConnectionEditor` (lines 999–1039):
     - Remove inline `TvTextField`s for Server, Username, Password.
     - Replace each with a `LiveRow`:
       - `LiveRow(label = if (xtream) "Server address" else "Playlist URL", value = server, onClick = { ... })`
       - `LiveRow(label = "Username", value = user, onClick = { ... })`
       - `LiveRow(label = "Password", value = if (hasPassword) "••••••••" else "Not set", onClick = { ... })`
     - Keep the "Save & update" action row at the bottom of the section.
   - When a dialog dismisses, restore focus to the originating `LiveRow` (`FOCUS_MODEL.md` Rule 2).
   - Scrolling down past these rows to "Remove playlist" now moves focus smoothly across rows without ever activating the virtual keyboard!
3. Run compile check and unit tests (`GEMINI.md` section 3).
4. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt` and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only `SettingsScreen.kt` and this file.
- [ ] Zero `TvTextField` composables are directly embedded inside the playlist `LazyColumn` items.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. `redsurf-tv nav "Settings"`
3. Navigate into Playlists -> select an existing playlist.
4. Press `DPAD_DOWN` repeatedly from the top row all the way down to "Remove playlist".
5. VERIFY: The Android on-screen virtual keyboard NEVER activates or appears during downward scrolling.
6. Navigate back up to "Playlist name", press `DPAD_CENTER` -> input dialog opens with virtual keyboard.
7. Press `BACK` -> dialog closes; focus returns cleanly to the "Playlist name" row.
8. `redsurf-tv end`

**Stop and report if:** compile fails or focus is lost when closing input dialog.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/035-playlist-settings-dialogs
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt
  - docs/agy/tasks/035-playlist-settings-dialogs.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
