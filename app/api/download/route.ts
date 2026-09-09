import { NextResponse } from 'next/server';

export async function GET() {
  try {
    const res = await fetch('https://api.github.com/repos/Fragger7/redsurf/releases/latest', {
      headers: {
        'User-Agent': 'RedSurf-Web'
      }
    });
    
    if (!res.ok) {
      return NextResponse.redirect(new URL('https://github.com/Fragger7/redsurf/releases/latest'));
    }
    
    const data = await res.json();
    const apkAsset = data.assets?.find((asset: any) => asset.name.endsWith('.apk'));
    
    if (apkAsset && apkAsset.browser_download_url) {
      return NextResponse.redirect(apkAsset.browser_download_url);
    }
    
    // Fallback to releases page
    return NextResponse.redirect(new URL('https://github.com/Fragger7/redsurf/releases/latest'));
  } catch (error) {
    return NextResponse.redirect(new URL('https://github.com/Fragger7/redsurf/releases/latest'));
  }
}
