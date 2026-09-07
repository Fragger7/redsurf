import { XMLParser } from 'fast-xml-parser';

export interface EPGProgram {
  id: string;
  channelId: string;
  start: Date;
  stop: Date;
  title: string;
  desc: string;
}

export interface EPGChannel {
  id: string;
  displayNames: string[];
  icon?: string;
}

export interface EPGRoot {
  channels: Record<string, EPGChannel>;
  programs: EPGProgram[];
}

export function parseXMLTV(xmlString: string): EPGRoot {
  const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: "@_"
  });
  
  const parsed = parser.parse(xmlString);
  const tv = parsed.tv;
  
  if (!tv) {
    return { channels: {}, programs: [] };
  }

  const channels: Record<string, EPGChannel> = {};
  const programs: EPGProgram[] = [];

  // Parse Channels
  if (tv.channel) {
    const channelArr = Array.isArray(tv.channel) ? tv.channel : [tv.channel];
    channelArr.forEach((c: any) => {
      const id = c["@_id"];
      if (!id) return;
      
      const displayNames = [];
      if (c["display-name"]) {
        const dNames = Array.isArray(c["display-name"]) ? c["display-name"] : [c["display-name"]];
        displayNames.push(...dNames.map((d: any) => typeof d === 'object' ? d['#text'] : d));
      }

      channels[id] = {
        id,
        displayNames,
        icon: c.icon ? c.icon["@_src"] : undefined
      };
    });
  }

  // Parse Programs
  if (tv.programme) {
    const programArr = Array.isArray(tv.programme) ? tv.programme : [tv.programme];
    programArr.forEach((p: any) => {
      const channelId = p["@_channel"];
      const startStr = p["@_start"];
      const stopStr = p["@_stop"];
      
      if (!channelId || !startStr || !stopStr) return;

      // XMLTV date format: 20231015080000 +0000 (YYYYMMDDHHmmss Z)
      const parseDate = (str: string) => {
        const year = parseInt(str.substring(0, 4));
        const month = parseInt(str.substring(4, 6)) - 1;
        const day = parseInt(str.substring(6, 8));
        const hour = parseInt(str.substring(8, 10));
        const min = parseInt(str.substring(10, 12));
        const sec = parseInt(str.substring(12, 14));
        
        let offsetMs = 0;
        if (str.length >= 20) {
           const sign = str.substring(15, 16) === '+' ? -1 : 1;
           const offsetHours = parseInt(str.substring(16, 18));
           const offsetMins = parseInt(str.substring(18, 20));
           offsetMs = sign * ((offsetHours * 60) + offsetMins) * 60 * 1000;
        }
        
        const d = new Date(Date.UTC(year, month, day, hour, min, sec));
        return new Date(d.getTime() + offsetMs);
      };

      const title = p.title ? (typeof p.title === 'object' ? p.title['#text'] : p.title) : 'Unknown Program';
      const desc = p.desc ? (typeof p.desc === 'object' ? p.desc['#text'] : p.desc) : '';

      programs.push({
        id: `${channelId}-${startStr}`,
        channelId,
        start: parseDate(startStr),
        stop: parseDate(stopStr),
        title,
        desc
      });
    });
  }

  return { channels, programs };
}
