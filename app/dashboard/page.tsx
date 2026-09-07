"use client";

import { useEffect, useState } from "react";
import { collection, onSnapshot, query, setDoc, doc, getDocs } from "firebase/firestore";
import { auth, db } from "../../lib/firebase";
import { onAuthStateChanged, signOut } from "firebase/auth";
import { useRouter } from "next/navigation";
import { Tv2, LogOut, Plus, Server, Link as LinkIcon, Activity } from "lucide-react";
import { motion } from "motion/react";

export default function FamilyDashboard() {
  const [user, setUser] = useState<any>(null);
  const [devices, setDevices] = useState<any[]>([]);
  const [playlists, setPlaylists] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  // New Playlist Form State
  const [showAdd, setShowAdd] = useState(false);
  const [formName, setFormName] = useState("");
  const [formServer, setFormServer] = useState("");
  const [formUser, setFormUser] = useState("");
  const [formPass, setFormPass] = useState("");

  // Pairing State
  const [pairingCode, setPairingCode] = useState("");
  const [selectedPlaylist, setSelectedPlaylist] = useState("");

  useEffect(() => {
    const unsubAuth = onAuthStateChanged(auth, (u) => {
      if (u) {
        setUser(u);
        fetchPlaylists(u.uid);
      } else {
        router.push("/auth");
      }
      setLoading(false);
    });

    // Real-time listener for TVs
    const q = query(collection(db, "family_devices"));
    const unsubDevices = onSnapshot(q, (snapshot) => {
      const devs = snapshot.docs.map(d => ({ id: d.id, ...d.data() }));
      devs.sort((a: any, b: any) => b.lastActive - a.lastActive);
      setDevices(devs);
    });

    return () => { unsubAuth(); unsubDevices(); };
  }, [router]);

  const fetchPlaylists = async (uid: string) => {
    const snapshot = await getDocs(collection(db, \`users/\${uid}/playlists\`));
    setPlaylists(snapshot.docs.map(d => ({ id: d.id, ...d.data() })));
  };

  const handleAddPlaylist = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    const playlistId = Date.now().toString();
    const data = { name: formName, server: formServer, username: formUser, password: formPass, type: "xtream" };
    await setDoc(doc(db, \`users/\${user.uid}/playlists\`, playlistId), data);
    setShowAdd(false);
    fetchPlaylists(user.uid);
  };

  const handlePairTV = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pairingCode.length < 5 || !selectedPlaylist) return;
    const pl = playlists.find(p => p.id === selectedPlaylist);
    if (!pl) return;

    await setDoc(doc(db, "pairingSessions", pairingCode.toUpperCase()), {
      status: "paired",
      playlistType: pl.type,
      name: pl.name,
      server: pl.server,
      username: pl.username,
      password: pl.password,
      hiddenGroups: [] // Default for now
    });
    
    setPairingCode("");
    alert("Credentials pushed to TV successfully!");
  };

  if (loading) return <div className="min-h-screen bg-neutral-950 text-white flex items-center justify-center">Loading...</div>;

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-50 p-8">
      <div className="max-w-6xl mx-auto">
        <header className="flex items-center justify-between border-b border-neutral-800 pb-8 mb-8">
          <div className="flex items-center space-x-4">
            <div className="w-12 h-12 bg-red-600 rounded-xl flex items-center justify-center">
              <Tv2 className="w-6 h-6 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">Cloud Console</h1>
              <p className="text-neutral-400">{user?.email}</p>
            </div>
          </div>
          <button onClick={() => signOut(auth)} className="px-4 py-2 bg-neutral-900 border border-neutral-800 text-neutral-300 rounded-full hover:bg-neutral-800 flex items-center transition-colors">
            <LogOut className="w-4 h-4 mr-2" /> Sign Out
          </button>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left Column: Playlists & Pairing */}
          <div className="lg:col-span-1 space-y-8">
            
            {/* Playlists Module */}
            <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-xl">
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-lg font-semibold">My Credentials</h2>
                <button onClick={() => setShowAdd(!showAdd)} className="text-red-400 hover:text-red-300 p-1">
                  <Plus className="w-5 h-5" />
                </button>
              </div>

              {showAdd && (
                <form onSubmit={handleAddPlaylist} className="mb-6 space-y-3 bg-neutral-950 p-4 rounded-xl border border-neutral-800">
                  <input required placeholder="Provider Name" value={formName} onChange={(e: any)=>setFormName(e.target.value)} className="w-full bg-neutral-900 rounded p-2 text-sm focus:outline-none focus:ring-1 focus:ring-red-500" />
                  <input required placeholder="Server URL (http://...)" value={formServer} onChange={(e: any)=>setFormServer(e.target.value)} className="w-full bg-neutral-900 rounded p-2 text-sm focus:outline-none focus:ring-1 focus:ring-red-500" />
                  <input required placeholder="Username" value={formUser} onChange={(e: any)=>setFormUser(e.target.value)} className="w-full bg-neutral-900 rounded p-2 text-sm focus:outline-none focus:ring-1 focus:ring-red-500" />
                  <input required type="password" placeholder="Password" value={formPass} onChange={(e: any)=>setFormPass(e.target.value)} className="w-full bg-neutral-900 rounded p-2 text-sm focus:outline-none focus:ring-1 focus:ring-red-500" />
                  <button type="submit" className="w-full bg-red-600 text-white rounded p-2 text-sm font-medium hover:bg-red-700">Save</button>
                </form>
              )}

              <div className="space-y-3">
                {playlists.length === 0 ? (
                  <p className="text-sm text-neutral-500 text-center py-4">No credentials saved.</p>
                ) : (
                  playlists.map(pl => (
                    <div key={pl.id} className="bg-neutral-950 p-3 rounded-lg border border-neutral-800 flex items-center">
                      <Server className="w-4 h-4 text-emerald-400 mr-3 flex-shrink-0" />
                      <div className="overflow-hidden">
                        <p className="text-sm font-medium truncate">{pl.name}</p>
                        <p className="text-xs text-neutral-500 truncate">{pl.server}</p>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* TV Pairing Module */}
            <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-xl">
              <h2 className="text-lg font-semibold mb-2">Push to TV</h2>
              <p className="text-xs text-neutral-400 mb-4">Enter the code from your Android TV to securely push credentials.</p>
              
              <form onSubmit={handlePairTV} className="space-y-4">
                <select 
                  required
                  value={selectedPlaylist}
                  onChange={(e: any)=>setSelectedPlaylist(e.target.value)}
                  className="w-full bg-neutral-950 border border-neutral-800 rounded-xl p-3 text-sm focus:outline-none focus:ring-1 focus:ring-red-500"
                >
                  <option value="">Select Credentials...</option>
                  {playlists.map(pl => <option key={pl.id} value={pl.id}>{pl.name}</option>)}
                </select>
                
                <div className="flex space-x-2">
                  <input 
                    required maxLength={6} placeholder="123456" 
                    value={pairingCode} onChange={(e: any)=>setPairingCode(e.target.value.toUpperCase())}
                    className="flex-1 bg-neutral-950 border border-neutral-800 rounded-xl p-3 text-center tracking-widest font-mono focus:outline-none focus:ring-1 focus:ring-red-500"
                  />
                  <button type="submit" className="bg-red-600 text-white rounded-xl px-4 hover:bg-red-700 transition-colors">
                    <LinkIcon className="w-5 h-5" />
                  </button>
                </div>
              </form>
            </div>
          </div>

          {/* Right Column: Family Dashboard Live Grid */}
          <div className="lg:col-span-2">
            <h2 className="text-xl font-bold mb-6 flex items-center">
              <Activity className="w-5 h-5 text-emerald-500 mr-2" />
              Active Network
            </h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {devices.length === 0 && (
                <div className="col-span-full text-center py-20 text-neutral-500 border border-neutral-800 border-dashed rounded-2xl">
                  <Tv2 className="w-12 h-12 mx-auto mb-4 opacity-50" />
                  <p>No TVs are currently active on the network.</p>
                </div>
              )}
              {devices.map((device, i) => {
                const isOnline = (Date.now() - device.lastActive) < 1000 * 60 * 5;
                return (
                  <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} key={device.id} className="bg-neutral-900 border border-neutral-800 rounded-2xl p-5 shadow-lg">
                    <div className="flex justify-between items-start mb-4">
                      <div>
                        <h3 className="font-semibold text-white">{device.deviceName}</h3>
                        <p className="text-xs text-neutral-500 font-mono">{device.id.substring(0, 6)}</p>
                      </div>
                      <div className={`w-2 h-2 rounded-full ${isOnline ? 'bg-emerald-500 animate-pulse' : 'bg-neutral-600'}`} />
                    </div>
                    {device.isPlaying ? (
                      <div>
                        <p className="text-xs text-red-400 font-semibold mb-1 uppercase tracking-wider">{device.type}</p>
                        <p className="text-sm font-medium leading-tight">{device.currentTitle}</p>
                      </div>
                    ) : (
                      <div><p className="text-sm text-neutral-500">Idle / Navigating Menus</p></div>
                    )}
                  </motion.div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
