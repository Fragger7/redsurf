# 📺 IPTV Domain Engineering & Networking Knowledge Base

This document contains critical domain-specific knowledge for building IPTV applications (specifically Xtream Codes and Stalker portals). It highlights hard-fought engineering solutions, network evasion tactics, and memory management constraints. 

**This should be read by any AI or developer architecting a new IPTV player to prevent critical regressions.**

---

## 1. The OOM (Out Of Memory) JSON Trap (CRITICAL)

**The Problem:** 
IPTV APIs (like the Xtream Codes `player_api.php?action=get_live_streams`) frequently return massive, monolithic JSON arrays. A standard provider's catalog can easily exceed **50MB to 100MB of raw text** containing 100,000+ channel and VOD objects. 
If you attempt to parse this response using standard high-level object mappers (Gson, Moshi, kotlinx.serialization, or standard Python `json.loads` on low-memory devices), the Android OS will instantly throw an `OutOfMemoryError` (OOM) and crash the app, particularly on RAM-constrained devices like Firesticks or older Android TVs.

**The Solution:**
You must bypass high-level memory mapping. Network responses for massive catalogs must be parsed token-by-token directly from the network socket buffer.
*   **Android/Kotlin Implementation**: Use the low-level `android.util.JsonReader`. Stream the response directly from OkHttp's `ResponseBody.charStream()` and manually traverse the JSON tree (`beginArray()`, `beginObject()`, `nextName()`), yielding objects incrementally into a local SQLite/Room database or UI state.

---

## 2. Anti-Bot Network Evasion & Handshakes

