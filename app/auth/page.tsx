"use client";

import { useState } from "react";
import { auth } from "../../lib/firebase";
import { createUserWithEmailAndPassword, signInWithEmailAndPassword, sendPasswordResetEmail } from "firebase/auth";
import { useRouter } from "next/navigation";
import { Tv2, Lock, Mail, ArrowRight, ArrowLeft } from "lucide-react";

export default function AuthPage() {
  const [mode, setMode] = useState<"login" | "signup" | "reset">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const router = useRouter();

  const handleAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setMessage("");
    try {
      if (mode === "login") {
        await signInWithEmailAndPassword(auth, email, password);
        router.push("/dashboard");
      } else if (mode === "signup") {
        await createUserWithEmailAndPassword(auth, email, password);
        router.push("/dashboard");
      } else if (mode === "reset") {
        await sendPasswordResetEmail(auth, email);
        setMessage("Password reset email sent. Check your inbox.");
        setMode("login");
      }
    } catch (err: any) {
      setError(err.message.replace("Firebase: ", ""));
    }
  };

  return (
    <div className="min-h-screen bg-neutral-950 flex items-center justify-center p-4 selection:bg-red-500/30">
      <div className="w-full max-w-md bg-neutral-900 border border-neutral-800 rounded-3xl p-8 shadow-2xl">
        <div className="flex justify-center mb-8">
          <div className="w-12 h-12 bg-red-600 rounded-xl flex items-center justify-center">
            <Tv2 className="w-6 h-6 text-white" />
          </div>
        </div>
        
        <h2 className="text-2xl font-bold text-white text-center mb-2">
          {mode === "login" ? "Welcome Back" : mode === "signup" ? "Create Account" : "Reset Password"}
        </h2>
        <p className="text-neutral-400 text-center mb-8">
          {mode === "reset" ? "Enter your email to receive a password reset link." : "Manage your IPTV credentials securely in the cloud."}
        </p>

        {error && (
          <div className="bg-red-500/10 border border-red-500/20 text-red-400 text-sm p-3 rounded-lg mb-6">
            {error}
          </div>
        )}
        
        {message && (
          <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm p-3 rounded-lg mb-6">
            {message}
          </div>
        )}

        <form onSubmit={handleAuth} className="space-y-4">
          <div>
            <label className="text-sm font-medium text-neutral-300 block mb-2">Email Address</label>
            <div className="relative">
              <Mail className="w-5 h-5 text-neutral-500 absolute left-3 top-3.5" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full bg-neutral-950 border border-neutral-800 rounded-xl pl-10 pr-4 py-3 text-white focus:ring-2 focus:ring-red-500 focus:outline-none transition-all"
                placeholder="you@example.com"
              />
            </div>
          </div>
          
          {mode !== "reset" && (
            <div>
              <div className="flex justify-between items-center mb-2">
                <label className="text-sm font-medium text-neutral-300 block">Password</label>
                {mode === "login" && (
                  <button type="button" onClick={() => { setMode("reset"); setError(""); setMessage(""); }} className="text-xs text-red-400 hover:text-red-300">
                    Forgot password?
                  </button>
                )}
              </div>
              <div className="relative">
                <Lock className="w-5 h-5 text-neutral-500 absolute left-3 top-3.5" />
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full bg-neutral-950 border border-neutral-800 rounded-xl pl-10 pr-4 py-3 text-white focus:ring-2 focus:ring-red-500 focus:outline-none transition-all"
                  placeholder="••••••••"
                />
              </div>
            </div>
          )}

          <button
            type="submit"
            className="w-full bg-red-600 text-white font-medium py-3 rounded-xl hover:bg-red-700 transition-colors flex items-center justify-center mt-2"
          >
            {mode === "login" ? "Sign In" : mode === "signup" ? "Create Account" : "Send Reset Link"} 
            {mode === "reset" ? null : <ArrowRight className="w-4 h-4 ml-2" />}
          </button>
        </form>

        <div className="mt-6 text-center">
          {mode === "reset" ? (
            <button
              onClick={() => { setMode("login"); setError(""); setMessage(""); }}
              className="text-neutral-400 hover:text-white text-sm transition-colors flex items-center justify-center w-full"
            >
              <ArrowLeft className="w-4 h-4 mr-2" /> Back to login
            </button>
          ) : (
            <button
              onClick={() => { setMode(mode === "login" ? "signup" : "login"); setError(""); setMessage(""); }}
              className="text-neutral-400 hover:text-white text-sm transition-colors"
            >
              {mode === "login" ? "Need an account? Sign up" : "Already have an account? Sign in"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
