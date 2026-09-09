"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { motion } from "motion/react";
import { Tv2, Server, LogOut, Plus, Link as LinkIcon, Activity, Settings, ShieldAlert, Trash2 } from "lucide-react";
import { auth, db } from "@/lib/firebase";
import { collection, query, onSnapshot, addDoc, serverTimestamp, setDoc, doc, deleteDoc } from "firebase/firestore";
import { signOut } from "firebase/auth";
import { useAuthState } from "react-firebase-hooks/auth";

export default function Dashboard() {
  const router = useRouter();
  const [user, authLoading] = useAuthState(auth);
  
  const [playlists, setPlaylists] = useState<any[]>([]);
  const [devices, setDevices] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  // Form states
  const [showAdd, setShowAdd] = useState(false);
  const [formName, setFormName] = useState("");
  const [formServer, setFormServer] = useState("");
  const [formUser, setFormUser] = useState("");
  const [formPass, setFormPass] = useState("");
  const [formType, setFormType] = useState("xtream"); // "xtream" or "m3u"
  const [formContentType, setFormContentType] = useState("both"); // "live", "vod", "both"

  const [pairingCode, setPairingCode] = useState("");
  const [selectedPlaylist, setSelectedPlaylist] = useState("");
  const [pairError, setPairError] = useState("");
  const [pairSuccess, setPairSuccess] = useState("");

  useEffect(() => {
    if (authLoading) return;
    if (!user) {
      router.push("/auth");
      return;
    }

    const q = query(collection(db, `users/${user.uid}/playlists`));
    const unsub = onSnapshot(q, (snap) => {
      setPlaylists(snap.docs.map(d => ({ id: d.id, ...d.data() })));
      setLoading(false);
    });

    const devQ = query(collection(db, "family_devices"));
    const unsubDev = onSnapshot(devQ, (snap) => {
      setDevices(snap.docs.map(d => ({ id: d.id, ...d.data() })));
    });

    return () => { unsub(); unsubDev(); };
  }, [user, authLoading, router]);

  const handleSignOut = async () => {
    try {
      await signOut(auth);
      router.push("/");
    } catch (e) {
      console.error("Sign out error", e);
    }
  };

  const handleAddPlaylist = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    await addDoc(collection(db, `users/${user.uid}/playlists`), {
      name: formName,
      server: formServer,
      username: formUser,
      password: formPass,
      type: formType,
      contentType: formContentType,
      isActive: true,
      addedAt: serverTimestamp()
    });
    setFormName(""); setFormServer(""); setFormUser(""); setFormPass(""); setFormContentType("both");
    setShowAdd(false);
  };

  const handleDeletePlaylist = async (id: string) => {
    if (!user || !confirm("Are you sure you want to delete this playlist?")) return;
    await deleteDoc(doc(db, `users/${user.uid}/playlists`, id));
  };

  const handleToggleActive = async (id: string, currentStatus: boolean) => {
    if (!user) return;
    await setDoc(doc(db, `users/${user.uid}/playlists`, id), { isActive: !currentStatus }, { merge: true });
  };

  const handlePairTV = async (e: React.FormEvent) => {
    e.preventDefault();
    setPairError("");
    setPairSuccess("");
    
    if (pairingCode.length !== 6) {
      setPairError("Code must be 6 digits.");
      return;
    }
    if (!selectedPlaylist) {
      setPairError("Please select a playlist to push.");
      return;
    }

    const pl = playlists.find(p => p.id === selectedPlaylist);
    if (!pl) return;

    try {
      await setDoc(doc(db, "pairingSessions", pairingCode.toUpperCase()), {
        status: "paired",
        playlistType: pl.type,
        contentType: pl.contentType || "both",
        name: pl.name,
        server: pl.server,
        username: pl.username,
        password: pl.password,
        hiddenGroups: [], // Will be managed in advanced settings later
        userId: user?.uid
      });
      
      setPairingCode("");
      setPairSuccess("Credentials pushed successfully! Check your TV.");
      setTimeout(() => setPairSuccess(""), 5000);
    } catch (err) {
      setPairError("Failed to push. Check permissions or try again.");
    }
  };

  if (loading || authLoading) return <div className="min-h-screen bg-neutral-950 text-white flex items-center justify-center">Loading...</div>;

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-50 flex flex-col">
      {/* Top Navbar */}
      <header className="border-b border-neutral-900 bg-neutral-950 sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 bg-red-600 rounded-xl flex items-center justify-center">
              <Tv2 className="w-5 h-5 text-white" />
            </div>
            <div>
              <span className="text-xl font-bold tracking-tight block leading-tight">RedSurf</span>
              <span className="text-[10px] text-red-400 font-mono tracking-widest uppercase">Cloud Console</span>
            </div>
          </div>
          <div className="flex items-center space-x-6">
            <span className="text-sm text-neutral-400 hidden sm:block">{user?.email}</span>
            <button onClick={handleSignOut} className="text-neutral-400 hover:text-white transition-colors flex items-center text-sm font-medium">
              <LogOut className="w-4 h-4 mr-2" /> Sign Out
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 p-6 md:p-8">
        <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          {/* Left Column (Playlists & TV Pairing) */}
          <div className="lg:col-span-5 space-y-8">
            
            {/* TV Pairing Module */}
            <div className="bg-gradient-to-b from-neutral-900 to-neutral-900/50 border border-neutral-800 rounded-3xl p-6 shadow-xl relative overflow-hidden">
              <div className="absolute top-0 right-0 w-32 h-32 bg-red-500/5 rounded-full blur-3xl -mr-10 -mt-10" />
              <h2 className="text-xl font-semibold mb-2 flex items-center">
                <LinkIcon className="w-5 h-5 mr-2 text-red-400" /> Push to TV
              </h2>
              <p className="text-sm text-neutral-400 mb-6">Enter the 6-digit code displayed on your RedSurf Android TV app to securely transmit credentials over the cloud.</p>
              
              <form onSubmit={handlePairTV} className="space-y-4 relative z-10">
                <div>
                  <label className="block text-xs text-neutral-500 mb-1 uppercase tracking-wider font-semibold">Select Playlist</label>
                  <select 
                    required
                    value={selectedPlaylist}
                    onChange={(e: any)=>setSelectedPlaylist(e.target.value)}
                    className="w-full bg-neutral-950 border border-neutral-800 rounded-xl p-3 text-sm focus:outline-none focus:border-red-500 focus:ring-1 focus:ring-red-500 appearance-none"
                  >
                    <option value="">Choose saved credentials...</option>
                    {playlists.filter(pl => pl.isActive !== false).map(pl => <option key={pl.id} value={pl.id}>{pl.name} ({pl.type})</option>)}
                  </select>
                </div>
                
                <div>
                  <label className="block text-xs text-neutral-500 mb-1 uppercase tracking-wider font-semibold">TV Pairing Code</label>
                  <div className="flex space-x-2">
                    <input 
                      required maxLength={6} placeholder="000000" 
                      value={pairingCode} onChange={(e: any)=>setPairingCode(e.target.value.replace(/\D/g, ''))}
                      className="flex-1 bg-neutral-950 border border-neutral-800 rounded-xl p-3 text-center tracking-[0.5em] font-mono focus:outline-none focus:border-red-500 focus:ring-1 focus:ring-red-500 text-lg"
                    />
                    <button type="submit" className="bg-red-600 text-white rounded-xl px-6 font-medium hover:bg-red-700 transition-colors shadow-lg shadow-red-900/20">
                      Push
                    </button>
                  </div>
                </div>

                {pairError && <p className="text-red-400 text-sm">{pairError}</p>}
                {pairSuccess && <p className="text-emerald-400 text-sm">{pairSuccess}</p>}
              </form>
            </div>

            {/* Playlists Module */}
            <div className="bg-neutral-900 border border-neutral-800 rounded-3xl p-6 shadow-xl">
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-xl font-semibold flex items-center">
                  <Server className="w-5 h-5 mr-2 text-blue-400" /> My Credentials
                </h2>
                <button onClick={() => setShowAdd(!showAdd)} className="bg-neutral-800 hover:bg-neutral-700 text-white rounded-full p-2 transition-colors">
                  <Plus className="w-5 h-5" />
                </button>
              </div>

              {showAdd && (
                <motion.form initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} onSubmit={handleAddPlaylist} className="mb-6 space-y-4 bg-neutral-950 p-5 rounded-2xl border border-neutral-800">
                  <div className="flex space-x-4 mb-4">
                    <label className="flex items-center space-x-2 cursor-pointer">
                      <input type="radio" checked={formType === "xtream"} onChange={() => setFormType("xtream")} className="text-red-500 focus:ring-red-500 bg-neutral-900 border-neutral-700" />
                      <span className="text-sm">Xtream Codes</span>
                    </label>
                    <label className="flex items-center space-x-2 cursor-pointer">
                      <input type="radio" checked={formType === "m3u"} onChange={() => setFormType("m3u")} className="text-red-500 focus:ring-red-500 bg-neutral-900 border-neutral-700" />
                      <span className="text-sm">M3U Link</span>
                    </label>
                  </div>
                  
                  <input required placeholder="Provider Name (e.g. My Provider)" value={formName} onChange={(e: any)=>setFormName(e.target.value)} className="w-full bg-neutral-900 rounded-lg p-3 text-sm focus:outline-none focus:border-red-500 border border-transparent" />
                  <input required placeholder="Server URL (http://...)" value={formServer} onChange={(e: any)=>setFormServer(e.target.value)} className="w-full bg-neutral-900 rounded-lg p-3 text-sm focus:outline-none focus:border-red-500 border border-transparent" />
                  <input required placeholder={formType === "xtream" ? "Username" : "Username (optional)"} value={formUser} onChange={(e: any)=>setFormUser(e.target.value)} className="w-full bg-neutral-900 rounded-lg p-3 text-sm focus:outline-none focus:border-red-500 border border-transparent" />
                  <input required={formType === "xtream"} type="password" placeholder={formType === "xtream" ? "Password" : "Password (optional)"} value={formPass} onChange={(e: any)=>setFormPass(e.target.value)} className="w-full bg-neutral-900 rounded-lg p-3 text-sm focus:outline-none focus:border-red-500 border border-transparent" />
                  
                  {formType === "xtream" && (
                    <div className="pt-2">
                      <label className="block text-xs text-neutral-500 mb-2 uppercase tracking-wider font-semibold">Content to Load</label>
                      <select 
                        value={formContentType}
                        onChange={(e) => setFormContentType(e.target.value)}
                        className="w-full bg-neutral-900 border border-transparent rounded-lg p-3 text-sm focus:outline-none focus:border-red-500"
                      >
                        <option value="both">Both (Live TV & VOD/Series)</option>
                        <option value="live">TV Channels Only</option>
                        <option value="vod">VOD (Movies & Series) Only</option>
                      </select>
                    </div>
                  )}

                  <button type="submit" className="w-full bg-neutral-100 text-neutral-900 rounded-lg p-3 text-sm font-semibold hover:bg-white transition-colors mt-4">Save Credentials</button>
                </motion.form>
              )}

              <div className="space-y-3">
                {playlists.length === 0 ? (
                  <div className="text-center py-8 border border-neutral-800 border-dashed rounded-2xl">
                    <p className="text-sm text-neutral-500">No credentials saved yet.</p>
                    <button onClick={() => setShowAdd(true)} className="text-red-400 text-sm mt-2 font-medium hover:text-red-300">Add your first playlist</button>
                  </div>
                ) : (
                  playlists.map(pl => (
                    <div key={pl.id} className="bg-neutral-950 p-4 rounded-xl border border-neutral-800 flex items-center justify-between group hover:border-neutral-700 transition-colors">
                      <div className="flex items-center overflow-hidden mr-4">
                        <div className="w-10 h-10 bg-blue-500/10 rounded-lg flex items-center justify-center mr-3 flex-shrink-0">
                          <Server className="w-5 h-5 text-blue-400" />
                        </div>
                        <div className="overflow-hidden">
                          <p className="text-sm font-semibold truncate">
                            {pl.name}
                            {pl.contentType && pl.contentType !== "both" && (
                              <span className="ml-2 text-[10px] uppercase tracking-wider bg-neutral-800 text-neutral-400 px-1.5 py-0.5 rounded">
                                {pl.contentType === "live" ? "Live Only" : "VOD Only"}
                              </span>
                            )}
                          </p>
                          <p className="text-xs text-neutral-500 truncate">{pl.server}</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <button 
                          onClick={() => handleToggleActive(pl.id, pl.isActive !== false)} 
                          className={`text-[10px] uppercase tracking-wider px-2 py-1 rounded transition-colors font-bold ${pl.isActive !== false ? 'bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500/20' : 'bg-neutral-800 text-neutral-500 hover:bg-neutral-700'}`}
                        >
                          {pl.isActive !== false ? "Active" : "Inactive"}
                        </button>
                        <button onClick={() => handleDeletePlaylist(pl.id)} className="text-neutral-600 hover:text-red-400 p-2 opacity-0 group-hover:opacity-100 transition-all">
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>

          {/* Right Column (Active Network) */}
          <div className="lg:col-span-7">
            <div className="bg-neutral-900 border border-neutral-800 rounded-3xl p-6 md:p-8 shadow-xl h-full min-h-[500px]">
              <div className="flex items-center justify-between mb-8">
                <div>
                  <h2 className="text-2xl font-bold flex items-center">
                    <Activity className="w-6 h-6 text-emerald-500 mr-3" />
                    Active Network
                  </h2>
                  <p className="text-sm text-neutral-400 mt-1">Monitor connected TVs across your household in real-time.</p>
                </div>
                <div className="w-3 h-3 bg-emerald-500 rounded-full animate-pulse shadow-[0_0_10px_rgba(16,185,129,0.5)]" />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {devices.length === 0 && (
                  <div className="col-span-full text-center py-24 text-neutral-500 border border-neutral-800 border-dashed rounded-3xl bg-neutral-950/50">
                    <Tv2 className="w-12 h-12 mx-auto mb-4 opacity-30" />
                    <p className="font-medium text-neutral-400">No TVs currently online.</p>
                    <p className="text-sm mt-1">When a TV connects and starts streaming, it will appear here.</p>
                  </div>
                )}
                {devices.map((device) => {
                  const isOnline = (new Date().getTime() - device.lastActive) < 1000 * 60 * 5;
                  if (!isOnline && device.hideWhenOffline) return null; // Logic option
                  
                  return (
                    <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} key={device.id} className="bg-neutral-950 border border-neutral-800 rounded-2xl p-5 shadow-lg relative overflow-hidden">
                      <div className="flex justify-between items-start mb-4">
                        <div>
                          <h3 className="font-semibold text-white flex items-center">
                            {device.deviceName || "Android TV"}
                          </h3>
                          <p className="text-xs text-neutral-500 font-mono mt-1">ID: {device.id.substring(0, 8)}</p>
                        </div>
                        <div className={`px-2 py-1 rounded text-[10px] font-bold uppercase tracking-wider ${isOnline ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-neutral-800 text-neutral-400'}`}>
                          {isOnline ? 'Online' : 'Offline'}
                        </div>
                      </div>
                      
                      <div className="mt-4 pt-4 border-t border-neutral-900">
                        {device.isPlaying && isOnline ? (
                          <div>
                            <div className="flex items-center mb-1">
                              <span className="w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse mr-2" />
                              <p className="text-[10px] text-red-400 font-bold uppercase tracking-widest">{device.type || "Live TV"}</p>
                            </div>
                            <p className="text-sm font-medium leading-snug line-clamp-2">{device.currentTitle}</p>
                          </div>
                        ) : (
                          <div className="flex items-center text-neutral-500">
                            <Settings className="w-4 h-4 mr-2 opacity-50" />
                            <p className="text-sm">{isOnline ? "Idle / Navigating Menus" : "Last seen recently"}</p>
                          </div>
                        )}
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            </div>
          </div>

        </div>
      </main>
    </div>
  );
}
