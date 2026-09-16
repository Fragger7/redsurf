# RedSurf Player Engine — Engineering Brief

**Author:** Opus, acting as lead architect for the player engine, 2026-09-16 — spawned specifically
to think through performance, memory, and format compatibility for the Player sprint (`PHASE_2.md`),
grounded in the real Chromecast hardware ceiling (`HARDWARE.md`) and existing domain knowledge
(`IPTV_DOMAIN_KNOWLEDGE.md`).

**Scope:** the media engine underneath and alongside `PHASE_2.md`. PHASE_2's interaction layer (OK
overlay, LEFT list, RIGHT zap, context menu, Recents/resume) is fixed scope and is not touched here
except where noted in §9.

**Status: brief only, nothing implemented yet.** This is a plan to execute against, not a changelog.

**Reading:** every recommendation is a decision, not an option. Each has a one-line rationale tied
to the Chromecast (~449 MB free, 4-core Amlogic, H.264/HEVC/VP9/MPEG-2 in hardware, **no AV1**,
Widevine L1) or to the household's second box (Shield Pro). Where a number needs measuring rather
than guessing, the brief says so and names the measurement.

---

## 0. Verdict in one paragraph

The player's *shape* is right and better than most hobby IPTV apps: one long-lived `ExoPlayer` with
media items swapped, a real fast-zap `LoadControl`, `setKeepContentOnPlayerReset` for the
black-screen minimiser, `OkHttpDataSource` with the evasion User-Agent, and `StreamInfo` read from
the actual decoded `Format`. What's missing is everything between "it plays" and "it plays
*anything*, and recovers when it doesn't": there is **no renderer factory configuration at all** (so
no decoder fallback, no extension renderers), **no extractor configuration** (so several very common
MPEG-TS variants fail or tune slowly), **no error handling whatsoever** (`grep onPlayerError` across
`tv-native/app/src/main` returns nothing — a dead channel or a 3-second network blip ends playback
permanently, silently, with the last frame frozen on screen), and no memory ceiling on the buffer
allocator. The AFR implementation is actively wrong for 25/50 fps content and duplicates a mechanism
Media3 already has on API 30+. Those five things are the brief.

---

## 1. What is already right — do not redo it

Stated explicitly so a later sprint doesn't "modernise" working code.

1. **One `ExoPlayer` per fullscreen session; zap swaps the media item.** `PlayerHost.kt`'s
   `remember { … }` + `LaunchedEffect(streamUrl)`. This is the correct structure and explicitly
   fixes the predecessor's release-per-URL bug. Keep.
2. **`setKeepContentOnPlayerReset(true)` + `setShutterBackgroundColor(BLACK)`.** Correct
   implementation of `PRODUCT_VISION.md` §3. Keep, including the `blackScreenBetweenZaps`
   inversion.
3. **`onRenderedFirstFrame` as the "there is now real video" signal**, with a 5 s belt-and-braces
   timeout. That is the right signal — not `STATE_READY`, not `onTracksChanged`. Keep and reuse
   (§4.4 depends on it).
4. **`OkHttpDataSource` rather than `DefaultHttpDataSource`.** Correct for IPTV: it inherits DoH,
   the evasion UA, and the redirect behaviour. Keep the choice; fix the client lifetime (§7).
5. **`PlayerView` default surface type (`SurfaceView`).** Lowest memory and power, and it
   composites *below* the window, which is exactly why `PlayerScreen`'s Compose overlays draw on
   top correctly. Never call `setZOrderOnTop`/`setZOrderMediaOverlay` — it would put video above
   the OSD.
6. **`StreamInfo` derived from `videoFormat`/`audioFormat` on listener callbacks, with unknown
   fields omitted rather than faked.** Right source of truth, right honesty policy. Extend it
   (§4.4), don't replace it.
7. **Lifecycle pause/resume observer and view-scoped `keepScreenOn`.** Both correct, both
   self-cleaning.
8. **Zap neighbour lookup as two O(1) indexed queries.** `ChannelDao.nextInGroup`/`prevInGroup`.
   Correct at 60 K channels.
9. **`usesCleartextTraffic="true"`** in the manifest — required by `IPTV_DOMAIN_KNOWLEDGE.md`
   §11, present.
10. **`TrackManager` as a single injected `DefaultTrackSelector`.** Right shape. Its *lifetime* is
    wrong (§9), and its defaults are wrong (§2.8), but the class earns its place.

---

## 2. Codec / container / DRM compatibility matrix

### 2.1 What IPTV actually delivers

| Input | Frequency in a real Xtream/M3U list | Media3 1.2.1 as configured today |
|---|---|---|
| MPEG-TS over HTTP, no extension or `.ts` | **The majority of live channels** | Works via `ProgressiveMediaSource` + `TsExtractor`, but with default extractor flags — see 2.6 for the two cases that fail |
| HLS `.m3u8` (multivariant or media playlist) | Common, growing | Works (`media3-exoplayer-hls` present) |
| Low-latency HLS (`EXT-X-PART`) | Rare from resellers; assume ~0% of this user's list | Supported by the library; unconfigured |
| DASH `.mpd` | Rare live, occasional catch-up/VOD | **Fails — `media3-exoplayer-dash` is not a dependency** |
| Raw progressive MP4/MKV (VOD) | Common once VOD lands | MP4/MKV/WebM work; audio inside them is the gap (2.4) |
| RTSP / RTMP | Effectively dead in 2026 IPTV | Not supported. **Decision: do not add.** Not worth the APK bytes or the surface area. |

**Decision:** add `androidx.media3:media3-exoplayer-dash` (~200 KB). Rationale: it is the only
realistic path for provider catch-up/VOD and it is the container DRM actually rides on (2.7);
adding it later alongside DRM is two risky changes instead of one.

### 2.2 Renderer factory — the single highest-value change in this brief

`PlayerHost.kt` never passes a `RenderersFactory`, so ExoPlayer builds a `DefaultRenderersFactory`
with `EXTENSION_RENDERER_MODE_OFF` and **`enableDecoderFallback = false`**.

**Decision:** pass an explicitly configured factory:

