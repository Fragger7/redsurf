# 🚨 ACTIVE DIRECTIVE: THE PHOENIX PROTOCOL (V4 Architecture Re-Pivot) 🚨
> **Date of Pivot:** September 2026
> **The Catalyst:** Sprints 16-19 attempted a monolithic "organ transplant" of StreamVault's UI, dragging in Dagger Hilt and coupling our pristine backend to 19 heavily stubbed, alien domain repositories. This caused cascading compile failures and broke the KMP architecture path.
> **The Solution:** We are executing the "Phoenix Protocol". We will rip out Dagger Hilt and all proxy stubs. We will use StreamVault *purely* as a visual inspiration. We will write 100% Stateless Jetpack Compose components and feed them data via a clean Orchestrator Pattern.

### Sprint 20: The Hilt Purge
- Delete `MockDomainModule.kt` and all proxy stubs (e.g., `Stubs.kt` variants).
- Remove `@HiltAndroidApp` and `@AndroidEntryPoint` annotations.
- Strip Dagger Hilt dependencies from `build.gradle.kts`.

### Sprint 21: Stateless UI Cloning (Live TV)
- Create `LiveTvGrid.kt` as a pure, dumb `@Composable`.
- Steal StreamVault's layout, focus scaling, and colors (mapping to `DesignSystemTokens`).
- Signature: `@Composable fun LiveTvGrid(categories: List<CategoryEntity>, channels: List<ChannelEntity>, onChannelSelect: (ChannelEntity) -> Unit)`

### Sprint 22: The Orchestrator Wiring (The Golden Path)
- Create `LiveTvViewModel.kt` (Standard Android ViewModel). Inject `AppDatabase`.
- Create `LiveTvRoute.kt` to collect `StateFlow<LiveTvUiState>` and pass it to `LiveTvGrid`.
- Map `onChannelSelect` to `EngineController.startLivePreview()`.

---

# 📚 HISTORICAL CONTEXT & SPRINT LOG
*The following chronicles the history of the TVMime V3 backend construction.*

## Goal Description
Build a high-performance, modern IPTV player for Android TV (with full D-Pad support) and Android Mobile. The player will support Xtream Codes (Phase 2) and Stalker portals (Future Phase), providing a zippy experience similar to TiviMate and IMPlayer. 

Key features include Live TV, VOD (Movies & Series), EPG with logos, and standard TV watching controls. A critical addition is **Chromecast / Android TV Casting capability from the Mobile app**.

Instead of restraining our architecture to basic lessons, we will model our foundation after state-of-the-art open-source repositories like **StreamVault** and **OwnTV** (both built in Kotlin with Jetpack Compose for TV), adopting their best practices for handling massive M3U/Xtream lists and D-Pad focus management.

## User Review Required

> [!TIP]
> **Firebase Free Tier Viability**
> You asked if the Firebase free tier is enough. **Yes, it is more than enough, provided we use it correctly.**
> We will NOT store the massive 50MB+ channel lists in Firebase. Instead, Firebase Firestore will only store the user's **Portal Credentials** (Xtream URL, Username, Password) and lightweight data (like Favorite Channel IDs). 
> The Android TV / Mobile app will fetch these tiny credentials from Firebase, and then make the heavy API request *directly* to the IPTV provider, caching the massive channel list locally on the device using SQLite (Room). This ensures we stay well within the free 1GB storage and 50k reads/day limits.

## Proposed Changes

### Application Architecture (Future-Proofed for Apple TV)
To ensure we can seamlessly port this application to **Apple TV (tvOS)** and **iOS** in the future, we will build the foundation using **Kotlin Multiplatform (KMP)**. This is a critical strategic choice.

- **Shared Core (KMP):** All business logic, network requests, JSON parsing, and local database caching will be written in a shared Kotlin module. This means when you decide to build the Apple TV version, 90% of the app's code is already done.
  - **Networking:** Ktor (KMP compatible) instead of OkHttp.
  - **Database:** Room (which recently became KMP compatible) or SQLDelight for local caching.
  - **Design System Tokens:** A centralized Kotlin object defining exact Hex Colors, Typography scales, and padding/spacing values. Every platform (Android Mobile, TV, Web) will pull from this single source of truth to guarantee pixel-perfect brand consistency. The canonical design reference for the entire ecosystem (including the Web Admin Portal) is the `tv_livetv_red_black.jpg` mockup—featuring a Deep Black background, vibrant Crimson Red accents, sleek typography, and high-performance subtle translucency.
