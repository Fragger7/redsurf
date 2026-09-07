import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.redsurf.app',
  appName: 'RedSurf',
  webDir: 'out', // Next.js static export directory
  server: {
    androidScheme: 'https',
    cleartext: true, // Allow fetching from non-https M3U sources if needed
  }
};

export default config;
