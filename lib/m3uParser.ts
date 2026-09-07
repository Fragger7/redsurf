export interface ParsedChannel {
  id: string;
  name: string;
  logo: string;
  url: string;
  group: string;
}

export interface ParsedPlaylist {
  groups: { id: string; name: string }[];
  channels: ParsedChannel[];
}

export function parseM3U(content: string): ParsedPlaylist {
  const lines = content.split(/\r?\n/);
  const channels: ParsedChannel[] = [];
  const groupSet = new Set<string>();
  
  let currentChannel: Partial<ParsedChannel> = {};

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    if (trimmed.startsWith('#EXTINF:')) {
      // Parse tvg-logo
      const logoMatch = trimmed.match(/tvg-logo="([^"]+)"/);
      // Parse group-title
      const groupMatch = trimmed.match(/group-title="([^"]+)"/);
      // Parse name (usually comes after the last comma)
      const nameMatch = trimmed.match(/,(.+)$/);

      const groupName = groupMatch ? groupMatch[1].trim() : 'Uncategorized';
      
      currentChannel = {
        id: crypto.randomUUID(),
        logo: logoMatch ? logoMatch[1].trim() : '',
        group: groupName,
        name: nameMatch ? nameMatch[1].trim() : 'Unknown Channel',
      };
      
      groupSet.add(groupName);
    } else if (!trimmed.startsWith('#')) {
      // It's likely a URL if it doesn't start with #
      if (currentChannel.name) {
        currentChannel.url = trimmed;
        channels.push(currentChannel as ParsedChannel);
        currentChannel = {}; // reset for next
      }
    }
  }

  // Generate groups array
  const groups = Array.from(groupSet).map(g => ({
    id: g,
    name: g
  }));

  // Add an "All Channels" group at the beginning if there are channels
  if (groups.length > 0) {
    groups.unshift({ id: 'all', name: 'All Channels' });
  }

  return { groups, channels };
}
