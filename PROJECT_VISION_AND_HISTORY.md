# RedSurf: Product Vision, Architecture, and Development History

## 1. Original Vision & Goals
As established by the Product Manager, the core objective of this project was to build **RedSurf**—the world's most premium, Android TV-exclusive IPTV player designed to compete directly with, and ultimately surpass, TiViMate. 

**Core Product Management Directives:**
*   **The "TiViMate Killer":** Deliver a world-class, premium IPTV experience natively on Android TV (Fire OS, Google TV).
*   **Cost-Effective Infrastructure:** Utilize free, robust infrastructure whenever possible (e.g., GitHub Actions for CI/CD, Vercel for web hosting).
*   **No Skeletons, No Mocking:** All features must be production-ready. Real parsers, real database queries, real video players. No fake data or placeholder UI.
*   **D-Pad First:** The entire TV interface must be engineered from the ground up for remote control (D-Pad) navigation using Jetpack Compose for TV.

## 2. Architectural Decisions & Tech Stack

To achieve the vision, we established a Monorepo architecture containing two distinct applications:

### A. The Native TV Client (`/tv-native`)
*   **Framework:** Pure Kotlin and Jetpack Compose for TV.
*   **Video Engine:** Media3 ExoPlayer for robust stream playback, track mapping, and format support.
*   **Local Persistence:** Room Database for caching channels, groups, and EPG data locally to ensure lightning-fast load times.
*   **Background Tasks:** WorkManager for silent, background playlist and EPG syncing.
*   **The Pivot to Local Pairing (NanoHttpd):** Initially, we explored using Firebase for cloud-based TV pairing. However, to enhance user privacy, reduce cloud dependency, and ensure the app works fully offline, we pivoted to a **Local LAN Pairing model**. We embedded a lightweight web server (NanoHttpd) directly into the Android TV app running on port 8080. Users can now scan a QR code on their TV and push credentials (M3U, Xtream) directly from their phone to the TV over their local network.

### B. The Web Portal (`/`)
*   **Framework:** Next.js (App Router) styled with Tailwind CSS.
*   **Purpose:** Originally intended as a cloud pairing dashboard, it was repositioned as a promotional landing page and APK distribution hub after the pivot to local TV pairing.
*   **Design:** Premium, dark-themed, and focused on highlighting features like Auto Frame Rate, Global Search, and DoH DNS.

## 3. Infrastructure & Systems Used

*   **GitHub & GitHub Actions:** 
    *   *Purpose:* Source code hosting and automated CI/CD.
    *   *Implementation:* We built a `semantic-release` pipeline that automatically compiles the native Android TV APK, versions it, and creates GitHub Releases whenever code is pushed to the `main` branch.
    *   *Credentials used:* `GIT_PAT` (GitHub Personal Access Token) injected as a secret to bypass permission limits and trigger workflows.
*   **Vercel:**
    *   *Purpose:* Zero-config, free-tier hosting for the Next.js web portal.
    *   *Implementation:* Automatically pulls from the GitHub `main` branch.
*   **Firebase:**
    *   *Purpose:* Initially provisioned for Firestore database and Authentication (`ai-studio-streammateiptv-...` and the experimental `fourth-surge-1txfk`).
    *   *Decision:* We explicitly moved away from relying on Firebase for core TV functionality. It remains in the web portal architecture for potential future "Cloud Backup" features, but the core product is proudly local-first.

## 4. The Development Journey

*   **Phase 1: Foundation & Schemas:** We started by defining the Room database schemas (`ChannelEntity`, `PlaylistEntity`, `EpgProgramEntity`) to handle complex multi-playlist environments, ensuring live TV, VOD, and Series could coexist without overlapping IDs.
*   **Phase 2: The Core Parsers:** Implemented M3U, Xtream Codes, and Stalker portal parsers. A major hurdle was ensuring robust URL sanitization (automatically injecting `http://` or `https://` if users forgot them), which caused silent connection failures early on.
*   **Phase 3: The CI/CD Pipeline:** We spent significant time battling Gradle caching issues and Kotlin compiler errors within the GitHub Actions environment. We systematically debugged these runners, isolated the Kotlin KSP and Compose syntax errors, and stabilized the build pipeline.
*   **Phase 4: Web Portal & Distribution:** Developed the Next.js landing page. To ensure users always get the latest version, we created a custom Next.js API route (`/api/download`) that automatically fetches the latest compiled APK from GitHub Releases and redirects the user, ensuring seamless distribution without manual updates.
*   **Phase 5: UI Polish:** Addressed missing branding by generating the RedSurf TV banners (`tv_banner.xml`) and icons. Fixed the TV Onboarding screen layout to clearly present the Local Network (QR), Xtream, M3U, and Cloud setup options to the user.

## 5. Current State & Completed Features

Today, RedSurf is a functional, compiling Android TV application with the following completed features:
1.  **Monorepo CI/CD:** Fully automated APK compilation and semantic versioning via GitHub Actions.
2.  **Next.js Landing Page:** Live promotional site with dynamic APK download routing.
3.  **Local Network Pairing:** NanoHttpd server implementation allowing credential injection from a mobile phone browser directly to the TV.
4.  **Multi-Format Support:** Support for M3U playlists, Xtream Codes API, and Stalker portals.
5.  **Room Database Integration:** Fast local caching of channels with proper separation of Live, VOD, and Series content.
6.  **Video Player:** ExoPlayer integration tailored for Android TV.
7.  **TV Onboarding Flow:** A polished, D-pad navigable welcome screen guiding users through credential entry.

## 6. The Road Ahead (Backlog)

As discussed, the future roadmap includes:
*   **Global Matrix Search:** Unified querying across Live Channels, VODs, and Series.
*   **Auto Frame Rate (AFR):** Dynamically switching the TV's hardware refresh rate to match the stream's FPS to eliminate judder.
*   **Custom DNS (DoH):** Bypassing ISP blocking natively.
*   **OTA Updater:** An in-app prompt on the TV that polls GitHub Releases and downloads/installs new APKs automatically.
