'use client';

import { useState, useEffect, useRef, useMemo } from 'react';
import { useTVNavigation } from '@/hooks/useTVNavigation';
import { VideoPlayer } from './VideoPlayer';
import { mockChannels, mockGroups } from '@/lib/mockData';
import { parseM3U, ParsedChannel } from '@/lib/m3uParser';
import { motion, AnimatePresence } from 'motion/react';
import { Settings, PlaySquare, ListVideo, Search, Clock, Plus, Tv, Waves, Smartphone } from 'lucide-react';
import { cn } from '@/lib/utils';
import Image from 'next/image';
import { auth, db } from '@/lib/firebase';
import { signInAnonymously, onAuthStateChanged, User } from 'firebase/auth';
import { collection, doc, setDoc, getDocs, onSnapshot } from 'firebase/firestore';
import QRCode from 'react-qr-code';

type FocusArea = 'sidebar' | 'groups' | 'channels' | 'player' | 'settings';

export function TVInterface() {
  const [uiVisible, setUiVisible] = useState(true);
  const [focusArea, setFocusArea] = useState<FocusArea>('channels');
  
  // Data State
  const [groups, setGroups] = useState(mockGroups);
  const [channels, setChannels] = useState(mockChannels);
  
  const [selectedGroupIdx, setSelectedGroupIdx] = useState(0);
  const [selectedChannelIdx, setSelectedChannelIdx] = useState(0);
  
  const [playingChannelId, setPlayingChannelId] = useState<string | null>(mockChannels[0].id);

  // Settings State
  const [m3uUrl, setM3uUrl] = useState('');
  const [isLoadingM3u, setIsLoadingM3u] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  
  // Pairing State
  const [pairingCode, setPairingCode] = useState<string | null>(null);

  // Auth & Initialization
  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (u) => {
      if (u) {
        setUser(u);
        // Load saved playlists
        try {
          const snapshot = await getDocs(collection(db, `users/${u.uid}/playlists`));
          if (!snapshot.empty) {
            const savedPlaylist = snapshot.docs[0].data();
            setM3uUrl(savedPlaylist.url);
            loadPlaylist(savedPlaylist.url);
          }
        } catch (e) {
          console.error("Error loading playlists:", e);
        }
      } else {
        signInAnonymously(auth).catch(console.error);
      }
    });
    return unsub;
  }, []);

  // Pairing Listener
  useEffect(() => {
    if (!pairingCode) return;
    
    const unsub = onSnapshot(doc(db, 'pairingSessions', pairingCode), async (snapshot) => {
      if (snapshot.exists()) {
        const data = snapshot.data();
        if (data.status === 'paired' && data.url) {
          console.log("Received paired URL from mobile!", data.url);
          setM3uUrl(data.url);
          
          if (user) {
            const playlistId = crypto.randomUUID();
            try {
              await setDoc(doc(db, `users/${user.uid}/playlists`, playlistId), {
                url: data.url,
                addedAt: Date.now()
              });
            } catch (e) {
              console.error("Failed to save playlist to cloud", e);
            }
          }
          
          await loadPlaylist(data.url);
          setPairingCode(null); // Close pairing mode
        }
      }
    });

    return unsub;
  }, [pairingCode, user]);

  const generatePairingCode = async () => {
    const code = Math.random().toString(36).substring(2, 8).toUpperCase();
    try {
      await setDoc(doc(db, 'pairingSessions', code), {
        status: 'waiting',
        createdAt: Date.now()
      });
      setPairingCode(code);
    } catch (e) {
      console.error("Failed to create pairing session", e);
    }
  };

  const loadPlaylist = async (url: string) => {
    setIsLoadingM3u(true);
    try {
      const res = await fetch(`/api/proxy?url=${encodeURIComponent(url)}`);
      const text = await res.text();
      const parsed = parseM3U(text);
      
      if (parsed.channels.length > 0) {
        setGroups(parsed.groups);
        setChannels(parsed.channels as any);
        setSelectedGroupIdx(0);
        setSelectedChannelIdx(0);
        setPlayingChannelId(parsed.channels[0].id);
        setFocusArea('groups');
      } else {
        alert("No channels found in the playlist.");
      }
    } catch (err) {
      console.error("Failed to fetch playlist", err);
      alert("Failed to load playlist. Check the URL and CORS policy.");
    } finally {
      setIsLoadingM3u(false);
    }
  };

  // Derive active items
  const activeGroup = groups[selectedGroupIdx] || { id: 'all', name: 'All' };
  const channelsInGroup = useMemo(() => {
    if (!groups.length || !channels.length) return [];
    return activeGroup.id === 'all' 
      ? channels 
      : channels.filter(c => c.group === activeGroup.id)
  }, [activeGroup, channels, groups]);
  
  const playingChannel = useMemo(() => 
    channels.find(c => c.id === playingChannelId) || channels[0] || { url: '' }
  , [playingChannelId, channels]);

  // Handle inactivity fade out
  const inactivityTimer = useRef<NodeJS.Timeout>(null);
  
  const resetInactivityTimer = () => {
    setUiVisible(true);
    if (inactivityTimer.current) clearTimeout(inactivityTimer.current);
    
    // Only fade out if player is focused
    if (focusArea === 'player') {
      inactivityTimer.current = setTimeout(() => {
        setUiVisible(false);
      }, 4000);
    }
  };

  useEffect(() => {
    resetInactivityTimer();
    return () => {
      if (inactivityTimer.current) clearTimeout(inactivityTimer.current);
    }
  }, [focusArea]);

  useTVNavigation({
    onAny: resetInactivityTimer,
    onUp: () => {
      if (focusArea === 'channels') {
        setSelectedChannelIdx(prev => Math.max(0, prev - 1));
      } else if (focusArea === 'groups') {
        setSelectedGroupIdx(prev => Math.max(0, prev - 1));
        setSelectedChannelIdx(0); // Reset channel idx when changing group
      } else if (focusArea === 'sidebar') {
        // Implement sidebar up logic if needed
      }
    },
    onDown: () => {
      if (focusArea === 'channels') {
        setSelectedChannelIdx(prev => Math.min(channelsInGroup.length - 1, prev + 1));
      } else if (focusArea === 'groups') {
        setSelectedGroupIdx(prev => Math.min(groups.length - 1, prev + 1));
        setSelectedChannelIdx(0);
      } else if (focusArea === 'sidebar') {
        // Implement sidebar down logic if needed
      }
    },
    onLeft: () => {
      if (focusArea === 'channels') setFocusArea('groups');
      else if (focusArea === 'groups') setFocusArea('sidebar');
    },
    onRight: () => {
      if (focusArea === 'sidebar') setFocusArea('groups');
      else if (focusArea === 'groups') setFocusArea('channels');
    },
    onEnter: () => {
      if (!uiVisible) {
        setUiVisible(true);
        setFocusArea('channels');
        return;
      }
      
      if (focusArea === 'channels') {
        const selected = channelsInGroup[selectedChannelIdx];
        if (selected) {
          setPlayingChannelId(selected.id);
          setFocusArea('player'); // Hide UI and just play
        }
      }
    },
    onBack: () => {
      if (focusArea === 'settings') {
        setFocusArea('sidebar');
      } else if (focusArea === 'player') {
        setFocusArea('channels');
        setUiVisible(true);
      } else {
        setFocusArea('player');
      }
    }
  });

  const handleAddPlaylist = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!m3uUrl) return;
    
    if (user) {
      const playlistId = crypto.randomUUID();
      try {
        await setDoc(doc(db, `users/${user.uid}/playlists`, playlistId), {
          url: m3uUrl,
          addedAt: Date.now()
        });
      } catch (e) {
        console.error("Failed to save playlist to cloud", e);
      }
    }
    
    await loadPlaylist(m3uUrl);
  };

  return (
    <div className="relative w-full h-full text-white font-sans overflow-hidden bg-zinc-950">
      {/* Background Player */}
      {playingChannel?.url && <VideoPlayer url={playingChannel.url} isPlaying={true} />}

      {/* UI Overlay */}
      <AnimatePresence>
        {(uiVisible && focusArea !== 'player') && (
          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="absolute inset-0 bg-zinc-950/75 flex z-10"
          >
            {/* Left Sidebar */}
            <div className={cn(
              "w-24 flex flex-col items-center py-8 gap-8 border-r border-white/5 transition-colors",
              focusArea === 'sidebar' ? "bg-black/90 shadow-[4px_0_24px_rgba(225,29,72,0.15)]" : "bg-black/40"
            )}>
               <div className="mb-4 text-rose-600 flex flex-col items-center justify-center">
                 <Waves className="w-10 h-10" strokeWidth={2.5} />
               </div>
               <SidebarIcon icon={Search} label="Search" active={focusArea === 'sidebar'} />
               <SidebarIcon icon={Tv} label="TV" active={focusArea === 'sidebar'} selected={focusArea !== 'settings'} onClick={() => setFocusArea('groups')} />
               <SidebarIcon icon={ListVideo} label="Movies" active={focusArea === 'sidebar'} />
               <SidebarIcon icon={Clock} label="Catch-up" active={focusArea === 'sidebar'} />
               <div className="mt-auto">
                 <SidebarIcon icon={Settings} label="Settings" active={focusArea === 'sidebar'} selected={focusArea === 'settings'} onClick={() => setFocusArea('settings')} />
               </div>
            </div>

            {focusArea === 'settings' ? (
               <div className="flex-1 p-16 bg-black/60 backdrop-blur-md overflow-y-auto no-scrollbar">
                 <h2 className="text-5xl font-display font-bold mb-12 text-white">Settings</h2>
                 
                 <div className="max-w-5xl grid grid-cols-1 md:grid-cols-2 gap-8">
                   {/* Mobile Pairing Box */}
                   <div className="bg-zinc-900/80 border border-rose-500/30 rounded-2xl p-8 flex flex-col items-center text-center shadow-lg shadow-rose-900/20">
                      <div className="w-16 h-16 bg-rose-600/20 rounded-full flex items-center justify-center mb-6">
                        <Smartphone className="w-8 h-8 text-rose-500" />
                      </div>
                      <h3 className="text-2xl font-bold mb-3">Add via Mobile</h3>
                      <p className="text-zinc-400 mb-8">Scan the QR code to easily add your IPTV credentials, Xtream Codes, or M3U link from your phone.</p>
                      
                      {pairingCode ? (
                        <div className="flex flex-col items-center bg-white p-6 rounded-xl">
                           <QRCode value={`${process.env.NEXT_PUBLIC_APP_URL || 'https://redsurf.app'}/pair/${pairingCode}`} size={180} />
                           <div className="mt-6 text-black font-mono text-3xl font-bold tracking-widest">{pairingCode}</div>
                        </div>
                      ) : (
                        <button 
                          onClick={generatePairingCode}
                          className="w-full mt-auto bg-rose-600 hover:bg-rose-500 text-white font-bold py-4 px-8 rounded-xl transition-colors focus:ring-4 focus:ring-white"
                        >
                          Show QR Code
                        </button>
                      )}
                   </div>

                   {/* Direct URL Box */}
                   <div className="bg-zinc-900/80 border border-white/10 rounded-2xl p-8">
                      <h3 className="text-2xl font-semibold mb-6 flex items-center gap-3">
                        <Plus className="w-6 h-6 text-zinc-400" />
                        Direct M3U URL
                      </h3>
                      <form onSubmit={handleAddPlaylist} className="flex flex-col gap-6">
                        <div>
                          <label className="block text-sm font-medium text-zinc-400 mb-2">Playlist URL</label>
                          <input 
                            type="url" 
                            value={m3uUrl}
                            onChange={(e) => setM3uUrl(e.target.value)}
                            placeholder="https://example.com/playlist.m3u"
                            className="w-full bg-black/50 border border-zinc-700 rounded-xl px-4 py-4 text-white focus:outline-none focus:border-rose-500 transition-colors"
                          />
                        </div>
                        <button 
                          type="submit"
                          disabled={isLoadingM3u || !m3uUrl}
                          className="bg-zinc-800 hover:bg-zinc-700 text-white font-bold py-4 px-8 rounded-xl transition-colors disabled:opacity-50 disabled:cursor-not-allowed focus:ring-4 focus:ring-white"
                        >
                          {isLoadingM3u ? 'Loading Playlist...' : 'Add Playlist'}
                        </button>
                      </form>
                   </div>
                 </div>
               </div>
            ) : (
              <>
                {/* Groups Column */}
                <div className="w-84 border-r border-white/5 flex flex-col bg-black/70 shadow-2xl backdrop-blur-xl">
                  <div className="p-8 pb-6 font-display text-3xl font-bold tracking-tight text-white">RedSurf</div>
                  <div className="flex-1 overflow-y-auto py-2 no-scrollbar">
                    {groups.map((group, idx) => (
                      <div 
                        key={group.id}
                        onClick={() => {
                          setSelectedGroupIdx(idx);
                          setSelectedChannelIdx(0);
                          setFocusArea('groups');
                        }}
                        className={cn(
                          "px-8 py-5 mx-4 my-1 rounded-xl text-xl font-medium transition-all cursor-pointer",
                          selectedGroupIdx === idx && focusArea === 'groups' ? "bg-rose-600 text-white shadow-[0_4px_20px_rgba(225,29,72,0.4)] scale-[1.02]" : "",
                          selectedGroupIdx === idx && focusArea !== 'groups' ? "bg-white/10 text-white" : "text-zinc-400 hover:text-zinc-200"
                        )}
                      >
                        {group.name}
                      </div>
                    ))}
                  </div>
                </div>

                {/* Channels Column */}
                <div className="w-[30rem] flex flex-col bg-black/80 backdrop-blur-2xl">
                  <div className="p-8 pb-6 text-2xl font-bold tracking-wide text-zinc-300 truncate">{activeGroup.name}</div>
                  <div className="flex-1 overflow-y-auto py-2 no-scrollbar">
                    {channelsInGroup.map((channel, idx) => (
                      <div 
                        key={channel.id}
                        onClick={() => {
                          setSelectedChannelIdx(idx);
                          setFocusArea('channels');
                        }}
                        onDoubleClick={() => {
                          setPlayingChannelId(channel.id);
                          setFocusArea('player');
                        }}
                        className={cn(
                          "px-6 py-4 mx-4 my-2 flex items-center gap-5 rounded-xl transition-all cursor-pointer",
                          selectedChannelIdx === idx && focusArea === 'channels' ? "bg-white text-black shadow-xl scale-[1.03]" : "text-zinc-300",
                          playingChannelId === channel.id && selectedChannelIdx !== idx ? "bg-white/10 text-white" : ""
                        )}
                      >
                        <div className="w-16 h-16 bg-zinc-900 rounded-lg overflow-hidden flex items-center justify-center flex-shrink-0 relative">
                           {channel.logo ? (
                             <Image src={channel.logo} alt="" fill className="object-contain p-1" referrerPolicy="no-referrer" unoptimized />
                           ) : (
                             <Tv className="w-6 h-6 text-zinc-700" />
                           )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className={cn("font-semibold truncate text-xl", selectedChannelIdx === idx && focusArea === 'channels' ? "text-black" : "text-white")}>
                            {channel.name}
                          </div>
                          {playingChannelId === channel.id && (
                            <div className={cn(
                              "text-sm font-bold uppercase tracking-wider mt-1",
                              selectedChannelIdx === idx && focusArea === 'channels' ? "text-rose-600" : "text-rose-500"
                            )}>Playing</div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Right Side - EPG Preview */}
                <div className="flex-1 p-16 flex flex-col justify-end bg-gradient-to-r from-transparent via-black/40 to-black/90 relative">
                    <div className="absolute inset-0 bg-gradient-to-t from-rose-900/10 to-transparent pointer-events-none" />
                    {channelsInGroup[selectedChannelIdx] && (
                      <div className="max-w-3xl relative z-10">
                        <h2 className="text-6xl font-display font-bold mb-6 drop-shadow-2xl text-white tracking-tight">{channelsInGroup[selectedChannelIdx].name}</h2>
                        <div className="text-2xl text-rose-400 mb-8 font-semibold tracking-wide flex items-center gap-4">
                          <span className="w-3 h-3 rounded-full bg-rose-500 animate-pulse" />
                          10:00 AM - 12:00 PM • Current Program
                        </div>
                        <p className="text-zinc-300 text-xl leading-relaxed line-clamp-4 max-w-2xl">
                          This is a placeholder description for the current program playing on {channelsInGroup[selectedChannelIdx].name}. When EPG is integrated, this will show live program details. Experience RedSurf with seamless navigation and crystal clear streams.
                        </p>
                      </div>
                    )}
                </div>
              </>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function SidebarIcon({ icon: Icon, label, active, selected, onClick }: { icon: any, label: string, active: boolean, selected?: boolean, onClick?: () => void }) {
  return (
    <div 
      onClick={onClick}
      className={cn(
      "w-14 h-14 rounded-full flex items-center justify-center transition-all cursor-pointer",
      selected ? "text-rose-500" : "text-zinc-500",
      active && selected ? "bg-rose-600 text-white shadow-[0_0_15px_rgba(225,29,72,0.4)]" : "",
      active && !selected ? "bg-white/10 text-white" : "hover:text-zinc-300 hover:bg-white/5",
    )}>
      <Icon className="w-7 h-7" />
    </div>
  );
}