- `setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)` — **`ON`, never
  `PREFER`.** `ON` appends extension renderers *after* the platform `MediaCodec` ones, so hardware
  decode and AC3/E-AC3 HDMI passthrough remain first choice and the FFmpeg decoders are reached
  only when the platform has nothing. `PREFER` would put a software FFmpeg AC3 decoder ahead of
  the sink's passthrough path — it would silently kill surround sound and burn CPU on a 4-core A55.
  This directly answers "how to enable extension renderers without regressing hardware-decode-first
  performance."
- `setEnableDecoderFallback(true)` — when the primary decoder fails to initialise or throws a
  decode error, try the next decoder in the list instead of ending playback. One line, no new
  dependency, no regression risk (platform decoders are still tried first), and it converts a class
  of "this one channel is just black" reports into working playback. **This is the cheapest real
  compatibility win available.**
- Leave `MediaCodecSelector.DEFAULT` alone. Media3 already orders hardware-accelerated decoders
  first; `PRODUCT_VISION.md` §4's "Hardware Decoder Locking" is already satisfied by the library
  and does not need custom code.

**"Force software decoder" (the dead `PlayerSettings.useSoftwareDecoder`):** implement as a
`MediaCodecSelector` that returns `!isHardwareAccelerated` decoders first, exposed as a
**diagnostic** toggle under Settings → Playback, default off. It exists to isolate a decoder bug on
one channel, not as a mode anyone runs in.

### 2.3 The FFmpeg extension — decision: yes, audio-only, prebuilt into the repo

**Decision: build and ship `media3-decoder-ffmpeg` (audio only), with DTS, TrueHD, AC3, E-AC3,
MP2/MP3, AAC, Vorbis, Opus, FLAC enabled.**

Rationale, specific to this household:
- **DTS is the real gap.** The Chromecast with Google TV has no DTS decoder and no DTS passthrough
  licence. A DTS audio track today produces **silent video** — the exact "audio but no video /
  video but no audio" failure that's a classic cheap-Android-TV-box problem. The Shield decodes
  DTS; the Chromecast does not. FFmpeg decoding to PCM makes DTS play on both.
- **MPEG audio Layer II** is pervasive in European DVB-sourced MPEG-TS and platform support for it
  is inconsistent across Amlogic firmware. FFmpeg makes it deterministic.
- **AC3/E-AC3 are not the reason to do this** — the platform handles them, and passthrough must
  stay on the platform path. They're enabled in the build only as a last-resort fallback behind
  `EXTENSION_RENDERER_MODE_ON`.

Implementation notes the engineer needs:
- The extension has **no Maven artifact**; it is an NDK build from the `androidx/media` source tree
  (`libraries/decoder_ffmpeg`, `build_ffmpeg.sh`) and **must be built against the exact Media3
  version the app depends on**.
- Enabled decoder set: `mp3 aac ac3 eac3 dca mlp truehd mp2 vorbis opus flac alac pcm_mulaw
  pcm_alaw`. (`dca` = DTS, `mlp`/`truehd` = Dolby TrueHD.)
- **ABI filters:** read `adb shell getprop ro.product.cpu.abilist` on both the Chromecast and the
  Shield and build only those ABIs. Do not ship x86/x86_64 — storage on the Chromecast is 79% full.
- **CI:** the release pipeline is a plain Gradle build. **Decision: commit the prebuilt `.so` files**
  under `tv-native/ffmpeg/jniLibs/<abi>/` with a documented, re-runnable build script next to them,
  rather than making CI carry an NDK/FFmpeg toolchain. Cost ≈ 3–6 MB of repo and APK. Record the
  FFmpeg version and Media3 version in a README beside the binaries — a mismatch after a Media3
  upgrade is a hard-to-diagnose crash.
- **Video stays hardware-only.** Do not add the libgav1/VP9 extensions (see 2.5).

**Rejected: libVLC as a second engine.** `player/PlayerEngine.kt` contains a dead
`PlayerEngineType.LIB_VLC` enum. Reject it permanently: +30–40 MB APK on a box with 880 MB free, a
second surface/audio/track stack, and every bug doubled. The FFmpeg audio extension delivers ~95%
of the real compatibility benefit at ~5% of the cost. **Delete `player/PlayerEngine.kt`** (15 lines,
zero references).

### 2.4 Audio passthrough and surround

Media3 already does the right thing: `DefaultAudioSink` queries `AudioCapabilities` from the HDMI
sink and chooses passthrough over decode for AC3/E-AC3/E-AC3-JOC when the sink advertises support,
and it re-queries on `ACTION_HDMI_AUDIO_PLUG` when an AVR is switched on. No code is needed for the
happy path — **provided `EXTENSION_RENDERER_MODE_ON` (not `PREFER`) is used**, which is why 2.2 is
worded the way it is.

Two things to add:
- **`setAudioAttributes(AudioAttributes(usage = USAGE_MEDIA, contentType = CONTENT_TYPE_MOVIE),
  handleAudioFocus = true)`** on the `ExoPlayer.Builder`. Currently unset, so the Assistant and
  system sounds don't duck the stream. Two lines.
- **Settings → Playback → "Audio output: Auto / Force stereo"**, default Auto. "Force stereo"
  builds the renderers factory with a `DefaultAudioSink` pinned to
  `AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES` (stereo PCM only), forcing decode instead of
  passthrough. This is the fix for the single most common surround complaint — TV or AVR advertises
  a format it can't actually render, producing silence or static — and it must be reachable without
  a rebuild.

### 2.5 What this SoC cannot decode, and what that means

Hardware on the Chromecast: H.264 (to 4K30), HEVC (4K60, incl. HDR10/HDR10+/DV), VP9 (4K60),
MPEG-2 (SD). **No AV1.** The Shield also has no AV1 hardware.

Consequences, both concrete:

1. **Never let ABR select an AV1 rendition on a box that can't decode it in hardware.** Android 14
   ships a software AV1 decoder, so ExoPlayer will happily "succeed" at selecting AV1 and then drop
   frames on four A55 cores — a worse outcome than a clean failure. **Decision: build the
   preferred-video-MIME list at runtime from the device, not from a constant.** At player
   construction, query `MediaCodecUtil.getDecoderInfos(mime, secure=false, tunneling=false)` for
   H.264/HEVC/VP9/AV1, keep those with `isHardwareAccelerated == true`, and apply them via
   `TrackSelectionParameters.setPreferredVideoMimeTypes(...)`. One binary, device-derived policy:
   the Chromecast excludes AV1, a future AV1-capable box includes it automatically. This is
   `HARDWARE.md`'s "design for scale, test on the weakest box" expressed in code rather than in a
   comment.
