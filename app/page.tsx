"use client";
import { useRouter } from "next/navigation";
import { motion } from "motion/react";
import { Tv, Server, Shield, Search, Zap, LogIn, ArrowRight, LayoutDashboard } from "lucide-react";
import { auth } from "@/lib/firebase";
import { useAuthState } from "react-firebase-hooks/auth";
import Link from "next/link";

export default function Home() {
  const router = useRouter();
  const [user, loading] = useAuthState(auth);

  return (
    <main className="min-h-screen bg-neutral-950 text-white selection:bg-red-500/30">
      {/* Navigation */}
      <nav className="border-b border-neutral-900 bg-neutral-950/50 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 bg-red-600 rounded-xl flex items-center justify-center">
              <Tv className="w-5 h-5 text-white" />
            </div>
            <span className="text-xl font-bold tracking-tight">RedSurf</span>
          </div>
          <div className="flex items-center space-x-4">
            {!loading && user ? (
              <Link href="/dashboard" className="px-5 py-2.5 bg-neutral-900 border border-neutral-800 text-neutral-300 rounded-full hover:bg-neutral-800 flex items-center transition-colors">
                <LayoutDashboard className="w-4 h-4 mr-2" /> Dashboard
              </Link>
            ) : (
              <Link href="/auth" className="px-5 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-full flex items-center transition-colors font-medium shadow-lg shadow-red-900/20">
                Sign In <ArrowRight className="w-4 h-4 ml-2" />
              </Link>
            )}
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="relative pt-32 pb-20 overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-red-900/20 via-neutral-950 to-neutral-950 -z-10" />
        <div className="max-w-7xl mx-auto px-6 text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
          >
            <h1 className="text-5xl md:text-7xl font-bold tracking-tighter mb-6">
              The Premium <br className="hidden md:block" />
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-red-500 to-rose-400">Android TV Experience</span>
            </h1>
            <p className="text-lg md:text-xl text-neutral-400 max-w-2xl mx-auto mb-10 leading-relaxed">
              RedSurf is engineered to deliver a world-class IPTV experience natively on Android TV. Manage multiple playlists, bypass ISP blocks, and push credentials directly from your phone.
            </p>
            {!loading && !user && (
              <div className="flex flex-col sm:flex-row items-center justify-center space-y-4 sm:space-y-0 sm:space-x-4">
                <Link href="/auth" className="px-8 py-4 bg-red-600 hover:bg-red-700 text-white rounded-full font-medium transition-colors flex items-center text-lg w-full sm:w-auto justify-center shadow-xl shadow-red-900/30">
                  Get Started Free
                </Link>
                <a href="/api/download" className="px-8 py-4 bg-neutral-900 border border-neutral-800 hover:bg-neutral-800 text-white rounded-full font-medium transition-colors flex items-center text-lg w-full sm:w-auto justify-center">
                  <Download className="w-5 h-5 mr-2" /> Download APK
                </a>
              </div>
            )}
            {!loading && user && (
              <div className="flex flex-col sm:flex-row items-center justify-center space-y-4 sm:space-y-0 sm:space-x-4">
                <Link href="/dashboard" className="px-8 py-4 bg-red-600 hover:bg-red-700 text-white rounded-full font-medium transition-colors flex items-center text-lg w-full sm:w-auto justify-center shadow-xl shadow-red-900/30">
                  Go to Dashboard <ArrowRight className="w-5 h-5 ml-2" />
                </Link>
                <a href="/api/download" className="px-8 py-4 bg-neutral-900 border border-neutral-800 hover:bg-neutral-800 text-white rounded-full font-medium transition-colors flex items-center text-lg w-full sm:w-auto justify-center">
                  <Download className="w-5 h-5 mr-2" /> Download APK
                </a>
              </div>
            )}
          </motion.div>
        </div>
      </section>

      {/* Features Grid */}
      <section className="py-24 bg-neutral-900/30 border-y border-neutral-900">
        <div className="max-w-7xl mx-auto px-6">
          <div className="text-center mb-16">
            <h2 className="text-3xl font-bold tracking-tight mb-4">Engineered for Performance</h2>
            <p className="text-neutral-400 max-w-2xl mx-auto">Everything you need for a seamless streaming experience, built straight into the core.</p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* Feature 1 */}
            <div className="bg-neutral-900/50 border border-neutral-800 p-8 rounded-3xl hover:bg-neutral-900 transition-colors">
              <div className="w-12 h-12 bg-red-500/10 rounded-2xl flex items-center justify-center mb-6">
                <Shield className="w-6 h-6 text-red-400" />
              </div>
              <h3 className="text-xl font-semibold mb-3">Custom DNS (DoH)</h3>
              <p className="text-neutral-400 leading-relaxed">
                Bypass ISP domain blocking natively with integrated DNS-over-HTTPS. Keep your streams connected securely.
              </p>
            </div>

            {/* Feature 2 */}
            <div className="bg-neutral-900/50 border border-neutral-800 p-8 rounded-3xl hover:bg-neutral-900 transition-colors">
              <div className="w-12 h-12 bg-emerald-500/10 rounded-2xl flex items-center justify-center mb-6">
                <Zap className="w-6 h-6 text-emerald-400" />
              </div>
              <h3 className="text-xl font-semibold mb-3">Auto Frame Rate</h3>
              <p className="text-neutral-400 leading-relaxed">
                Eliminate judder completely. RedSurf dynamically switches your TV&apos;s hardware refresh rate to perfectly match the stream&apos;s FPS.
              </p>
            </div>

            {/* Feature 3 */}
            <div className="bg-neutral-900/50 border border-neutral-800 p-8 rounded-3xl hover:bg-neutral-900 transition-colors">
              <div className="w-12 h-12 bg-blue-500/10 rounded-2xl flex items-center justify-center mb-6">
                <Server className="w-6 h-6 text-blue-400" />
              </div>
              <h3 className="text-xl font-semibold mb-3">Multi-Playlist Support</h3>
              <p className="text-neutral-400 leading-relaxed">
                Merge and manage multiple Xtream and M3U accounts simultaneously with deep schema separation and master favorites.
              </p>
            </div>

            {/* Feature 4 */}
            <div className="bg-neutral-900/50 border border-neutral-800 p-8 rounded-3xl hover:bg-neutral-900 transition-colors">
              <div className="w-12 h-12 bg-purple-500/10 rounded-2xl flex items-center justify-center mb-6">
                <Search className="w-6 h-6 text-purple-400" />
              </div>
              <h3 className="text-xl font-semibold mb-3">Global Matrix Search</h3>
              <p className="text-neutral-400 leading-relaxed">
                Unified search querying across Live Channels, VODs, Series, and EPG Program titles all at the exact same time.
              </p>
            </div>

            {/* Feature 5 */}
            <div className="lg:col-span-2 bg-gradient-to-br from-neutral-900 to-neutral-950 border border-neutral-800 p-8 rounded-3xl relative overflow-hidden">
              <div className="absolute top-0 right-0 p-8 opacity-10 pointer-events-none">
                <LayoutDashboard className="w-48 h-48" />
              </div>
              <div className="relative z-10">
                <div className="w-12 h-12 bg-rose-500/10 rounded-2xl flex items-center justify-center mb-6">
                  <Tv className="w-6 h-6 text-rose-400" />
                </div>
                <h3 className="text-2xl font-semibold mb-3">Cloud Pairing Console</h3>
                <p className="text-neutral-400 leading-relaxed max-w-xl mb-6">
                  Typing long Xtream credentials on a D-Pad is painful. Enter a 6-digit code on the RedSurf Cloud Console to instantly push your playlists, EPG offsets, and custom User-Agents securely to your TV.
                </p>
                {!loading && user ? (
                  <Link href="/dashboard" className="inline-flex items-center text-rose-400 hover:text-rose-300 font-medium">
                    Open Dashboard <ArrowRight className="w-4 h-4 ml-2" />
                  </Link>
                ) : (
                  <Link href="/auth" className="inline-flex items-center text-rose-400 hover:text-rose-300 font-medium">
                    Create your account <ArrowRight className="w-4 h-4 ml-2" />
                  </Link>
                )}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-neutral-900 py-12">
        <div className="max-w-7xl mx-auto px-6 flex flex-col md:flex-row justify-between items-center text-sm text-neutral-500">
          <p>© {new Date().getFullYear()} RedSurf IPTV. All rights reserved.</p>
          <div className="flex space-x-6 mt-4 md:mt-0">
            <Link href="#" className="hover:text-white transition-colors">Privacy Policy</Link>
            <Link href="#" className="hover:text-white transition-colors">Terms of Service</Link>
          </div>
        </div>
      </footer>
    </main>
  );
}
