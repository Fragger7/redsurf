"use client";

import { useState, use } from "react";
import { motion } from "motion/react";
import { Link, CheckCircle2, Server, FileVideo, Loader2 } from "lucide-react";
import { db } from "@/lib/firebase";
import { doc, updateDoc } from "firebase/firestore";

export default function PairPage({ params }: { params: Promise<{ code: string }> }) {
  const resolvedParams = use(params);
  const code = resolvedParams.code;
  const [method, setMethod] = useState<"xtream" | "m3u">("xtream");
  
  // Xtream State
  const [server, setServer] = useState("");
  const [user, setUser] = useState("");
  const [pass, setPass] = useState("");

  // M3U State
  const [m3uUrl, setM3uUrl] = useState("");

  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      let finalUrl = "";
      if (method === "xtream") {
        const cleanServer = server.endsWith("/") ? server.slice(0, -1) : server;
        finalUrl = `${cleanServer}/get.php?username=${user}&password=${pass}&type=m3u_plus&output=ts`;
      } else {
        finalUrl = m3uUrl;
      }

      // Update Firestore session to trigger the TV's SnapshotListener
      const sessionRef = doc(db, "pairingSessions", code);
      await updateDoc(sessionRef, {
        status: "paired",
        url: finalUrl,
        updatedAt: Date.now()
      });

      setSuccess(true);
    } catch (err) {
      console.error(err);
      setError("Failed to push configuration to TV.");
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <main className="min-h-screen bg-[#09090b] text-white flex flex-col items-center justify-center p-4">
        <motion.div
          initial={{ scale: 0.9, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          className="w-full max-w-md bg-[#18181b] rounded-2xl p-8 border border-green-900/50 shadow-2xl text-center"
        >
          <div className="w-20 h-20 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-6">
            <CheckCircle2 className="w-10 h-10 text-green-500" />
          </div>
          <h2 className="text-3xl font-semibold mb-2">Successfully Paired!</h2>
          <p className="text-gray-400">
            Your playlist has been securely transmitted. You can now look at your TV. RedSurf is parsing your channels.
          </p>
        </motion.div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#09090b] text-white flex flex-col items-center justify-center p-4 py-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="w-full max-w-md bg-[#18181b] rounded-2xl p-8 border border-gray-800 shadow-2xl"
      >
        <div className="text-center mb-8">
          <h1 className="text-2xl font-semibold tracking-tight">Configure TV</h1>
          <p className="text-gray-400 mt-2 text-sm">Pairing Code: <span className="text-rose-500 font-mono font-bold tracking-widest">{code}</span></p>
        </div>

        <div className="flex gap-4 mb-8">
          <button
            onClick={() => setMethod("xtream")}
            className={`flex-1 py-3 rounded-xl border flex flex-col items-center justify-center gap-2 transition-colors ${
              method === "xtream" 
                ? "bg-rose-600/10 border-rose-600 text-rose-500" 
                : "bg-[#09090b] border-gray-800 text-gray-400 hover:border-gray-600"
            }`}
          >
            <Server className="w-5 h-5" />
            <span className="text-sm font-medium">Xtream Codes</span>
          </button>
          <button
            onClick={() => setMethod("m3u")}
            className={`flex-1 py-3 rounded-xl border flex flex-col items-center justify-center gap-2 transition-colors ${
              method === "m3u" 
                ? "bg-rose-600/10 border-rose-600 text-rose-500" 
                : "bg-[#09090b] border-gray-800 text-gray-400 hover:border-gray-600"
            }`}
          >
            <FileVideo className="w-5 h-5" />
            <span className="text-sm font-medium">M3U Playlist</span>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {method === "xtream" ? (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-4">
              <div>
                <label className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-2 block">Server URL</label>
                <input
                  type="url"
                  value={server}
                  onChange={(e) => setServer(e.target.value)}
                  placeholder="http://domain.com:8080"
                  required
                  className="w-full bg-[#09090b] border border-gray-700 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors text-white"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-2 block">Username</label>
                <input
                  type="text"
                  value={user}
                  onChange={(e) => setUser(e.target.value)}
                  required
                  className="w-full bg-[#09090b] border border-gray-700 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors text-white"
                />
              </div>
              <div>
                <label className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-2 block">Password</label>
                <input
                  type="password"
                  value={pass}
                  onChange={(e) => setPass(e.target.value)}
                  required
                  className="w-full bg-[#09090b] border border-gray-700 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors text-white"
                />
              </div>
            </motion.div>
          ) : (
             <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-4">
              <div>
                <label className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-2 block">M3U URL</label>
                <input
                  type="url"
                  value={m3uUrl}
                  onChange={(e) => setM3uUrl(e.target.value)}
                  placeholder="http://..."
                  required
                  className="w-full bg-[#09090b] border border-gray-700 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors text-white"
                />
              </div>
             </motion.div>
          )}

          {error && <p className="text-rose-500 text-sm font-medium mt-4">{error}</p>}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-rose-600 hover:bg-rose-700 text-white font-medium rounded-xl py-4 flex items-center justify-center transition-colors disabled:opacity-50 mt-8"
          >
            {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : "Push to TV"}
          </button>
        </form>
      </motion.div>
    </main>
  );
}
