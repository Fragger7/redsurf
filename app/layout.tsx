import type {Metadata} from 'next';
import { Outfit, Manrope } from 'next/font/google';
import './globals.css'; // Global styles

const outfit = Outfit({ subsets: ['latin'], variable: '--font-outfit' });
const manrope = Manrope({ subsets: ['latin'], variable: '--font-manrope' });

export const metadata: Metadata = {
  title: 'RedSurf',
  description: 'A modern, cross-platform IPTV player optimized for Android TV and mobile.',
  openGraph: {
    title: 'RedSurf',
    description: 'A modern, cross-platform IPTV player optimized for Android TV and mobile.',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'RedSurf',
    description: 'A modern, cross-platform IPTV player optimized for Android TV and mobile.',
  },
};

export default function RootLayout({children}: {children: React.ReactNode}) {
  return (
    <html lang="en">
      <body className={`${outfit.variable} ${manrope.variable} font-sans`} suppressHydrationWarning>{children}</body>
    </html>
  );
}