2. **When AV1 is the *only* track** (single-rendition stream, no alternative), the preferred-MIME
   list can't save you. Detect it in the error/format path and surface **"This device can't decode
   AV1"** in the OSD rather than presenting a stuttering picture. Honest failure beats a bad
   picture — the same rule the info block already follows for unknown badges.

**Do not add the libgav1 AV1 extension.** Software AV1 at 1080p on this SoC is not watchable and
the extension is several MB.

**Interlaced content (1080i/576i):** very common in IPTV. Deinterlacing is the hardware decoder's
job on Amlogic; ExoPlayer has no knob for it. If combing appears, record it as a device limitation,
do not build a software deinterlacer.

### 2.6 Container/extractor decisions — three real MPEG-TS gaps

`DefaultMediaSourceFactory(dataSourceFactory)` is constructed with a default
`DefaultExtractorsFactory`. Three fixes, all in one place:

1. **`setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)`.** Many
   provider TS feeds never emit a true IDR frame (or the connection lands mid-GOP). Without this
   flag `H264Reader` withholds output until an IDR arrives — which can be many seconds, or never.
   **This is both a compatibility fix and a zap-latency fix** and is the single most likely cause
   of "some channels take forever to start." Do **not** additionally set `FLAG_DETECT_ACCESS_UNITS`
   — it costs CPU we don't have and solves a rarer problem.
2. **A MIME fallback ladder on `UnrecognizedInputFormatException`.** `DefaultMediaSourceFactory`
   picks HLS vs progressive from the **original** URI's extension, never from the redirect target
   or the response `Content-Type`. Providers routinely 302 a `.ts` URL onto an `.m3u8` edge
   (`IPTV_DOMAIN_KNOWLEDGE.md` §11). Today that produces a hard `UnrecognizedInputFormatException`
   and a dead channel. **Decision:** on `PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`
   (or an `UnrecognizedInputFormatException` cause), retry the same URL exactly once with
   `MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8)`, and if that also
   fails, once more with `MimeTypes.VIDEO_MP2T`. Two retries, then a real error message. Cheap,
   bounded, and it recovers a whole class of provider quirk.
3. **Leave TS single-PMT mode alone.** `DefaultExtractorsFactory` already constructs `TsExtractor`
   for the single-program case, which is what IPTV delivers. Only reach for a custom
   `ExtractorsFactory` if a provider is genuinely multiplexing programs — don't pre-build for it.

**PTS wraparound / long-session stalls:** the 33-bit MPEG-TS PTS wraps roughly every 26.5 hours;
some provider encoders also emit discontinuities after an upstream hiccup. Do not engineer for this
specifically — the generic **stall watchdog** in §4.5 covers it along with five other causes.

### 2.7 DRM — decision: hook, not machinery

- **Widevine L1 vs L3 is a non-question for this app.** Both the Chromecast with Google TV and the
  Shield Pro are L1. If content is Widevine-protected and the device is L1, Media3's built-in
  `DefaultDrmSessionManager` handles it with no app-level difference; L3 would only ever mean a
  lower resolution cap imposed by the licence server. There is nothing for RedSurf to decide or
  implement here.
- **Xtream/Stalker IPTV is essentially never DRM-protected.** Building a DRM subsystem for it would
  be building for a case that doesn't occur.
- **The one realistic path is `#KODIPROP`.** M3U files in the wild carry
  `#KODIPROP:inputstream.adaptive.license_type=...` / `license_key=...` lines. **Decision: parse
  those two properties in `M3uParser.kt`, persist them on the channel row, and populate
  `MediaItem.DrmConfiguration` when present.** ~30 lines, no new dependency (DRM is in
  `media3-exoplayer` core; it needs the DASH module from 2.1 to be useful). Everything else —
  offline licences, key rotation, provisioning UI — is out of scope and should stay out.

### 2.8 Subtitles — what `TrackManager` gets wrong today

`TrackManager`'s constructor sets `setPreferredAudioLanguage("en")` **and
`setPreferredTextLanguage("en")`**. The second one is a defect: a preferred text language causes
ExoPlayer to *select and render* a matching text track, so on any channel carrying an English
CEA-608 track, subtitles appear unrequested. **Decision:**

- Default text to **disabled**: `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)`. Subtitles are
  opt-in, via the picker PHASE_2 decision 9 already specifies.
- Replace the hardcoded `"en"` audio preference with
  `setPreferredAudioLanguages(*Util.getSystemLanguageCodes())`, overridden by a persisted user
  choice.
- **When the user picks an audio track from the picker, also persist its language and set it as
  the preferred language.** Track-group overrides do not survive a channel change (the group
  identity is gone), so without this, "I want the English feed" has to be re-chosen after every
  zap. This is what makes the Settings → Playback → "Preferred audio" row real.

Coverage relative to what IPTV actually carries:

| Format | Status | Action |
|---|---|---|
| CEA-608 embedded in TS | Works — `TsExtractor` synthesises a CEA-608 track when the PMT declares no caption descriptors | None |
| CEA-708 | Works when the PMT carries a correct caption descriptor | None |
| **DVB bitmap subtitles** (`application/dvbsubs`) — very common in EU feeds | Decoder is in `media3-extractor`; `DvbSubtitleReader` handles it | None — but **verify on a real EU channel**, it's untested here |
| WebVTT / TTML in HLS | Works | None |
| **Teletext subtitles** (EBU, still present in some EU TS) | **Not supported by Media3 at all** | Document as a known gap; do not build a decoder |
| SRT/ASS sidecar files | Only relevant to VOD | Add `MediaItem.SubtitleConfiguration` when VOD lands; ASS styling is not supported and never will be here |

`PlayerView` already contains a `SubtitleView` and applies the system captioning style, so
rendering works with `useController = false`. No work needed there.

---

## 3. Buffer and memory strategy

### 3.1 Validating 2500 / 15000 / 500 / 1500

Verdict: **the time values are defensible and should mostly stand; the missing byte ceiling is the
real problem.**

