# Task 002 - Recognise 720p / 1080p / 2160p as quality tiers

**Builder:** Gemini, level 1   **Branch:** `agy/002-quality-p-tokens`   **Written by:** lead, 2026-10-28

**Goal:** provider channel names often say `1080p` instead of `FHD`. Today
`ChannelNameNormalizer` keeps `1080p` inside the matching key (`espn 1080p`), which breaks
matching. Make the three "p" tokens set the quality tier, exactly like `hd`/`fhd` already do.

**Read first (only these):** `GEMINI.md`;
`tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelNameNormalizer.kt`;
`tv-native/app/src/test/java/com/redsurf/tv/sports/ChannelNameNormalizerTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. In `ChannelNameNormalizer.kt`, add three entries to the existing `qualityTokens` map, in this
   order after `"4k" to Quality.UHD,`: `"720p" to Quality.HD,` `"1080p" to Quality.FHD,`
   `"2160p" to Quality.UHD,`. Change nothing else in that file.
3. In `ChannelNameNormalizerTest.kt`, add these three tests at the end of the class, in the same
   style as `splitsCountryQualityAndKey` (one `normalize` call, `assertEquals` on fields):
   - `pTokenSetsFhd`: `"US| ESPN 1080p"` -> key `"espn"`, quality `Quality.FHD`.
   - `pTokenSetsHdAndUhd`: `"FOX 720p"` -> key `"fox"`, quality `Quality.HD`; and
     `"FOX 2160p"` -> key `"fox"`, quality `Quality.UHD` (two calls in one test is fine).
   - `firstQualityTokenStillWins`: `"ESPN HD 1080p"` -> quality `Quality.HD`, key `"espn"`.
4. Run the compile check and unit tests (`GEMINI.md` section 3). Read the real test counts from
   the HTML report. Expected: 76 tests, 0 failures (73 before + your 3).
5. Fill in the Builder report below, commit, push your branch.

**Change exactly:** the two files above, and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `git diff origin/main --stat` shows only the three files.
- [ ] Compile `BUILD SUCCESSFUL` quoted; tests 76 / 0 failures quoted from the report.

**Stop and report if:** the map or test file does not look as described; any existing test fails;
the count is not 76.

## Builder report

- **Status:**
- **Branch:**
- **Files changed:**
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
