import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  eslint: {
    ignoreDuringBuilds: true,
  },
  typescript: {
    ignoreBuildErrors: true,
  },
  async redirects() {
    return [
      {
        source: '/tv.apk',
        destination: '/api/apk',
        permanent: false,
      },
    ];
  },
};

export default nextConfig;
