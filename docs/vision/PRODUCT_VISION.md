# TVMime v1.0: Premium Android TV Product Vision
**Documented by AI Product Architect | Target: TiviMate / IMPlayer Parity & Beyond**

## 1. The Core Paradigm: "The Player IS The App"
Premium IPTV apps do not have standard Android navigation drawers or separate "screens" for live TV. The video player is the perpetual background. All UI elements (Guide, Settings, Channel Lists) are translucent glass panes overlaid on top of the running video.

*   **Current Flaw:** The app uses a standard `TvNavigationDrawer` which feels like a mobile app ported to TV.
*   **V1.0 Solution:** Rip out the permanent drawer. Boot directly into fullscreen playback of the last watched channel.

## 2. The D-Pad Control Matrix (The Industry Standard)
Muscle memory dictates how users interact with TV remotes. We must map exactly to expectations:
*   **[ CENTER / OK ]:** 
    *   *Short Press:* Brings up the Bottom HUD (Current program, timeline, next program, resolution, audio format) and the Quick Controls row (Play/Pause, Audio Tracks, Subtitles, Aspect Ratio).
    *   *Long Press:* Opens the Context Menu (Add to Favorites, Lock Channel, Report Issue).
*   **[ LEFT ]:** 
    *   Slides out the Master Channel List from the left edge. 
    *   *Column 1:* Categories/Groups (Favorites, News, Sports).
    *   *Column 2:* Channels in that category (with current playing EPG text).
*   **[ RIGHT ]:**
    *   Slides out the Mini-EPG (Timeline for the currently tuned channel) OR executes Last Channel Zap (user configurable).
*   **[ UP / DOWN ]:**
    *   *While HUD is hidden:* Seamlessly Zap to the next/previous channel in the current group.
    *   *While HUD is visible:* Navigate UI elements.

## 3. The "Zap" Experience (Channel Changing)
Channel surfing must feel instantaneous.
*   **Visuals:** When changing channels, show a sleek Bottom Banner featuring: Channel Number, Logo (if available), Name, Current Program Name, Progress Bar of current program, Next Program Name, and stream stats (1080p, 60fps, Dolby). 
*   **Performance:** Implement a "Black Screen Minimizer". Do not clear the video surface until the first I-frame of the next stream is ready. Use a quick crossfade.

## 4. Hardware & Networking (The "No Buffering" Tech Stack)
To achieve ultimate speed, we must bypass standard Android media limitations.
*   **Ktor / OkHttp Connection Pooling:** IPTV providers are slow. We will keep a pool of persistent connections open to the portal to reduce TLS handshake times on channel changes.
*   **Media3 `LoadControl` Tuning:** 
    *   *Fast Zap Mode:* Start playback the millisecond we have 500ms of buffer.
    *   *Stable Mode:* Rapidly build up to a 10-second buffer in the background to survive network jitter.
*   **Hardware Decoder Locking:** Force `MediaCodecVideoRenderer` to prioritize hardware (VPU) over software (CPU). Software decoding 1080p60fps streams on a Firestick causes immediate thermal throttling.
*   **Auto Frame Rate (AFR):** Detect if the stream is 24fps (Movies), 25/50fps (UK/EU TV), or 30/60fps (US TV) and seamlessly switch the TV panel's refresh rate to match, eliminating telecine judder.
*   **Audio Passthrough:** Pass Dolby Digital / AC3 directly to the soundbar/receiver via HDMI ARC instead of decoding it on the TV.

## 5. Expanded Settings Dashboard
The settings must empower power users while remaining clean.
*   **Playback:** Hardware vs Software Decoder toggle, AFR Toggle, Audio Passthrough toggle.
*   **Buffer Size:** Network Cache size (Fast Zap vs Stability Mode).
*   **Appearance:** Customize OSD timeout, Clock position, Channel sorting (by number vs alphabetical).
*   **EPG:** Update frequency (Every start, Every 12 hours, Daily), Timezone Offset (to fix provider time sync issues).

## 6. The Dual-Model Data Sync Lifecycle (Cloud + Local)
TVMime operates a hybrid edge-compute model to bypass Vercel serverless limitations while delivering seamless multi-device sync:
*   **The Cloud (Vercel/Firebase):** Acts exclusively as a lightweight credential locker. It stores M3U/Xtream URLs, Usernames, and Passwords. It NEVER processes massive 50MB M3U playlists.
*   **The Edge (Android TV CPU):** Handles all heavy lifting. The TV fetches credentials from the cloud and connects directly to the IPTV provider, parsing the 100,000+ channel JSON locally using low-memory stream readers into a Room Database.
*   **UX Flow:** Users can pair via a 6-digit code to sync credentials from the Web Admin, OR they can manually type credentials into the TV. Locally added playlists can be pushed up to the cloud via an "Upload to Web Admin" button. All playlists feature a visual indicator (☁️ Cloud vs 📺 Local) in the manager.
