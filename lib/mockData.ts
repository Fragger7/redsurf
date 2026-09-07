export const mockGroups = [
  { id: 'all', name: 'All Channels' },
  { id: 'news', name: 'News' },
  { id: 'entertainment', name: 'Entertainment' },
  { id: 'sports', name: 'Sports' },
];

export const mockChannels = [
  {
    id: 'ch1',
    group: 'all',
    name: 'Big Buck Bunny (Test 1)',
    logo: 'https://picsum.photos/seed/bbb/100/100',
    url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
  },
  {
    id: 'ch2',
    group: 'entertainment',
    name: 'Sintel (Test 2)',
    logo: 'https://picsum.photos/seed/sintel/100/100',
    url: 'https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8',
  },
  {
    id: 'ch3',
    group: 'all',
    name: 'Apple BipBop (Test 3)',
    logo: 'https://picsum.photos/seed/apple/100/100',
    url: 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8',
  },
  {
    id: 'ch4',
    group: 'news',
    name: 'Caminandes (Test 4)',
    logo: 'https://picsum.photos/seed/cam/100/100',
    url: 'https://amssamples.streaming.mediaservices.windows.net/91492735-c523-432b-ba01-faba6c2206a2/AzureMediaServicesPromo.ism/manifest(format=m3u8-aapl)',
  }
];
