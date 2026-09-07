# Architecture: RedSurf

## Core Strategy
RedSurf is an isomorphic application. It is primarily built with **Next.js 15 (React 19)** for the web, but is designed with **Capacitor** integration in mind to wrap the application into an Android APK. This allows us to share 100% of the UI code while still having native Android capabilities (like raw socket networking if needed, though HTTP streaming works fine in modern WebViews).

## The Data Layer (Firebase)
We use Firebase for backend-as-a-service (BaaS):
- **Firestore**: Stores user configuration, remote M3U/EPG URLs, customized groups, favorites, and watch history.
- **Firebase Auth**: Identifies users across devices (Web, Android TV, Mobile) for syncing.

## The Streaming Layer
- **HLS.js**: Used directly via a React ref to power the background `<video>` element. It is configured to prioritize low latency.
- **Proxy Strategy**: Since many IPTV servers do not send `Access-Control-Allow-Origin: *` headers, `fetch()` calls to load `.m3u` manifests fail on client-side browsers. The `/api/proxy` Next.js server route securely proxies these requests.

## The Presentation Layer
- **Spatial Navigation**: Since TV remotes do not have a mouse, we rely on a custom React hook `useTVNavigation` that listens to `ArrowUp`, `ArrowDown`, `ArrowLeft`, `ArrowRight`, `Enter`, and `Escape`.
- **CSS Hierarchy**: The layout is split into columns (`w-24`, `w-84`, `w-[30rem]`) with the player occupying `absolute inset-0 z-0` so it sits behind the blurred overlay interface.

## Build and Deployment
1. **Web Output**: Cloud Run / Vercel deployment hosts the Next.js API routes and server-side components.
2. **Android Output**: Capacitor generates the Android Project, taking the static `out` directory and wrapping it. Alternatively, to support API routes in the APK, the Android webview can point to the hosted Cloud Run URL directly.
