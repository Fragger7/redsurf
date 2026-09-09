import { NextResponse } from 'next/server';

export async function GET() {
  try {
    const res = await fetch('https://api.github.com/repos/Fragger7/redsurf/releases/latest', {
      headers: {
        'Accept': 'application/vnd.github.v3+json',
        // Optional: 'Authorization': `Bearer ${process.env.GITHUB_TOKEN}` // if we get rate limited
      },
      next: { revalidate: 60 } // Cache for 60 seconds
    });

    if (!res.ok) {
      console.error('Failed to fetch latest release from GitHub:', await res.text());
      return new NextResponse('Failed to fetch latest release', { status: 500 });
    }

    const data = await res.json();
    
    // Find the APK asset
    const apkAsset = data.assets?.find((asset: any) => asset.name.endsWith('.apk'));
    
    if (apkAsset && apkAsset.browser_download_url) {
      return NextResponse.redirect(apkAsset.browser_download_url);
    }

    return new NextResponse('APK asset not found in the latest release', { status: 404 });
  } catch (error) {
    console.error('Error redirecting to APK:', error);
    return new NextResponse('Internal Server Error', { status: 500 });
  }
}