- `bufferForPlaybackMs = 500` — correct, and the core of the fast-zap feel. Keep.
- `minBufferMs = 2500` / `maxBufferMs = 15000` — reasonable on ethernet. `PHASE_2.md` #2.6
  correctly says these get retuned only from a measurement; honour that. **Do not change them
  speculatively.**
- `bufferForPlaybackAfterRebufferMs = 1500` → **change to 2500.** This one is defensible without a
  measurement: a rebuffer means the network just failed you, and resuming after 1.5 s usually
  rebuffers again within seconds. Trading 1 s once against a rebuffer loop is the right trade.
- **`targetBufferBytes` is unset**, which means `DefaultLoadControl` computes its default video
  budget of `2000 × 64 KB ≈ 125 MB`. On a device with **449 MB free**, a nominal allocator ceiling
  of 125 MB is not a ceiling at all. In practice the time bounds bind first (15 s × 8 Mbps ≈ 15 MB),
  but a 20 Mbps 4K HEVC channel is ≈ 37 MB, and `DefaultAllocator` retains its high-water free-list
  for the rest of the session. **Decision: `setTargetBufferBytes(20 * 1024 * 1024)` and leave
  `setPrioritizeTimeOverSizeThresholds(false)` (the default) so the byte cap is a real ceiling, not
  advisory.**
- **Rule to write into the code as a comment, because it will otherwise be broken by a later tuning
  pass:** if `maxBufferMs` is ever raised, `targetBufferBytes` must be raised with it, and the ABR
  thresholds in §4.2 must stay strictly below `maxBufferMs`. Raising one alone produces a player
  that either can never fill its buffer or can never step down its bitrate.

### 3.2 Multi-view budget

`HARDWARE.md` says 2 tiles, unproven. The current `MultiViewEngine.kt` computes a grid for up to 9
tiles and gives each tile a full `PlayerHost` — meaning per tile: its own `OkHttpClient` (own
connection pool and thread pool), its own `TrackManager`, its own `AfrManager`, its own
unbounded-byte `LoadControl` and allocator, its own `keepScreenOn`, and its own audio track decoding
and mixing. Nine of those would not survive on this box; two of those is already wasteful.

**Decisions:**
- **A shared `DefaultAllocator` across all tile `LoadControl`s.** This is the only way to bound
  *total* buffer memory rather than per-player memory. Pass the same allocator instance to each
  `DefaultLoadControl.Builder`.
- **Per-tile `LoadControl`: `2000 / 6000 / 500 / 2500`, `targetBufferBytes = 8 MB`.** A tile is
  glanceable, not primary; 6 s of cushion is enough.
- **Audio track disabled on every unfocused tile** (`setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,
  true)`), not merely `volume = 0f`. Muting still runs a decoder and an `AudioTrack`. Only the
  focused tile has audio.
- **`setMaxVideoSize(1280, 720)` on unfocused tiles** via `TrackSelectionParameters`. State the
  honest limit: this only helps for HLS/DASH with multiple renditions. **A single-rendition
  MPEG-TS has no lower variant, so a 2-tile multiview of two 1080p TS channels is genuinely two full
  1080p decodes.** That is exactly why 2 is the ceiling.
- **Tile count is device-derived, not hardcoded.** `maxTiles = min(configuredLimit,
  MediaCodecInfo.CodecCapabilities.getMaxSupportedInstances())` for the relevant video MIME, with
  `configuredLimit` a Settings value defaulting to 2 on ≤2 GB devices and 4 on >3 GB. This is
  `HARDWARE.md`'s "configurable limit that defaults to 2 on this device class," implemented rather
  than commented.
- **`MultiViewEngine.kt` is a rewrite, not a patch.** It uses the legacy
  `com.redsurf.tv.data.Channel` model, hardcodes `Color(0xFFE11D48)` against the design-system
  rule, and is unreferenced. Rewrite it against `ChannelEntity` and the `PlayerController` of §9
  when multiview becomes a real phase. **Not now** — PHASE_2 decision 18 says don't touch it, and
  that stands.

### 3.3 Player lifecycle: reuse vs recreate

Current behaviour: the player is created when `isFullscreen` flips true and **released on exiting
fullscreen**; media items are swapped for zaps within a session.

**Decision: keep exactly this lifetime.** The rationale, so it doesn't get "optimised" later:
- **Within a session, reuse is mandatory and already correct.** Releasing per zap would be a
  regression.
- **Across sessions, recreation is correct on this box.** A retained idle `ExoPlayer` is a few MB
  and, more importantly, an open provider socket if it isn't fully stopped — which collides with
  `max_connections: 1` (§4.3). On a 449 MB budget, holding a player to save ~30 ms of construction
  is a bad trade. Renderer construction is cheap; `MediaCodecUtil`'s decoder-info query is
  statically cached after the first call, so the second fullscreen entry is cheaper than the first
  anyway.
- **What is *not* correct is releasing the `OkHttpClient` with it** — see §7.

**Explicitly rejected: a second player for preview-while-browsing.** A live preview pane on this
device means a second decode session and a second provider connection during D-pad scrolling.
`PreviewStub` staying a still image is the right call.

---

## 4. Live-specific performance

### 4.1 Low-latency HLS — enable the machinery, don't chase it

**Decision: configure the live knobs, set no target offset override, and do not build anything
LL-HLS-specific.**

- On `DefaultMediaSourceFactory`: `setLiveMinSpeed(0.95f)` and `setLiveMaxSpeed(1.06f)`. Media3's
  `DefaultLivePlaybackSpeedControl` already holds a live target offset by nudging playback speed;
  without a usable speed range it can't actually correct drift. These two lines make an existing
  mechanism work.
- **Do not override `setLiveTargetOffsetMs`.** Honour the server's
  `PART-HOLD-BACK`/`HOLD-BACK`/`EXT-X-START`. Overriding it to chase latency on a plain
  6-second-segment HLS stream buys nothing and causes stalls.
- On `HlsMediaSource.Factory`: set `setAllowChunklessPreparation(true)` explicitly. It avoids
  downloading a segment of every rendition just to learn its tracks — a direct zap-latency win on
  multivariant playlists. It is the current library default; setting it explicitly documents the
  intent and protects against a default change.
