"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { motion } from "motion/react";
import { Tv, KeyRound, ArrowRight, Loader2 } from "lucide-react";
import { db } from "@/lib/firebase";
import { doc, getDoc } from "firebase/firestore";

export default function Home() {
  const router = useRouter();
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handlePair = async (e: React.FormEvent) => {
    e.preventDefault();
    if (code.length !== 6) {
      setError("Please enter a valid 6-digit code.");
      return;
    }

    setLoading(true);
    setError("");

    try {
      // Verify if the session exists in Firestore
      const sessionRef = doc(db, "pairingSessions", code);
      const sessionSnap = await getDoc(sessionRef);

      if (!sessionSnap.exists()) {
        setError("Invalid or expired pairing code. Please check your TV.");
        setLoading(false);
        return;
      }

      const data = sessionSnap.data();
      if (data.status === "paired") {
        setError("This code has already been paired.");
        setLoading(false);
        return;
      }

      // Valid session, redirect to the playlist configuration screen
      router.push(`/pair/${code}`);
    } catch (err) {
      console.error(err);
      setError("Failed to connect to pairing service. Please try again.");
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-[#09090b] text-white flex flex-col items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md bg-[#18181b] rounded-2xl p-8 border border-gray-800 shadow-2xl"
      >
        <div className="flex flex-col items-center text-center mb-8">
          <div className="w-16 h-16 bg-rose-600/20 rounded-full flex items-center justify-center mb-4">
            <Tv className="w-8 h-8 text-rose-500" />
          </div>
          <h1 className="text-3xl font-semibold tracking-tight">RedSurf</h1>
          <p className="text-gray-400 mt-2 text-sm">
            Enter the 6-digit code displayed on your TV to securely transfer your playlist.
          </p>
        </div>

        <form onSubmit={handlePair} className="space-y-6">
          <div className="space-y-2">
            <div className="relative">
              <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-500" />
              <input
                type="text"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                placeholder="000000"
                className="w-full bg-[#09090b] border border-gray-700 text-center text-3xl tracking-[0.5em] rounded-xl py-4 focus:outline-none focus:border-rose-500 transition-colors"
                required
              />
            </div>
            {error && <p className="text-rose-500 text-sm text-center font-medium">{error}</p>}
          </div>

          <button
            type="submit"
            disabled={loading || code.length !== 6}
            className="w-full bg-rose-600 hover:bg-rose-700 text-white font-medium rounded-xl py-4 flex items-center justify-center transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <>
                Connect to TV
                <ArrowRight className="w-5 h-5 ml-2" />
              </>
            )}
          </button>
        </form>
      </motion.div>
    </main>
  );
}
