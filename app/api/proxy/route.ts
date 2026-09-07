import { NextRequest, NextResponse } from 'next/server';

export async function GET(req: NextRequest) {
  const url = req.nextUrl.searchParams.get('url');

  if (!url) {
    return NextResponse.json({ error: 'Missing url parameter' }, { status: 400 });
  }

  try {
    const response = await fetch(url, {
      headers: {
        'User-Agent': 'RedSurf-IPTV-Player/1.0',
      },
    });

    if (!response.ok) {
      return NextResponse.json(
        { error: `Failed to fetch from remote URL. Status: ${response.status}` },
        { status: response.status }
      );
    }

    const text = await response.text();
    
    return new NextResponse(text, {
      status: 200,
      headers: {
        'Content-Type': 'application/vnd.apple.mpegurl',
        'Access-Control-Allow-Origin': '*',
      },
    });
  } catch (error: any) {
    console.error('Proxy fetch error:', error);
    return NextResponse.json({ error: 'Failed to fetch the URL' }, { status: 500 });
  }
}
