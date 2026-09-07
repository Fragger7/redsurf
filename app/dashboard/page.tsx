"use client";

import { useEffect, useState } from "react";
import { collection, onSnapshot, query, orderBy } from "firebase/firestore";
import { db } from "../../lib/firebase";
import { Tv2, PlayCircle, PauseCircle, Activity } from "lucide-react";
import { motion } from "motion/react";

export default function FamilyDashboard() {
  const [devices, setDevices] = useState<any[]>([]);

  useEffect(() => {
    // Real-time listener for all TVs in the house
    const q = query(collection(db, "family_devices"));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const devs = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
      // Sort by recently active
      devs.sort((a, b) => b.lastActive - a.lastActive);
      setDevices(devs);
    });
    return () => unsubscribe();
  }, []);

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-50 p-8">
      <div className="max-w-6xl mx-auto">
        <header className="flex items-center justify-between border-b border-neutral-800 pb-8 mb-8">
          <div className="flex items-center space-x-4">
            <div className="w-12 h-12 bg-red-600 rounded-xl flex items-center justify-center shadow-lg shadow-red-900/20">
              <Activity className="w-6 h-6 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">Family Dashboard</h1>
              <p className="text-neutral-400">Real-time TV presence and multi-room sync</p>
            </div>
          </div>
          <div className="px-4 py-2 bg-neutral-900 text-neutral-300 rounded-full flex items-center text-sm font-medium border border-neutral-800">
            <div className="w-2 h-2 rounded-full bg-emerald-500 mr-2 animate-pulse"></div>
            Live Sync Active
          </div>
        </header>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {devices.length === 0 && (
            <div className="col-span-full text-center py-20 text-neutral-500">
              <Tv2 className="w-12 h-12 mx-auto mb-4 opacity-50" />
              <p>No TVs are currently active.</p>
            </div>
          )}

          {devices.map((device, i) => {
            const isOnline = (Date.now() - device.lastActive) < 1000 * 60 * 5; // 5 mins

            return (
              <motion.div 
                initial={{ opacity: 0, y: 20 }} 
                animate={{ opacity: 1, y: 0 }} 
                transition={{ delay: i * 0.1 }}
                key={device.id} 
                className="bg-neutral-900 border border-neutral-800 rounded-2xl overflow-hidden shadow-xl"
              >
                <div className="p-6 border-b border-neutral-800 bg-neutral-900/50 flex justify-between items-start">
                  <div>
                    <h2 className="text-lg font-semibold flex items-center">
                      <Tv2 className="w-5 h-5 mr-2 text-neutral-400" />
                      {device.deviceName}
                    </h2>
                    <p className="text-xs text-neutral-500 mt-1 font-mono">{device.id.substring(0, 8)}</p>
                  </div>
                  <div className={`px-2 py-1 rounded text-xs font-bold ${isOnline ? 'bg-emerald-500/20 text-emerald-400' : 'bg-neutral-800 text-neutral-500'}`}>
                    {isOnline ? 'ONLINE' : 'OFFLINE'}
                  </div>
                </div>
                
                <div className="p-6">
                  {device.isPlaying ? (
                    <div className="flex items-start space-x-4">
                      <PlayCircle className="w-8 h-8 text-red-500 flex-shrink-0" />
                      <div>
                        <p className="text-xs text-red-400 font-medium uppercase tracking-wider mb-1">Now Playing • {device.type}</p>
                        <p className="text-neutral-200 font-medium leading-tight">{device.currentTitle}</p>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-start space-x-4 opacity-50">
                      <PauseCircle className="w-8 h-8 text-neutral-500 flex-shrink-0" />
                      <div>
                        <p className="text-xs text-neutral-500 font-medium uppercase tracking-wider mb-1">Paused / Idle</p>
                        <p className="text-neutral-400 leading-tight">{device.currentTitle || "Navigating Menus"}</p>
                      </div>
                    </div>
                  )}
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