- **Native UI Layer:**
  - **Android TV / Mobile (Current):** Jetpack Compose (Material 3) & Compose for TV. We will stick with a **Left-side category menu** for the TV UI as it is intuitive and industry standard (TiviMate style).
  - **Apple TV (Future):** We will build a native SwiftUI interface that simply plugs into our shared KMP core.
- **Media Player Interface:** We will create a shared interface for the video player. On Android, it will use hardware-accelerated **AndroidX Media3 (ExoPlayer)**. In the future on Apple TV, it will map to **AVPlayer**.

---

---

### Phase 1: Cloud Presence & Admin Foundation [COMPLETED]
- Built `adminWeb/` React 19 + Vite 6 + Tailwind CSS v4 hosted on Vercel at [tvmime.vercel.app](https://tvmime.vercel.app).
- Firebase Authentication (Email/Password & Anonymous) under `tvmime-65909`.
- Firestore database for `UserPortals` syncing across devices.
- Serverless proxy `/api/test-portal` for CORS and mixed-content IPTV validation.
- Direct TV and Mobile APK download cards on landing page.

---

### Phase 2: Network & Data Layer (Xtream Codes - Shared KMP) [COMPLETED]
- **`shared/src/commonMain/.../repository/XtreamRepository.kt`**: Fetches categories, channels, and EPG data from Xtream Codes APIs with spoofed User-Agent (`IPTVSmartersPro/1.1.1`).
- **`shared/src/commonMain/.../network/StreamingCatalogParser.kt`**: Zero-OOM token-by-token JSON parsing directly into local SQLite database.
- **`shared/src/commonMain/.../db/AppDatabase.kt`**: Multiplatform Room Database caching categories, channels, and EPG schedules.
- **`shared/src/commonMain/.../sync/FirebaseSyncClient.kt`**: Cloud portal synchronization with local Room storage.

---

### Phase 3: Mobile App & Chromecast [IN PROGRESS]
- [x] Scaffold Mobile Jetpack Compose foundation (`androidApp/src/main/java/com/tvmime/mobile/MainActivity.kt`).
- [ ] Implement Mobile EPG and channel grid with touch optimization.
- [ ] Complete `androidx.media3:media3-cast` receiver discovery and remote playback transfer.

---

### Phase 4: Android TV UI (Compose for TV) [COMPLETED]
- **`tvApp/.../ui/navigation/TvNavigationDrawer.kt`**: Collapsible left navigation drawer with D-Pad focus handling.
- **`tvApp/.../ui/live/LiveTvScreen.kt`**: Split channel browser with category column, channel list, and embedded preview player.
- **`tvApp/.../ui/player/TvVideoPlayer.kt`**: Fullscreen hardware-accelerated Media3 player:
  - Top information bar (CH number, name, live resolution, active playlist badge, and active/max connection ratio).
  - Dedicated persistent top-right clock overlay pill.
  - In-playback controls (Play/Pause, Aspect Ratio, Audio Tracks sheet, Subtitles sheet, Telemetry HUD, Favorite toggle, TV Guide launcher, Issue Reporting).
  - D-Pad Right Last Channel Quick Zap with animated top-center toast.
  - Live Telemetry HUD (bitrate, remote host/IP, buffer depth & % cached, decoders).
- **`tvApp/.../ui/guide/TvGuideScreen.kt`**: TiviMate-style EPG grid guide with 30-minute time intervals and PIP mini-preview.
- **`tvApp/.../ui/settings/SettingsScreen.kt` & `TvPreferencesManager.kt`**: Interactive D-Pad preferences (Clock toggle, OSD timeout picker, Last Channel zap toggle).
- **`tvApp/.../ui/onboarding/OnboardingScreen.kt`**: Quick TV pairing wizard & demo portal loader.
- **`tvApp/.../ui/about/AboutScreen.kt`**: Crediting Faraz Ahmad, architecture specifications, and dynamic runtime version code.

---

### Phase 5: CI/CD & Automated Semantic Versioning [COMPLETED]
- Standalone repository: `https://github.com/Fragger7/tvmime.git`.
- GitHub Actions workflow (`.github/workflows/build.yml`) compiling both Android TV and Mobile APKs.
- Automatic semantic version bumping (`v1.x.0` via conventional commits) and strictly monotonic build codes (`git rev-list --count HEAD`).
- Permanent TV APK download link redirecting to `https://tvmime.vercel.app/tv.apk`.
- Cryptographic debug keystore locked to preserve 100% in-place OTA update compatibility.

## Verification Plan

### Automated Tests
- Unit tests for Firebase data mapping.
- Unit tests for Xtream JSON parsing to Room Entities.

### Manual Verification
- Add a portal credential via the Cloud/Mobile UI, launch the TV app, and verify it automatically syncs the portal and begins downloading the channels.
- Cast a Live TV stream from the Android Mobile app to a local Chromecast / Android TV device.
- Navigate the TV UI using only D-Pad arrows, ensuring no focus traps exist.

---

### Sprint 2: Usability, Focus, and Navigation Refinement [COMPLETED]
- **Channel Zapping (UP/DOWN):** Implemented `zapNext()` and `zapPrevious()` in `TvMainViewModel.kt` and mapped them to the `DPAD_UP` and `DPAD_DOWN` hardware keys in `MainActivity.kt` when HUD overlay is hidden.
- **Focus Management (No Lost Remotes):** Solved Compose TV focus traps by instantiating distinct `FocusRequester` instances for each overlay (`CHANNEL_LIST`, `HUD`, `SETTINGS`) and programmatically requesting focus via a `LaunchedEffect` whenever the `overlayState` changes.
- **Long-Press Context Menu & Group Hiding:** Extended `TvPreferencesManager.kt` to persist a `Set<String>` of hidden category IDs to `SharedPreferences`. Modified `TvMainViewModel.kt`'s `categories` StateFlow to intercept and filter out hidden categories in real-time. Wired `onLongClick` in `LiveTvScreen.kt` CategoryCard.
- **Channel State Integrity:** Restored `playChannel` with previous-channel tracking for instant D-Pad Right swap and watch-history tracking in Room.

### Sprint 3: The Architecture Pivot & "Sohva-TV" Paradigm [COMPLETED]
- [x] **Phase 3.1: D-Pad Raw Event Overhaul** (Pass 2) - Rip out `TvNavigationDrawer` and replace with raw Z-index 0 `AndroidView` ExoPlayer intercepting `KeyEvent` routing to Compose overlay panes.
- [x] **Phase 3.2: ExoPlayer Ghost Connection Fix** (Pass 2) - Introduce `LifecycleEventObserver` to drop TCP sockets on `ON_STOP` to respect provider limits.
- [x] **Phase 3.3: Tuned Buffer Profiles** (Pass 2) - Implement Sohva-TV tuned math for Fast Zap (`LOW_LATENCY`) and 4K Deep Buffer (`STABILITY`) profiles.
- [x] **Phase 3.4: Multi-Portal Aggregation DAOs** (Pass 3) - Update Room DAOs to support multiple `isActive = true` portals.
- [x] **Phase 3.5: Unified ViewModel Aggregation** (Pass 3) - Refactor `TvMainViewModel` to combine flows across `activePortals` for categories, channels, and EPG.
- [x] **Phase 3.6: Cloud Sync UX Toggle** (Pass 3) - Update `CloudSyncScreen` to act as checkboxes (Toggle Activate/Deactivate) rather than radio buttons.
- [x] **Phase 3.7: TiviMate Accordion & Focus Retention** (Pass 3) - Group channels by provider in `LiveTvScreen` using collapsible headers and hoist `TvLazyListState` to prevent scroll position loss.

### Sprint 4: Systems-Level Architecture & Polish
- [x] **Phase 4.1: Background Sync Workers** - Implement Android `WorkManager` for silent EPG/Channel refreshing (every X hours / on launch) with user settings.
- [x] **Phase 4.2: EPG Local Time-Shift** - Refactor EPG logic to auto-map UTC to the local device timezone, and add a manual timezone offset slider in Settings for sloppy providers.
- [x] **Phase 4.3: Catch-Up TV (DVR)** - Allow scrolling backwards in the EPG and construct the specialized `/timeshift/{stream_id}` URLs for archived playback.
- [x] **Phase 4.4: M3U Fallback Pipeline** - Implement regex-based `#EXTM3U` parsing alongside the existing Xtream JSON parser to support raw playlist links.

### Sprint 5: Dual-Model Sync & Cross-Platform (In Progress)
- [x] **Phase 5.1: Auth Wizard Simplification** - Eliminate login flash and rip out the broken QR code UI.
- [ ] **Phase 5.2: Mobile App (`androidApp`) EPG** - Adapt the TV Grid to a touch-optimized UI.

---

## Backlog & Future Explorations

> [!NOTE]
> **Sportmate Live Hub (Dedicated Sports Overlay)**
> Based on the `Sohva-TV` `EventChannelMatcher` blueprint, we will build a dedicated `SPORTS_HUB` overlay. 
> - **The Data:** Integrates a free third-party sports API (e.g., TheSportsDB) to pull real-world schedules.
> - **The Logic:** Scans the messy IPTV Room database for matching team names and times to automatically route users to live games.
> - **The UI:** Uses Jetpack Compose to dynamically assemble "Match Cards" (Gradient background + Home/Away transparent PNG logos pulled via Coil from free CDNs like ESPN). 
> - **Performance:** Employs `TvLazyVerticalGrid` and `DeviceCapabilityDetector` to disable heavy blurs/animations on low-RAM devices (e.g., Chromecast) while looking premium on Nvidia Shields.

> [!TIP]
> **Titan-Tier Power User Features**
> - **Multi-View (Sports Bar Mode):** Hardware-checked multi-ExoPlayer instances (2-9 screens) for simultaneous viewing.
> - **Auto-Framerate Matching (AFR):** ExoPlayer HDMI OS-level hooks to match stream framerate (24Hz/50Hz) and eliminate judder.
> - **Local Timeshift Buffering:** Custom `CacheDataSource.Factory` to cache live `.ts` streams to USB/internal disk for instant pause/rewind of Live TV.
> - **Trakt.tv VOD Sync:** Cloud syncing watch-progress for movies and series across devices.
> - **D-Pad Macro Keymapping:** Expand `MainActivity.kt` to allow users to bind custom actions (e.g., Double Tap Left = EPG).

> [!NOTE]
> **Radical 3D UI / Game Engine Integration**
> Once the core KMP engine and baseline Jetpack Compose UI are rock-solid, explore ripping off the Compose layer and plugging the shared Kotlin engine into a game engine (**Unity** or **Godot**) via native bridging.

---

### Sprint 6: "Frankenstein" UI Transplant (Sohva-TV Parity) [COMPLETED]
- **The Goal:** Wholesale copy the highly-optimized Jetpack Compose UI architectures from the `Sohva-TV` open source repository to achieve immediate 10-foot UI performance and aesthetic parity.
- **The Rationale:** Bypasses manual trial-and-error prompting for layout and performance tuning (e.g. `TvLazyVerticalGrid` stuttering). Sohva-TV has solved Android TV focus management, typography spacing, and hardware performance.
- **The Method (Gut & Wire):**
  1. Extract `TvUiComponents.kt` and `StreamMateTheme.kt` to act as the aesthetic foundation.
  2. Gut the current `tvApp/src/main/java/com/tvmime/tv/ui` layer.
  3. Transplant Sohva-TV's `PlayerChrome.kt`, `GuideGrid.kt`, and `HomeScreen.kt` into the module.
  4. Wire TVMime's robust backend (`TvMainViewModel`, Room DB, `CloudSyncScreen`) to feed these new UI surfaces.
  5. Refactor the ExoPlayer implementation if Sohva-TV's approach (e.g., Media3 `PlaybackService`) provides better performance or background playback capabilities.
- **The Follow-Up:** Once complete, we will scrub remaining generic branding and surgically alter colors/icons to match TVMime's ultimate vision.

---

## 🔥 ABORT CODE: The V2 Teardown & V3 Master Surgery Pivot 🔥
> **Date of Pivot:** September 2026
> **The Catalyst:** Sprint 6 (The Sohva-TV Transplant) exposed irreconcilable differences between the TVMime foundation and the imported Sohva UI. The resulting "Frankenstein" architecture suffered from cascading Kotlin compilation failures, Coil 2 vs Coil 3 incompatibilities, and an unstable Room Database dependency graph.
> **The Decision:** We have halted all active feature development (including the Sports Hub and Touch EPG) to execute a true "Measure a Billion Times, Cut Once" rebuild. We are stripping TVMime down to its absolute bare metal and engineering a ground-up architecture based on a deep-dive analysis of the best open-source IPTV giants (`OwnTV`, `StreamVault`, `IPTVMine-Pro`).

### Sprint 7: TVMime V3 (The Foundation Re-Architecture)
- **Step 1: The Autonomous Research Fleet:** We will fan out autonomous AI agents to literally read the execution paths, database ingestion chunks, and `LoadControl` buffers of `OwnTV` (for their dual ExoPlayer/libmpv engine) and `StreamVault` (for their massive M3U Room DB threading).
- **Step 2: The Evasion Proxy & Hilt DI:** We will abandon the monolithic `TvMainViewModel`. We will implement Dagger Hilt for modularity, and we will build a local proxy (`OkHttp` Interceptor) that strictly spoofs headers to defeat HTTP 403/456 blocks from aggressive IPTV providers.
- **Step 3: The Native Custom UI:** No more hijacked `R.drawable` resources. We will build a bespoke, featherweight Jetpack Compose interface adhering exclusively to the Deep Black & Crimson Red design language.

### Sprint 8: Execution of V3 Phase 1 (The Iron Core)
- **Step 1:** Eradicate the old `tvApp` Compose UI layer. Delete `HomeScreen.kt`, `LiveTvScreen.kt`, and `PlayerChrome.kt` to clear the blast radius.
- **Step 2:** Inject Dagger Hilt into `tvApp/build.gradle.kts`. Build the Application class (`TvMimeApp`) with `@HiltAndroidApp` and configure the root `MainActivity` with `@AndroidEntryPoint`.
- **Step 3:** Implement the Network Evasion Interceptor. Build the singleton `OkHttpClient` injecting the Chrome User-Agent and `Origin` retry logic defined in the V3 Blueprint.

### Sprint 9: Execution of V3 Phase 2 (StreamVault Mass Ingestion Engine)
- **Step 1:** Define the Room Entity `ImportStageEntity` and the `CatalogSyncDao`. This DAO will contain the `@Insert` for 1000-item batch chunks and the raw SQLite `INSERT INTO ... SELECT ... WHERE NOT EXISTS` differential queries.
- **Step 2:** Build the `BoundedInputStream` and `M3uParser` classes. These will parse the 50MB M3U network streams linearly without buffering the entire string array into RAM.
- **Step 3:** Build the `SyncManager` repository (provided via Hilt) that orchestrates the streaming parser, batches entities into arrays of 1000, flushes them to the `CatalogSyncDao`, and then executes the final reconciliation transaction.

### Sprint 10: Execution of V3 Phase 3 (OwnTV Dual-Engine Playback)
- **Step 1:** Inject the `dev.jdtech.mpv:libmpv` library into Gradle.
- **Step 2:** Define the abstract `TvMimeVideoEngine` interface so the UI layer is completely blind to which engine is actually rendering the video.
- **Step 3:** Implement the `LivePreviewEngine` (ExoPlayer via Media3) configured with a 500ms `LoadControl` for instant zapping.
- **Step 4:** Implement the `MainPlayer` (libmpv) to handle heavy VOD files and full-screen streams.
- **Step 5:** Build the `EngineController` singleton (provided via Dagger Hilt). This controller will receive `play()` commands from the UI and internally manage the `0x80001000` hardware codec claim failure by injecting a 508ms suspension delay when hot-swapping between ExoPlayer and libmpv.

### Sprint 11: Execution of V3 Phase 4 (Jetpack Compose TV Foundation)
- **Step 1:** Create `TvMimeTheme.kt` to enforce the strict "Deep Black & Crimson Red" canonical design palette natively in Compose, eliminating reliance on generic `R.color` or legacy Android XML themes.
- **Step 2:** Build the `TvNavigation.kt` graph using `androidx.navigation.compose`.
- **Step 3:** Implement a lightweight, natively compiled `LiveTvScreen` utilizing `androidx.tv.material3` (Compose for TV), replacing the heavy "Sohva-TV" transplants.
- **Step 4:** Wire the UI layer directly into `MainActivity.kt`, consuming the Hilt-injected backends.

### Sprint 12: Execution of V3 Phase 5 (The Jetpack Compose UI Wiring)
- **Step 1:** Build the `TvMainViewModel` (injected via `@HiltViewModel`). This acts as the sole bridge between the Compose UI and our backend engines, exposing the database as a reactive `StateFlow` and handling D-Pad intent routing.
- **Step 2:** Construct the `LiveTvScreen` layout using `androidx.tv.material3`. It will feature a left-side Category Rail (TiviMate style) and a center Channel List.
- **Step 3:** Mount the `TvMimeVideoEngine` surface behind the UI using an `AndroidView` (since `libmpv` and `ExoPlayer` require raw Android `SurfaceView`s to render hardware-accelerated video).

### Sprint 13: Execution of V3 Phase 6 (Onboarding & Auth Wiring)
- **Step 1:** Fix the broken QR Code rendering in `OnboardingScreen.kt` and point the payload to `https://tvmime.vercel.app/link?code=`.
- **Step 2:** Refactor the Onboarding UI to use pure Compose TV `androidx.tv.material3` components, removing legacy XML or mobile modifiers.
- **Step 3:** Wire the `TvMimeNavHost` to boot into the `OnboardingScreen` first.
- **Step 4:** Connect the Firebase Email/Password and QR token listeners to the V3 backend. Upon successful credential retrieval, pass the portal URL to the `SyncManagerM3uImporter` and navigate to `LiveTvScreen`.

### Sprint 14: Execution of V3 Phase 7 (Firebase Cloud Sync Listener)
- **Step 1:** Ensure Firebase Firestore dependencies are present in the Gradle configuration.
- **Step 2:** Implement the `FirebaseSyncManager` (or integrate into `TvMainViewModel`). This service will listen to a specific Firestore document (`/tv_sessions/{sessionCode}`) for incoming IPTV credentials.
- **Step 3:** Wire the `OnboardingScreen` to trigger this listener upon generating the QR code. When credentials arrive, the app will execute the `SyncManagerM3uImporter` and securely delete the session document.

### Sprint 15: Execution of V3 Phase 8 (Restore Email/Password Auth Flow)
- **Step 1:** Implement `signInWithEmail` in `TvMainViewModel`. It will call the KMP `FirebaseSyncClient`, retrieve the session token, fetch the user's saved `PortalConfig` list, and trigger the Mass Ingestion Engine for the active portal.
- **Step 2:** Restore the `OutlinedTextField` inputs for Email and Password in `OnboardingScreen.kt`.
- **Step 3:** Wire the Login button to execute the ViewModel authentication and handle UI loading/error states.

### Phase Summary & Forward Strategy (Session End)
- **Completed:** V3 Backend Foundation (Network Evasion Proxy, Mass SQLite Ingestion Engine, Dual-Engine Playback, Ktor Firebase REST Client).
- **Decision Reached:** We are executing a full UI transplant of **StreamVault-IPTV** using the Adapter pattern.
- **Next Phase:** We will aggressively focus on extracting and wiring only the StreamVault **Live TV Ecosystem** (Grid, EPG, Player Controls) to our V3 backend, heavily stubbing the rest of the application until the golden path is functional.

### V3 Master Surgery: Phase 2 Execution (Completed)
- **Sprint 16**: Performed massive code extraction. Imported StreamVault's `ui`, `domain`, and `player` completely into `tvApp` to fix 113+ unresolved references at compile time.
- **Sprint 17**: Handled the Dagger Hilt catastrophe. Because we didn't import the implementations of the 19 domain repositories, Dagger Hilt would crash. Implemented `MockDomainModule.kt` utilizing `java.lang.reflect.Proxy` to create dynamic instances of all interfaces returning empty Flows. Later patched `CategoryRepository` and `ChannelRepository` proxies to directly map to TVMime's `AppDatabase` via Room DAOs.
- **Sprint 18**: Built a dynamic `MemoryPrefs` map backing the `PreferencesRepository` proxy to ensure the StreamVault Settings Screen toggles actually read and persist locally in-memory during the session. Wired `TvNavigation.kt` to boot into StreamVault's `AppNavigation` post-login using a custom `StreamVaultNavViewModel`.

### Remaining Work
- Bridge `Media3PlayerEngine` to `TvMimeVideoEngine` for true playback.
- **Sprint 19**: Replaced the dummy `PlayerEngine` proxy with a functional interceptor. When StreamVault UI calls `prepare(StreamInfo)`, the URL is extracted and handed off to TVMime's `EngineController.startLivePreview()`. When it calls `renewStreamUrl()`, it triggers `EngineController.handoffToMainPlayer()`. Upgraded the `EpgRepository` proxy to map `EpgProgramEntity` from TVMime's SQLite to StreamVault's `Program` models, completing the Live TV MVP.