- **Honest framing for the owner:** LL-HLS is a provider feature. If this user's provider doesn't
  emit `EXT-X-PART`, none of this changes anything. The above is ~4 lines and costs nothing;
  anything more is speculative work.

### 4.2 ABR defaults — the one that's actually wrong today

`DefaultTrackSelector(context)` is built with the default `AdaptiveTrackSelection.Factory`, whose
defaults are `minDurationForQualityIncrease = 10 s`, `maxDurationForQualityDecrease = 25 s`,
`minDurationToRetainAfterDiscard = 25 s`. **The app's `maxBufferMs` is 15 s.** Two of those three
thresholds can never be reached, so quality step-downs under jitter behave erratically — the exact
condition `IPTV_DOMAIN_KNOWLEDGE.md` §4 is about.

**Decision:** construct the selector as `DefaultTrackSelector(context,
AdaptiveTrackSelection.Factory(minDurationForQualityIncreaseMs = 6_000,
maxDurationForQualityDecreaseMs = 10_000, minDurationToRetainAfterDiscardMs = 10_000,
bandwidthFraction = 0.7f))`. Every threshold now sits strictly inside the 15 s buffer.

**Also:** set `DefaultBandwidthMeter.Builder(context).setInitialBitrateEstimate(20_000_000)` and
pass the shared singleton to the player. The household is on ethernet; starting every fresh estimate
at a country-default ~1–2 Mbps means the first rendition chosen on a zap is the worst one, and ABR
then spends 10+ seconds climbing. Media3 already shares the bandwidth meter singleton across
players by default — keep that, just seed it.

### 4.3 Zap ordering vs `max_connections: 1` — a real correctness bug

`IPTV_DOMAIN_KNOWLEDGE.md` §10 is explicit: the old socket must be severed before the new one
opens, or the provider returns HTTP 456. `PHASE_2.md` decision 13 says the opposite — "never
`stop()`" — and `PlayerHost` currently calls `stop()`/`clearMediaItems()` only when
`blackScreenBetweenZaps` is on.

**Decision: always `exoPlayer.stop()` before `setMediaItem()` + `prepare()`, on every zap.**

Why this is safe and why decision 13's concern doesn't apply:
- The reason decision 13 forbade `stop()` was the black screen. **`setKeepContentOnPlayerReset(true)`
  is precisely the mechanism that makes `stop()` visually free** — the shutter holds the last frame
  through the reset. The user-visible behaviour PHASE_2 specifies is unchanged.
- The performance objection ("we lose decoder reuse") doesn't apply either: `setMediaItem()`
  already resets playback and disables renderers, so the codec is re-initialised on every zap
  regardless of `stop()`. There is no seamless codec-reuse path for a live channel change. `stop()`
  adds no decoder cost.
- Add a **single delayed retry for HTTP 456 specifically**: on `InvalidResponseCodeException` with
  `responseCode == 456`, wait 1200 ms and `prepare()` once more. The close of the previous socket is
  asynchronous on the loading thread; one bounded retry absorbs the race without a retry storm.

This amends decision 13's *mechanism* while preserving its *user-visible outcome*. Record it in
PHASE_2's "what actually happened" log as the plan's own rules require.

### 4.4 Error handling — currently zero, and this is the biggest reliability gap

There is no `Player.Listener.onPlayerError` anywhere in `tv-native/app/src/main`. Today: a dead
channel, a 403, a 456, a provider hiccup, or a three-second network blip all end playback
permanently with a frozen last frame and no message. `IPTV_DOMAIN_KNOWLEDGE.md` §3 explicitly
requires graceful handling of 456/884.

**Decision: one `PlaybackErrorController`, owned by the `PlayerController` of §9, with three
behaviours:**

1. **Classify.** Map `PlaybackException.errorCode` and, where the cause is
   `HttpDataSource.InvalidResponseCodeException`, the HTTP status, to a short user-facing string:
   - `403` → "Provider rejected this stream (403)" — do not retry; retrying with a different UA is
     explicitly warned against.
   - `456` → "Too many connections — retrying" — one delayed retry (4.3).
   - `884` → "Stream locked by provider (anti-dump)" — do not retry.
   - `404` → "Channel not available" — do not retry. (Separator pseudo-channels should already be
     filtered by the parser per `IPTV_DOMAIN_KNOWLEDGE.md` §9.)
   - `ERROR_CODE_IO_NETWORK_CONNECTION_*`, `ERROR_CODE_IO_UNSPECIFIED` → retry.
   - `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` → the MIME ladder of 2.6.
   - `BehindLiveWindowException` → `seekToDefaultPosition(); prepare()`. Classic and currently
     missing.
2. **Retry with bounded backoff** for retryable classes only: 500 ms, 1 s, 2 s, 4 s, then stop and
   show the message. Never unbounded.
3. **Surface it.** A single line in the OSD, in the same slot the "tuning" indicator uses (AGENTS.md
   backlog item 3). One `Player.Listener` instance feeds three consumers: `StreamInfo` badges
   (existing), the buffering indicator, and this controller.

**Also set a `LoadErrorHandlingPolicy`** with `minimumLoadableRetryCount = 6` for live sources — the
default (3, for progressive) gives up too early on a stream that's meant to run for hours.

### 4.5 Stall watchdog

**Decision:** if `playbackState == STATE_BUFFERING` continuously for **15 seconds**, call
`prepare()` once (and after a second occurrence within 60 s, treat it as an error per 4.4). One
timer, ~20 lines. It covers, generically, what would otherwise be five separate investigations: PTS
wraparound after a long session, provider encoder discontinuities, a silently half-dead TCP
connection, an edge server that stopped sending without closing, and DNS flapping.

Pair it with a **media-specific OkHttp client** (§7): `connectTimeout = 5 s`, `readTimeout = 8 s`. A
live stream that goes 8 seconds without a byte is dead; today it inherits a 15 s read timeout, so
the UI sits frozen for 15 seconds with no feedback before anything happens at all. This is
`IPTV_DOMAIN_KNOWLEDGE.md` §4's fast-fail hedging applied to the media path, where it has never been
applied.

### 4.6 Next-channel prefetch — decision: **do not build it**

Stated as a decision with its reasoning, because it's the obvious idea and someone will propose it
again:

