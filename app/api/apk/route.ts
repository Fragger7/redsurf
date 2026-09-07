import { NextResponse } from 'next/server';

export async function GET() {
  try {
    // Fetch the latest release from our GitHub repository
    const response = await fetch('https://api.github.com/repos/Fragger7/redsurf/releases/latest', {
      headers: {
        'Accept': 'application/vnd.github.v3+json',
        // 'Authorization': `token ${process.env.GITHUB_TOKEN}` // Optional, prevents rate limits if set
      },
      next: { revalidate: 60 } // Cache for 60 seconds
    });

    if (!response.ok) {
      throw new Error(`GitHub API returned ${response.status}`);
    }

    const release = await response.json();
    
    // Find the APK asset attached to the release
    const apkAsset = release.assets?.find((asset: any) => asset.name.endsWith('.apk'));

    if (apkAsset && apkAsset.browser_download_url) {
      // Redirect the user directly to the APK download URL
      return NextResponse.redirect(apkAsset.browser_download_url);
    } else {
      // Fallback if no APK is attached yet, redirect to the releases page
      return NextResponse.redirect(release.html_url);
    }
  } catch (error) {
    console.error("Failed to fetch latest APK:", error);
    // Fallback to the main repo releases page
    return NextResponse.redirect('https://github.com/Fragger7/redsurf/releases/latest');
  }
}
