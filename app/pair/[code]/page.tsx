"use client";

import { useEffect, useState } from "react";
import { doc, onSnapshot, updateDoc } from "firebase/firestore";
import { db } from "../../../lib/firebase";
import { useParams } from "next/navigation";
import { CheckCircle2, Tv2, ListVideo, EyeOff, Eye } from "lucide-react";
import { motion } from "motion/react";

export default function PairSessionPage() {
  const params = useParams();
  const code = params.code as string;
  const [status, setStatus] = useState("waiting");
  const [hiddenGroups, setHiddenGroups] = useState<string[]>([]);

  useEffect(() => {
    const sessionRef = doc(db, "pairingSessions", code);
    const unsubscribe = onSnapshot(sessionRef, (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data();
        setStatus(data.status);
        if (data.hiddenGroups) setHiddenGroups(data.hiddenGroups);
      }
    });
    return () => unsubscribe();
  }, [code]);

  const toggleHideGroup = async (groupName: string, isHidden: boolean) => {
    const sessionRef = doc(db, "pairingSessions", code);
    const newHiddenGroups = isHidden 
      ? hiddenGroups.filter(g => g !== groupName) 
      : [...hiddenGroups, groupName];
      
    await updateDoc(sessionRef, { hiddenGroups: newHiddenGroups });
  };

  // Mock groups that would normally come from the TV's playlist parse
  const availableGroups = ["UK Sports", "US News", "Movies", "Kids", "International", "Adult XXX"];

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
                <ListVideo className="w-5 h-5 mr-2 text-red-500" /> Category / Group Management
              </h2>
              <p className="text-neutral-400 mb-6">Hide entire groups (like Adult or International channels) here. This enables <b>Smart Sync</b>: your TV will completely ignore hidden groups during playlist parsing, saving massive amounts of memory and bandwidth.</p>
              
              <div className="bg-neutral-950 rounded-xl border border-neutral-800 divide-y divide-neutral-800">
                {availableGroups.map((group) => {
                  const isHidden = hiddenGroups.includes(group);
                  return (
                    <div key={group} className="p-4 flex items-center justify-between hover:bg-neutral-900/50 transition-colors">
                      <h3 className={`font-medium ${isHidden ? 'text-neutral-500 line-through' : 'text-neutral-200'}`}>
                        {group}
                      </h3>
                      <button 
                        onClick={() => toggleHideGroup(group, isHidden)}
                        className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
                          isHidden 
                            ? 'bg-neutral-800 border-neutral-700 text-neutral-300 hover:bg-neutral-700' 
                            : 'bg-red-600/10 border-red-600/20 text-red-500 hover:bg-red-600/20'
                        }`}
                      >
                        {isHidden ? <span className="flex items-center"><Eye className="w-4 h-4 mr-2"/> Show</span> : <span className="flex items-center"><EyeOff className="w-4 h-4 mr-2"/> Hide</span>}
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );
}
