"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Tv2, Download, ShieldCheck, Zap, ArrowRight, Activity } from "lucide-react";
import { motion } from "motion/react";
import Link from "next/link";

export default function Home() {
  const [code, setCode] = useState("");
  const router = useRouter();

  const handlePair = (e: React.FormEvent) => {
    e.preventDefault();
    if (code.trim().length >= 5) {
      router.push(`/pair/${code.trim()}`);
    }
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-50 selection:bg-red-500/30">
      {/* Navigation */}
      <nav className="border-b border-neutral-900 bg-neutral-950/50 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <div className="w-8 h-8 bg-red-600 rounded-lg flex items-center justify-center">
              <Tv2 className="w-5 h-5 text-white" />
            </div>
            <span className="text-xl font-bold tracking-tight">RedSurf</span>
          </div>
          <div className="flex items-center space-x-6 text-sm font-medium">
            <Link href="/dashboard" className="text-neutral-400 hover:text-white flex items-center transition-colors">
              <Activity className="w-4 h-4 mr-2" />
              Family Dashboard
            </Link>
            <a href="/api/apk" className="px-4 py-2 bg-white text-black rounded-full hover:bg-neutral-200 transition-colors flex items-center">
              <Download className="w-4 h-4 mr-2" />
              Get APK
            </a>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto px-6 py-20">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 items-center">
          
          {/* Left Column: Hero Copy & Pairing */}
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-red-500/10 text-red-400 text-sm font-medium mb-6 border border-red-500/20">
              <Zap className="w-4 h-4" />
              <span>The Next Generation of IPTV</span>
            </div>
            
            <h1 className="text-5xl lg:text-7xl font-bold tracking-tight mb-6 leading-tight">
              Your TV,<br />
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-red-500 to-orange-500">
                Perfected.
              </span>
            </h1>
            
            <p className="text-lg text-neutral-400 mb-10 max-w-xl">
              A premium, native Android TV player built for speed. Featuring Multi-View PiP, DoH Anti-Throttling, and real-time cloud synchronization.
            </p>

            <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-2xl max-w-md">
              <h3 className="text-xl font-semibold mb-2">Connect Your TV</h3>
              <p className="text-sm text-neutral-400 mb-6">Enter the 6-digit code shown on your Android TV screen to sync your playlists and manage channels.</p>
              
              <form onSubmit={handlePair} className="flex space-x-3">
                <input
                  type="text"
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="e.g. 123456"
                  className="flex-1 bg-neutral-950 border border-neutral-800 rounded-xl px-4 py-3 text-white placeholder:text-neutral-600 focus:outline-none focus:ring-2 focus:ring-red-500 transition-all font-mono tracking-widest text-lg"
                  maxLength={6}
                />
                <button 
                  type="submit" 
                  disabled={code.length < 5}
                  className="bg-red-600 text-white px-6 py-3 rounded-xl font-medium hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center"
                >
                  Pair <ArrowRight className="w-4 h-4 ml-2" />
                </button>
              </form>
            </div>
          </motion.div>

          {/* Right Column: Features Graphic */}
          <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: 0.5, delay: 0.2 }} className="relative">
            <div className="absolute inset-0 bg-gradient-to-tr from-red-500/20 to-transparent blur-3xl rounded-full" />
            <div className="relative bg-neutral-900 border border-neutral-800 rounded-3xl p-8 shadow-2xl overflow-hidden">
              <div className="space-y-6">
                <FeatureCard 
                  icon={<ShieldCheck className="w-6 h-6 text-emerald-400" />}
                  title="Native DNS-over-HTTPS"
                  desc="Bypass ISP throttling and blocking automatically via Cloudflare or Google DoH natively in the player."
                />
                <FeatureCard 
                  icon={<Tv2 className="w-6 h-6 text-blue-400" />}
                  title="Multi-View PiP Grid"
                  desc="Watch up to 4 live streams simultaneously on a single screen with hardware-accelerated rendering."
                />
                <FeatureCard 
                  icon={<Activity className="w-6 h-6 text-purple-400" />}
                  title="Real-Time Cloud Sync"
                  desc="Pause a movie in the living room and resume in the bedroom. Manage hidden channels directly from your phone."
                />
              </div>
            </div>
          </motion.div>

        </div>
      </main>
    </div>
  );
}

function FeatureCard({ icon, title, desc }: { icon: React.ReactNode, title: string, desc: string }) {
  return (
    <div className="flex items-start space-x-4 p-4 rounded-2xl hover:bg-neutral-800/50 transition-colors">
      <div className="w-12 h-12 rounded-xl bg-neutral-950 border border-neutral-800 flex items-center justify-center flex-shrink-0 shadow-inner">
        {icon}
      </div>
      <div>
        <h4 className="text-lg font-semibold text-white mb-1">{title}</h4>
        <p className="text-sm text-neutral-400 leading-relaxed">{desc}</p>
      </div>
    </div>
  );
}
