import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
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
