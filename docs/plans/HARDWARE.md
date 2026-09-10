# Target Hardware

## The principle: design for scale, test on the weakest box

The user's real playlists run to **~60K channels and 150K+ VOD items**, and the household has more
than one device — a **Shield Pro** in another room alongside the Chromecast below. So two rules
that must not be confused with each other:

1. **Nothing in the app may assume a channel-count ceiling.** Lazy loading (Room `PagingSource`,
   paged lists, streaming imports with batched inserts, bounded image caches) is how the app stays
   flat in memory regardless of playlist size — see `IPTV_DOMAIN_KNOWLEDGE.md` §1, §6, §13. A
   hard cap on data is a bug, not a safety measure.
2. **The Chromecast is still the primary test device**, because it's the weakest hardware the app
   has to be good on — if it's smooth there, it's smooth on the Shield. The measured limits below
   are facts about *this box*, used to size caches and catch regressions. They are not product
   limits. When something looks like a hardware ceiling, the Shield is how to tell a Chromecast
   limit from an app bug.

## Primary test device: Chromecast with Google TV (1st gen, 4K, "Sabrina")

**All values below were read off the device over ADB on 2026-09-10 — measured, not assumed.**

| Spec | Value | Consequence for RedSurf |
|---|---|---|
| SoC | Amlogic, 4 cores | Modest CPU. No heavy work on the main thread, ever. |
| **RAM** | **1.89 GB total — 449 MB free in normal use** | **The binding constraint on this whole project.** |
| **Storage** | **3.9 GB `/data`, 880 MB free (79% full)** | Tight. Lean APK, capped Room DB, never cache raw playlists to disk. |
| OS | **Android 14, API 34** | `compileSdk 34` already matches exactly. `minSdk 23` is fine. |
| Video | 4K HDR, Dolby Vision, HDR10+ | H.264, HEVC, VP9 hardware-decoded. AV1 is not (verify only if it matters). |
| Network | Ethernet via official adapter | Stable, and ADB works over it — no need to switch to Wi-Fi. |
| Remote | D-pad, Back, Home, Assistant, app shortcuts | **No number keys. No colour buttons.** |
| RedSurf installed? | **No** | First install will be release-signed, so no uninstall dance is needed. |

ADB device id: `192.172.7.160:35631`, `product:sabrina_prod_stable`.

## What 1.89 GB of RAM actually means

Worse than the "2 GB" on the spec sheet: the OS reports **1.89 GB total with roughly 449 MB
actually available**. That is the real budget RedSurf has to live inside, and it is small.

This is the right device to develop against — if RedSurf is fast here, it is fast everywhere.
Every rule in `../vision/IPTV_DOMAIN_KNOWLEDGE.md` §1 ("The OOM JSON Trap") is mandatory, not
advisory:

- **Never** load a full M3U or Xtream JSON response into memory. Stream-parse to Room in batches.
- **Never** hold the full channel list in a ViewModel. Page from Room; let SQLite own the data.
  *(The current `MainViewModel.checkLocalCache` and `performSearch` both load every channel into
  memory and filter in Kotlin. Both must go.)*
- Load channel logos lazily with a bounded memory cache.
- Watch for OOM on XMLTV EPG ingest — batch inserts, clear as you go.

## Multiview ceiling: 2 tiles, and even that is not guaranteed

Each tile is a live decode session plus buffers. With **449 MB actually free**, **two concurrent
streams is the optimistic maximum on this device**, and the secondary tile will likely need to be
a lower-resolution variant. Treat 2 as a target to be *measured*, not a promise — the first real
multiview test on this box may come back saying 1.

Do not report multiview as working on the basis of it compiling. Watch it run, on this device,
with `adb shell dumpsys meminfo com.redsurf.tv` open.

Build multiview with the tile count as a **configurable limit that defaults to 2 on this device
class**. Do not hardcode a 2×2 grid. On a Shield or a modern Google TV Streamer, raise it.

This is a hardware limit, not a code-quality limit — no implementation makes 4× 1080p decode fit
in 2GB.

## Remote input consequences

The CCwGTV remote has no number pad, so any "type the channel number to jump" feature needs
another affordance (an on-screen number pad, or search). Do not design around number keys.

If the user drives the app with their **LG TV remote via HDMI-CEC** instead, that remote *does*
have number and colour buttons — confirm before building anything that depends on them.

## ADB works over Ethernet

ADB over network is IP-based; it does not care whether the device is on Wi-Fi or Ethernet.
**There is no need to unplug the Ethernet adapter.**

To enable, on the device:
1. Settings → System → About → click **Android TV OS build** 7 times.
2. Settings → System → Developer options → enable **ADB debugging** (and **Network debugging** if
   listed separately).
3. Note the device IP: Settings → Network & Internet → (the Ethernet connection).

Then from the laptop: `adb connect <device-ip>:5555`, accept the prompt on the TV.

This gives us `adb install` and — critically — **`adb logcat`**. Without logcat, diagnosing a
crash on the TV is guesswork from a verbal description.