- A **second player pre-preparing the neighbour** is out: a second decode session on a 449 MB /
  4-core box, and a second concurrent provider connection, which is precisely the
  `max_connections: 1` violation of §4.3. Non-starter.
- **Warming the network path only** (DNS + TLS + redirect resolution for the neighbour's URL) sounds
  free but isn't: essentially every channel in one playlist shares the same host, so DNS and TLS are
  already warm within a session, and the only part that *would* help — resolving the 302 to the edge
  — requires actually opening the stream, which is the 456 risk again. **Real cost, no measurable
  win.**
- **The zap budget is dominated by decoder init and time-to-first-keyframe, not connection setup.**
  The fixes that actually move that number are in this brief already: `FLAG_ALLOW_NON_IDR_KEYFRAMES`
  (2.6), `setAllowChunklessPreparation` (4.1), the seeded bandwidth estimate (4.2), and a shared
  connection pool (§7).

Revisit only if `PHASE_2.md` #2.6's key-press-to-first-frame measurement attributes a significant
share to connection setup. Measure before building.

---

## 5. Auto frame rate — rebuild around the platform, don't extend `AfrManager`

`AfrManager.kt` has four defects and one architectural problem.

**The architectural problem:** on API 30+ (both household devices are API 34), Media3's
`VideoFrameReleaseHelper` **already calls `Surface.setFrameRate()`** with
`VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS` by default. The platform performs the
refresh-rate match itself, seamlessly, with no HDMI re-sync black screen, whenever the panel
supports a seamless switch. `AfrManager` is a second, worse implementation of the same thing running
alongside it via `preferredDisplayModeId`.

**The defects:**
1. **25 fps and 50 fps content gets no match at all.** The matcher accepts a mode only within 2.0 Hz
   of the stream rate, so a 25 fps stream on a panel offering 50/60 Hz finds nothing
   (`|50 − 25| = 25`). **Integer multiples are the whole point of AFR** — 24→48/72, 25→50,
   29.97→59.94. On a UK/EU-heavy list this means AFR silently does nothing for most channels.
2. **No resolution filter.** `display.supportedModes` includes modes at other resolutions, so the
   matcher can switch the panel to e.g. 1280×720@50 — changing UI scale and causing a long re-sync.
   Modes must be filtered to the current mode's `physicalWidth`/`physicalHeight`.
3. **Only triggered by `onVideoSizeChanged`.** Two consecutive channels at the same resolution but
   different frame rates produce no callback, so the refresh rate stays wrong.
4. **`windowManager.defaultDisplay` is deprecated** and wrong on API 30+; and `restoreOriginalMode()`
   re-pins the original mode rather than setting `preferredDisplayModeId = 0` ("no preference"),
   which is what actually releases the panel.

**Decisions:**
- **Media3's seamless `Surface.setFrameRate` path is the default and needs no code.** Leave
  `videoChangeFrameRateStrategy` at its default.
- **`AfrManager` becomes the opt-in non-seamless fallback only**, for the case the seamless path
  can't cover (e.g. a 50 Hz switch on a panel currently at 60 Hz where the transition isn't
  seamless). Gate it on the existing **Settings → Playback → "Auto frame rate", default Off** (the
  grey row already exists in `SETTINGS.md`). Its current `isEnabled = true` default is wrong.
- **When rebuilt:** match on integer multiples (score `min over n∈{1,2,3,4} of |refreshRate − n ×
  fps|`, tolerance 0.2 Hz, tiebreak toward the higher refresh rate); filter modes to the current
  resolution; trigger on `onTracksChanged` and `onRenderedFirstFrame`, not `onVideoSizeChanged`;
  remember the applied `modeId` and skip if unchanged so zapping between two 50 fps channels doesn't
  re-sync HDMI; restore with `preferredDisplayModeId = 0`.
- **State the tension plainly in the Settings row's help text and in the code:** a non-seamless mode
  switch *is* an HDMI re-sync black screen of 1–2 s, which no amount of `keepContentOnPlayerReset`
  can hide. **AFR on means slower zaps.** That is physics, not a bug, and it is exactly why the
  default is Off and why the seamless path is preferred.

---

## 6. Network layer (`IptvNetworkModule.kt`) — two fixes the player depends on

1. **`getOkHttpClient()` builds a brand-new `OkHttpClient` on every call** — plus a throwaway
   bootstrap client — so every playlist fetch, every update check, and every player construction
   creates a fresh `ConnectionPool`, dispatcher thread pool and `SSLSocketFactory`. On a 449 MB
   device this is both wasted memory and wasted TLS handshakes, and it is the direct opposite of
   `PRODUCT_VISION.md` §4's "keep a pool of persistent connections open." **Decision: one
   lazily-created base `OkHttpClient` singleton; every variant derives from it with
   `newBuilder()`**, which shares the connection pool, dispatcher and TLS session cache. Rebuild
   the singleton only when `currentDnsProvider` changes.
2. **`PlayerHost` calls `IptvNetworkModule.getDataSourceFactory()` with no argument**, so a playlist
   configured with a custom User-Agent gets the *global* UA for its media requests while its API
   requests get the custom one. That is a 403 waiting to happen on exactly the providers a custom
   UA was added for. **Decision: thread the playlist's User-Agent (and any custom headers) from
   `ChannelEntity`/`PlaylistEntity` through to the data source factory.** Note this needs a
   playlist-level UA field if one doesn't exist yet.
3. Add `getMediaOkHttpClient()` with the aggressive live timeouts from §4.5, derived from the same
   base client.

---

## 7. Dependency decisions

- **Upgrade Media3 from 1.2.1** (January 2024) to the newest 1.x that builds **without raising
  `compileSdk` above 34**. The gain is real — years of MPEG-TS extractor and `MediaCodec` fixes,
  LL-HLS maturity, better decoder-fallback behaviour — and the risk is contained. **Bisect the
  version, don't guess it:** raise the version, build, and stop at the last one that compiles
  cleanly against `compileSdk 34`. **A `compileSdk` 35+/AGP bump is a separate, isolated change**,
  in the same spirit as the repo's existing "R8 stays off until it's its own change" rule.