**The Problem:** 
Many IPTV providers utilize Cloudflare, aggressive CDNs, or custom firewalls that automatically block default HTTP clients (like OkHttp's default User-Agent, Python's `requests`, or ExoPlayer's default agent). 

**The Solution (Xtream Codes):**
*   All HTTP requests (both API calls and actual media stream requests via ExoPlayer/Media3) must spoof a recognized player User-Agent. 
*   **Standard Evasion Agent**: `User-Agent: IPTVSmartersPro/1.1.1` (or similar heavily whitelisted legacy players).
*   Avoid sending unnecessary modern headers that give away the fact that it is a programmatic scraper.

**The Solution (Stalker Portals):**
*   Stalker portals are even stricter. They require older Set-Top Box user-agents (e.g., `Mozilla/5.0 (QtEmbedded; U; Linux; C) MAG200...`).
*   They require a multi-step handshake where the MAC address is injected as a raw Cookie (`Cookie: mac=00:1A:79:XX:XX:XX`).

---

## 3. Media3 / ExoPlayer Handling & Formats

IPTV streams generally arrive in two formats, requiring robust player handling:

1.  **MPEG-TS (`.ts` / Direct Socket):** Raw transport streams. These are highly susceptible to network jitter. The player must be configured with a robust buffering strategy. 
2.  **HLS (`.m3u8`):** HTTP Live Streaming playlists. 

**Engineering Constraints for Playback:**
*   **Data Source Factory:** When instantiating ExoPlayer/Media3, you must use a custom `DefaultHttpDataSource.Factory` that injects the evasion `User-Agent` (see Section 2). If you use the default factory, the provider will return HTTP 403 when the player tries to fetch the video chunks.
*   **Hardware Acceleration:** Always prioritize hardware video decoding.
*   **Egress Blocks (Ghost Lines):** Be aware that a provider may return `HTTP 200 OK` for the API login, but return `HTTP 456` (Stream Egress Disabled) or `HTTP 884` (Anti-Dump Lockout) when ExoPlayer actually attempts to fetch the `.ts` stream. The player UI must gracefully handle these specific HTTP media errors.

---

## 4. Live Socket Latency & Fast-Fail Hedging

**The Problem:** 
When querying massive lists of streams or pinging a server, dead IPTV servers will hang the network thread until the absolute OS socket timeout (which can be 30-60 seconds), freezing the application UI.

**The Solution:**
*   Implement aggressive, bounded coroutine timeouts (`withTimeoutOrNull`) for all network operations.
*   Use Fast-Fail Hedging: If a host throws `UnknownHostException` (DNS failure) or `ConnectException` (Connection Refused), immediately mark the host as dead. Do not waste time retrying with different User-Agents, as the server is fundamentally unreachable.

---

## 5. Architectural Separation of Concerns

While this is domain knowledge, the architecture implementing it must remain decoupled:
1.  **Network Layer**: Pure OkHttp + Coroutines + `JsonReader`.
2.  **Data Layer**: Must heavily utilize a local database (e.g., Room) to cache the 100,000+ channels. The TV UI cannot hold 100k objects in RAM.
3.  **UI Layer**: Jetpack Compose for TV (using `androidx.tv.foundation.lazy.list` / `androidx.tv.material3`) for proper D-Pad focus management and Leanback design paradigms.

---

## 6. Category Bloat & Real-Time Preference Filtering

**The Problem:**
IPTV providers commonly return hundreds of international categories (e.g., Arabic, Russian, Portuguese, Polish, Turkish, German) that a single subscriber will never watch. Displaying 300+ categories in a TV sidebar introduces severe scroll fatigue on a 5-button D-Pad remote.

**The Solution:**
*   Persist a user blacklist (`Set<String>`) of hidden category IDs inside local device `SharedPreferences`.
*   Filter categories dynamically in the presentation `StateFlow` (`combine(activePortal, currentDestination, hiddenCategories)`).
*   Provide a 1-click or long-press action (`onLongClick`) on category cards so users can prune their guide in seconds without deleting provider records from the database or requiring cloud round-trips.

---

## 7. Android TV Remote Focus Management & Trap Prevention

**The Problem:**
In standard Android TV development with Jetpack Compose, toggling visibility of overlays (such as sliding out a channel list or HUD over a running video surface) frequently results in "lost remote focus". The TV remote becomes completely unresponsive because focus is left dangling on a disposed composable.

**The Solution:**
*   Assign dedicated `FocusRequester` instances to each discrete UI state (`playerFocusRequester`, `hudFocusRequester`, `channelListFocusRequester`, `settingsFocusRequester`).
*   Drive focus acquisition via a centralized `LaunchedEffect(overlayState)`.
*   Always ensure the root container has a fallback focus target (`playerFocusRequester`) when all overlays close.

---

## 8. Fast Channel Zapping & Black Screen Elimination

**The Problem:**
Standard media player implementations clear the video surface upon switching stream URLs, creating an jarring 1-2 second black screen blink on every channel change.

**The Solution:**
*   **Surface Retention:** Configure Media3/ExoPlayer video surface view to retain the last rendered frame until the first decoded video frame (I-frame) of the new stream is dispatched to the surface.
*   **Fast Zap Load Control:** Tune `DefaultLoadControl` to request initial playback as soon as 500ms of data is in the socket buffer, rather than waiting for 2500ms+.
*   **Sequential Channel Cycling:** Implement `zapNext()` and `zapPrevious()` directly in the ViewModel to cycle through adjacent channels in the active category index without opening the full channel list.

---

## 9. Fake Category Header Rows ("Separator Channels")

**The Problem:**
IPTV providers frequently inject non-stream pseudo-channels to visually divide groups in traditional M3U apps (e.g., `##### 4K SPORTS #####`, `=== UK PREMIUM ===`, `--- CINEMA ---`). These entries have real `stream_id`s in Xtream JSON responses but their stream targets immediately return `HTTP 404 Not Found` or cause infinite buffer loops when tuned.

**The Solution:**
*   Sanitize streams in the parser layer (`StreamingCatalogParser.kt`) using regex boundary checking:
    ```kotlin
    val isSeparatorHeader = name.matches(Regex("^[#=\\-_~*]{3,}.*|.*[#=\\-_~*]{3,}$"))
    if (isSeparatorHeader) return null // Discard from Room database
    ```
*   Never insert separator rows as playable channel records.

---

## 10. Concurrency Limits & Strict Socket Disconnects

**The Problem:**
Commercial IPTV portals strictly enforce `max_connections: 1`. If an ExoPlayer instance begins loading a new channel before the old TCP socket has cleanly severed, the provider's reverse proxy detects 2 concurrent streams and responds with `HTTP 456` (Too Many Connections) or drops the stream entirely.

**The Solution:**
*   Upon every channel switch, immediately execute `exoPlayer.stop()` and `exoPlayer.clearMediaItems()` before enqueueing the next stream item.
*   Release HTTP DataSources immediately in `DisposableEffect(exoPlayer) { onDispose { ... } }`.

---

## 11. Cleartext Traffic & Edge CDN 302 Redirects

**The Problem:**
IPTV portals often provide `http://` or `https://` entrypoints that issue `HTTP 302 Found` redirects to bare-IP edge streaming relays (e.g., `http://185.x.x.x:8080/...`). On Android 9+ (API 28+), network security defaults block all cleartext HTTP traffic, causing silent `IOException: Cleartext HTTP traffic to ... not permitted` crashes.

**The Solution:**
*   Explicitly define `network_security_config.xml` with `<base-config cleartextTrafficPermitted="true" />` and reference it in the `AndroidManifest.xml`.

---

## 12. Streaming Parser Null-Safety ("Expected a string but was NULL")

**The Problem:**
Different Xtream Codes / IPTV panel backends (e.g., Xtream UI, 1Stream, ZapX) omit or return raw `null` literals for optional channel fields such as `"stream_icon": null`, `"epg_channel_id": null`, `"category_id": null`, or `"custom_sid": null`.
Calling low-level Gson `JsonReader.nextString()` on a `JsonToken.NULL` throws an unhandled `IllegalStateException: Expected a string but was NULL`. This unhandled exception abruptly terminates the entire catalog stream parser mid-ingestion, rolls back/aborts channel and VOD insertion, and leaves the app in a state where category groups appear with counts (from the earlier category sync) but channel/movie lists are completely empty.

**The Solution:**
*   Always inspect the token type before consuming string values:
    ```kotlin
    fun JsonReader.nextStringOrNull(): String? {
        if (peek() == JsonToken.NULL) {
            nextNull()
            return null
        }
        return nextString()
    }
    ```
*   Ensure that parsing individual channel entities has fallback isolation so that a corrupt row does not crash the entire ingestion stream.

---

## 13. Multi-Portal Aggregation vs. Single Active Portal Architecture

**The Problem:**
Users often subscribe to multiple IPTV services (e.g., one optimized for live sports, another for international channels or VOD). When multiple portals are activated in the web admin/cloud sync, displaying only a single active portal forces the user to constantly switch portals in Settings to see their content, which breaks the seamless TiviMate / IMPlayer experience.

**The Solution:**
*   **Unified Query Model:** In `XtreamRepository` and Room DAO, support querying across all portals where `isActive = true`.
*   **Visual Grouping & Collision Prevention:** Prefix channel IDs by portal ID (already implemented as `${type}_${streamId}` -> should be `${portalId}_${type}_${streamId}`) to prevent collisions between identical stream IDs across different providers.
*   **Group / Playlist Filtering:** In the Channel List overlay and TV Guide, display a Playlist selector pill or group categories by `[Playlist Name] Category Name` so channels from all active playlists can be navigated concurrently.

