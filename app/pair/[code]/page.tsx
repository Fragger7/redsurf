"use client";

import { useEffect, useState } from "react";
import { doc, onSnapshot, updateDoc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { useParams } from "next/navigation";
import { CheckCircle2, Tv2, ListVideo, EyeOff } from "lucide-react";
import { motion } from "motion/react";

export default function PairSessionPage() {
  const params = useParams();
  const code = params.code as string;
  const [status, setStatus] = useState("waiting");
  const [channels, setChannels] = useState<any[]>([]);

  useEffect(() => {
    const sessionRef = doc(db, "pairingSessions", code);
    const unsubscribe = onSnapshot(sessionRef, (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();
        setStatus(data.status);
        if (data.channels) setChannels(data.channels);
      }
    });
    return () => unsubscribe();
  }, [code]);

  const toggleHideChannel = async (channelId: string, currentHidden: boolean) => {
    const sessionRef = doc(db, "pairingSessions", code);
    await updateDoc(sessionRef, {
      [\`channelStates.\${channelId}.isHidden\`]: !currentHidden
    });
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-50 p-8">
      <div className="max-w-4xl mx-auto">
        <header className="flex items-center justify-between border-b border-neutral-800 pb-8 mb-8">
          <div className="flex items-center space-x-4">
            <div className="w-12 h-12 bg-red-600 rounded-xl flex items-center justify-center">
              <Tv2 className="w-6 h-6 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">RedSurf TV Manager</h1>
              <p className="text-neutral-400">Pairing Code: {code}</p>
            </div>
          </div>
          {status === "paired" ? (
            <div className="px-4 py-2 bg-emerald-500/10 text-emerald-400 rounded-full flex items-center text-sm font-medium border border-emerald-500/20">
              <CheckCircle2 className="w-4 h-4 mr-2" /> Connected to TV
            </div>
          ) : (
            <div className="px-4 py-2 bg-amber-500/10 text-amber-400 rounded-full flex items-center text-sm font-medium border border-amber-500/20 animate-pulse">
              Waiting for TV...
            </div>
          )}
        </header>

        {status === "paired" && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
            <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6">
              <h2 className="text-xl font-semibold mb-4 flex items-center">
                <ListVideo className="w-5 h-5 mr-2 text-red-500" /> Remote Channel Management
              </h2>
              <p className="text-neutral-400 mb-6">Manage your TV's channels, hide unwanted content, and reorder groups directly from your browser. Changes sync to your TV instantly.</p>
              
              <div className="bg-neutral-950 rounded-xl border border-neutral-800 divide-y divide-neutral-800">
                {[
                  { id: "1", name: "BBC One HD", group: "UK General", hidden: false },
                  { id: "2", name: "Sky Sports Main Event", group: "UK Sports", hidden: false },
                  { id: "3", name: "Adult Channel 1", group: "XXX", hidden: true },
                ].map((ch) => (
                  <div key={ch.id} className="p-4 flex items-center justify-between hover:bg-neutral-900/50 transition-colors">
                    <div>
                      <h3 className="font-medium">{ch.name}</h3>
                      <p className="text-xs text-neutral-500">{ch.group}</p>
                    </div>
                    <button className={\`px-4 py-2 rounded-lg text-sm font-medium border transition-colors \${ch.hidden ? 'bg-neutral-800 border-neutral-700 text-neutral-300' : 'bg-red-600/10 border-red-600/20 text-red-500 hover:bg-red-600/20'}\`}>
                      {ch.hidden ? <span className="flex items-center"><EyeOff className="w-4 h-4 mr-2"/> Hidden</span> : 'Hide on TV'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );
}