- **Add** `androidx.media3:media3-exoplayer-dash` (2.1).
- **Add** the locally-built FFmpeg audio decoder module (2.3).
- **Do not add** RTSP, RTMP, libgav1, or libVLC.
- **Delete** `player/PlayerEngine.kt` (dead libVLC enum) and `settings/PlayerSettings.kt` (dead,
  unwired, and its defaults — 15 s min buffer, 50 s max — are wrong for this app; its real rows
  belong in `AppPreferences` + the Settings → Playback category, which `SETTINGS.md` already lays
  out as grey rows).

---

## 8. Measurement — what to record, so tuning stops being guesswork

`PHASE_2.md` #2.6 already specifies zap-latency and memory measurements. Extend both rather than
inventing a parallel scheme:

- **Zap latency:** log `key-down → onRenderedFirstFrame`, and split it: `key-down → prepare()`,
  `prepare() → first byte` (from the first `onLoadStarted`/`onLoadCompleted`), `first byte → first
  frame`. **The split is what tells you whether to work on network or on decoder/keyframe.**
  Without it, §4.6's prefetch question can't be answered.
- **Memory:** `adb shell dumpsys meminfo com.redsurf.tv` while playing, recording the Graphics/EGL
  line separately from Java heap — decoder and surface buffers live outside the Java heap and are
  the part that actually scales with tile count.
- **Record both on the Chromecast, on the real ~30K list.** `HARDWARE.md`'s rule: the Shield is the
  instrument for telling a Chromecast hardware limit from a RedSurf bug, not the device you tune
  against.

---

## 9. How this fits around `PHASE_2.md`

**One item must land before a specific PHASE_2 slice. Everything else is orthogonal or additive.**

### Must land first: hoist the player out of `PlayerHost` into a `PlayerController`

`PHASE_2.md` #2.3's remaining slice — the Actions row: Audio picker, Subtitles picker, Aspect
cycling, Video info — needs a handle on the `ExoPlayer`, the `TrackManager`, and the `PlayerView`.
Today all three are trapped inside `PlayerHost`'s `remember { }` block; the `TrackManager` instance
is constructed and **immediately discarded** (only its `trackSelector` escapes), so `PlayerScreen`
cannot reach `getAudioTracks`/`selectAudioTrack` at all. PHASE_2's own #2.3 note already flags this
as "needs `TrackManager` access inside `PlayerScreen` and a new command path into `PlayerHost` —
real architecture work."

**Decision: introduce `player/PlayerController.kt`, created via a `rememberPlayerController()` in
`PlayerScreen`, owning the `ExoPlayer`, `TrackManager`, AFR controller, error controller, and a
`StateFlow` of player UI state (stream info, buffering, error, resize mode).** `PlayerHost` shrinks
to a thin `AndroidView` that binds `controller.exoPlayer` to a `PlayerView`. This is the natural
home for almost every decision in this brief (renderers factory, extractors factory, load control,
error controller, watchdog), **and** it is what unblocks PHASE_2 #2.3 and #2.4 — so it is one
refactor serving both, not a detour.

Sequencing: **do the hoist, land the P0 items inside it in the same sprint, then resume #2.3's
Actions row on top of it.**

### Orthogonal — land whenever

§2 (renderers/extractors/codecs/DRM), §3.1 (buffer ceiling), §4 (ABR, errors, watchdog), §5 (AFR),
§6 (network), §7 (dependencies). None of these change any key binding, overlay state, focus
behaviour, or anything the Checkpoint A/B lists test. They change what plays and what happens when
it doesn't.

### Extends PHASE_2's decisions in two places, both to be logged in its "what actually happened"
sections

- **Decision 13's "never `stop()`"** is amended by §4.3. Same user-visible behaviour, different
  mechanism, `max_connections: 1` correctness gained.
- **Decision 13's `LoadControl` numbers** gain a byte ceiling and one changed value (`afterRebuffer`
  1500 → 2500) per §3.1. The time values stay pending #2.6's measurement, exactly as decision 13
  requires.

### Also fed by this brief

Several `SETTINGS.md` grey rows under **Playback** become implementable and should be flipped live
as their engine support lands: "Auto frame rate" (§5), "Buffer" (§3.1), "Preferred audio" (§2.8),
"Preferred subtitles" (§2.8), plus two new rows this brief adds — **"Audio output: Auto / Force
stereo"** (§2.4) and a diagnostic **"Force software decoder"** (§2.2).

---

## 10. Prioritized punch list

**P0 — correctness and reliability; do these first, inside the `PlayerController` hoist**

1. `PlayerController` hoist (§9). Unblocks everything below *and* PHASE_2 #2.3/#2.4.
2. `DefaultRenderersFactory` with `setEnableDecoderFallback(true)` (§2.2). One line, largest
   compatibility-per-effort ratio in the document.
3. `PlaybackErrorController` + `Player.Listener.onPlayerError`: classify 403/404/456/884, bounded
   backoff retry, `BehindLiveWindowException` recovery, visible message (§4.4).
4. `stop()` before every zap's `setMediaItem()` + `prepare()`, plus the single delayed 456 retry
   (§4.3).
5. `setTsExtractorFlags(FLAG_ALLOW_NON_IDR_KEYFRAMES)` (§2.6). Compatibility *and* zap latency.
6. `setTargetBufferBytes(20 MB)`; `bufferForPlaybackAfterRebufferMs` 1500 → 2500 (§3.1).
7. `TrackManager` defaults: text disabled by default, system-locale audio preference, persist the
   user's audio-language choice (§2.8).
8. Stall watchdog (15 s buffering → one `prepare()`) + media-specific OkHttp timeouts 5 s / 8 s
   (§4.5).
9. Single shared `OkHttpClient` in `IptvNetworkModule`; thread the per-playlist User-Agent into the
   media data source (§6).

**P1 — performance and format reach**

10. ABR thresholds sized to the actual buffer; seeded 20 Mbps initial bandwidth estimate (§4.2).
11. Runtime hardware-decoder probe → `setPreferredVideoMimeTypes`, excluding AV1 on this SoC;
    explicit "can't decode AV1" message when AV1 is the only track (§2.5).
12. MIME fallback ladder on `UnrecognizedInputFormatException` (§2.6).
13. `setAudioAttributes(..., handleAudioFocus = true)`; Settings → Playback → "Audio output: Auto /
    Force stereo" (§2.4).
14. Live knobs: `setLiveMinSpeed(0.95f)` / `setLiveMaxSpeed(1.06f)`, explicit
    `setAllowChunklessPreparation(true)` (§4.1).
15. Buffering/"tuning" indicator fed by the same listener (AGENTS.md backlog item 3; §4.4).
16. Media3 version bisect-upgrade within `compileSdk 34`; add `media3-exoplayer-dash` (§7).

**P2 — the extension and the housekeeping**

17. FFmpeg audio decoder extension: build, ABI-filter, commit prebuilt `.so` + rebuild script, wire
    `EXTENSION_RENDERER_MODE_ON` (§2.3). The DTS/MP2 fix; biggest single unit of work in the brief.
18. AFR rebuild: seamless path as default, `AfrManager` demoted to opt-in fallback with
    integer-multiple matching and a resolution filter, wired to its Settings row, default Off (§5).
19. `#KODIPROP` → `MediaItem.DrmConfiguration` in `M3uParser` (§2.7).
20. Delete `player/PlayerEngine.kt` and `settings/PlayerSettings.kt`; `ui/player/PlayerOsd.kt` is
    already slated for deletion by PHASE_2 #2.3 — agreed, delete it with the Actions row (§7).
21. Extend #2.6's measurements: split zap latency into three phases; record Graphics/EGL memory
    separately (§8).

**P3 — when multiview becomes a real phase**

22. Rewrite `MultiViewEngine` against `PlayerController` + `ChannelEntity`: shared allocator,
    per-tile 8 MB / 6 s budget, audio only on the focused tile, `getMaxSupportedInstances()`-derived
    tile cap defaulting to 2 (§3.2).
23. Verify DVB bitmap subtitles on a real EU channel; document teletext subtitles as a permanent
    non-goal (§2.8).

**Explicitly rejected, recorded so they aren't re-proposed:** libVLC dual engine; libgav1/software
AV1; RTSP/RTMP; next-channel prefetch; a second player for browse preview; a custom
`MediaCodecSelector` for hardware preference.

---

---

## 11. Error UX mapping (user directive, 2026-09-16 — added after this brief)

§4.4 specifies *what* to classify and *how* to retry. It does not specify what the user sees, and
that's not optional here: a raw `PlaybackException` code or an HTTP status number is not
acceptable UI text on a family TV app. The user's own words: friendly feedback on what an error
could mean, visual feedback in the UX so far where applicable, and this pattern should extend to
error handling anywhere else in the app going forward - not just the player.

**Decision: one `PlayerErrorMapper` object, single source of truth, called by exactly one place
(the `PlaybackErrorController` of §4.4).** No error copy gets written ad hoc at a call site.

```kotlin
data class PlayerErrorPresentation(
    val message: String,       // short, plain language, no codes/exception names
    val isRetrying: Boolean,   // true = show a subtle "reconnecting" state, not an alarming one
    val isTerminal: Boolean,   // true = retries exhausted / not retryable, needs user action (change channel)
)

object PlayerErrorMapper {
    fun present(error: PlaybackException): PlayerErrorPresentation
}
```

**Copy, mapped from §4.4's classification (examples, not exhaustive - extend as new codes are
actually hit on real channels, don't pre-write copy for codes never seen):**

| Cause | User sees | State |
|---|---|---|
| HTTP 403 | "This channel isn't available right now" | terminal |
| HTTP 456 (§4.3's race) | *(nothing yet — see below)* → "Reconnecting…" only if the one bounded retry hasn't resolved it within ~1s | retrying → terminal only if the retry fails |
| HTTP 884 | "This channel is temporarily locked by the provider" | terminal |
| HTTP 404 / channel gone | "Channel not found" | terminal |
| Network timeout/IO error | "Reconnecting…" | retrying, escalates to "Can't reach this channel — try another" only after all 4 backoff attempts (§4.4) fail | 
| Container unsupported, both MIME retries failed | "This channel's format isn't supported" | terminal |
| AV1-only stream (§2.5) | "This device can't play this channel's video format" | terminal |
| Stall watchdog fired once (§4.5) | "Reconnecting…" | retrying |
| Stall watchdog fired twice in 60s | "Having trouble with this channel — try another" | terminal |

**UX placement:** reuse the OSD's existing badge/indicator slot (the same one the "tuning"
indicator uses, `AGENTS.md` backlog item 3) - a small, non-blocking on-screen line, never a modal
dialog that steals D-pad focus. Retrying states are deliberately low-key (viewers shouldn't panic
over a transient blip that resolves in under a second); terminal states are a touch more visible
and stay on screen until the user changes channel or the stream recovers on its own. Auto-dismiss
the instant real video frames resume (`onRenderedFirstFrame`).

**Standing pattern, not just for the player:** the same shape - a central mapper, friendly
non-technical copy, non-blocking visible feedback, retrying vs. terminal distinction - is now the
expected approach anywhere else in the app that surfaces a failure (playlist import errors, EPG
sync failures, update-check failures, etc.), per the user's own framing. Flag it when building or
touching any other error path, even outside this phase.

---

### Critical files for implementation

- `tv-native/app/src/main/java/com/redsurf/tv/player/PlayerHost.kt` — becomes the thin `PlayerView`
  binding; source of the `PlayerController` extraction; where the renderers/extractors/load-control
  decisions land.
- `tv-native/app/src/main/java/com/redsurf/tv/player/tracks/TrackManager.kt` — track-selection
  defaults, ABR factory, preferred-MIME probe, persisted audio language.
- `tv-native/app/src/main/java/com/redsurf/tv/network/IptvNetworkModule.kt` — shared `OkHttpClient`,
  media-specific timeouts, per-playlist User-Agent threading.
- `tv-native/app/src/main/java/com/redsurf/tv/player/tuning/AfrManager.kt` — demoted to opt-in
  fallback; integer-multiple matching and resolution filtering.
- `tv-native/app/build.gradle.kts` — Media3 version bisect, DASH module, FFmpeg extension module and
  ABI filters.
- `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt` — owns the new
  `PlayerController`; consumes error/buffering state; the seam where this brief meets `PHASE_2.md`
  #2.3/#2.4.
