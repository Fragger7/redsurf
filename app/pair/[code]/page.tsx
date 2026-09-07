'use client';

import { useState, useEffect } from 'react';
import { useParams } from 'next/navigation';
import { db } from '@/lib/firebase';
import { doc, getDoc, updateDoc } from 'firebase/firestore';

export default function PairDevicePage() {
  const params = useParams();
  const code = params.code as string;
  
  const [status, setStatus] = useState<'loading' | 'invalid' | 'ready' | 'paired'>('loading');
  const [type, setType] = useState<'m3u' | 'xtream'>('m3u');
  
  // Form State
  const [m3uUrl, setM3uUrl] = useState('');
  const [epgUrl, setEpgUrl] = useState('');
  
  const [xtreamUrl, setXtreamUrl] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [includeVOD, setIncludeVOD] = useState(true);

  useEffect(() => {
    async function checkCode() {
      if (!code) return;
      try {
        const docRef = doc(db, 'pairingSessions', code);
        const snap = await getDoc(docRef);
        if (snap.exists() && snap.data().status === 'waiting') {
          setStatus('ready');
        } else {
          setStatus('invalid');
        }
      } catch (err) {
        console.error(err);
        setStatus('invalid');
      }
    }
    checkCode();
  }, [code]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (status !== 'ready') return;
    
    setStatus('loading');
    
    try {
      let finalUrl = '';
      if (type === 'm3u') {
        finalUrl = m3uUrl;
      } else {
        // Construct Xtream Codes API URL
        const cleanBase = xtreamUrl.endsWith('/') ? xtreamUrl.slice(0, -1) : xtreamUrl;
        const typeParam = includeVOD ? 'get_live_categories' : 'get_live_categories'; // Simplification for now
        finalUrl = `${cleanBase}/get.php?username=${username}&password=${password}&type=m3u_plus&output=ts`;
      }

      const docRef = doc(db, 'pairingSessions', code);
      await updateDoc(docRef, {
        url: finalUrl,
        epgUrl: epgUrl || '',
        status: 'paired'
      });
      
      setStatus('paired');
    } catch (err) {
      console.error(err);
      alert('Failed to pair. Please try again.');
      setStatus('ready');
    }
  };

  if (status === 'loading') {
    return <div className="flex items-center justify-center min-h-screen bg-neutral-950 text-white">Loading...</div>;
  }

  if (status === 'invalid') {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-neutral-950 text-white p-6 text-center">
        <h1 className="text-2xl font-bold text-red-500 mb-2">Invalid Code</h1>
        <p className="text-neutral-400">This pairing code has expired or does not exist. Please generate a new one on your TV.</p>
      </div>
    );
  }

  if (status === 'paired') {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-neutral-950 text-white p-6 text-center">
        <div className="w-16 h-16 bg-green-500 rounded-full flex items-center justify-center mb-4">
          <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" /></svg>
        </div>
        <h1 className="text-2xl font-bold mb-2">Device Paired!</h1>
        <p className="text-neutral-400">Your TV should now start loading the playlist automatically. You can close this window.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-neutral-950 text-white p-6">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold bg-gradient-to-r from-rose-500 to-orange-500 bg-clip-text text-transparent">RedSurf</h1>
          <p className="text-neutral-400 mt-2">Connect your playlist</p>
        </div>

        <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6">
          <div className="flex space-x-2 mb-6">
            <button 
              type="button"
              onClick={() => setType('m3u')}
              className={`flex-1 py-2 px-4 rounded-lg text-sm font-medium transition-colors ${type === 'm3u' ? 'bg-rose-600 text-white' : 'bg-neutral-800 text-neutral-400'}`}
            >
              M3U URL
            </button>
            <button 
              type="button"
              onClick={() => setType('xtream')}
              className={`flex-1 py-2 px-4 rounded-lg text-sm font-medium transition-colors ${type === 'xtream' ? 'bg-rose-600 text-white' : 'bg-neutral-800 text-neutral-400'}`}
            >
              Xtream Codes
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {type === 'm3u' ? (
              <>
                <div>
                  <label className="block text-sm font-medium text-neutral-400 mb-1">M3U Playlist URL</label>
                  <input 
                    type="url" 
                    required 
                    value={m3uUrl}
                    onChange={(e) => setM3uUrl(e.target.value)}
                    className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors"
                    placeholder="http://example.com/playlist.m3u"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-neutral-400 mb-1">EPG URL (Optional)</label>
                  <input 
                    type="url" 
                    value={epgUrl}
                    onChange={(e) => setEpgUrl(e.target.value)}
                    className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors"
                    placeholder="http://example.com/epg.xml"
                  />
                  <p className="text-xs text-neutral-500 mt-1">Leave blank to use provider's default EPG if available.</p>
                </div>
              </>
            ) : (
              <>
                <div>
                  <label className="block text-sm font-medium text-neutral-400 mb-1">Server URL</label>
                  <input 
                    type="url" 
                    required 
                    value={xtreamUrl}
                    onChange={(e) => setXtreamUrl(e.target.value)}
                    className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors"
                    placeholder="http://server.com:8080"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-neutral-400 mb-1">Username</label>
                  <input 
                    type="text" 
                    required 
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-neutral-400 mb-1">Password</label>
                  <input 
                    type="password" 
                    required 
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-4 py-3 focus:outline-none focus:border-rose-500 transition-colors"
                  />
                </div>
                <label className="flex items-center space-x-3 mt-4 p-3 bg-neutral-950 border border-neutral-800 rounded-lg cursor-pointer">
                  <input 
                    type="checkbox" 
                    checked={includeVOD}
                    onChange={(e) => setIncludeVOD(e.target.checked)}
                    className="w-5 h-5 accent-rose-600 rounded bg-neutral-900 border-neutral-700"
                  />
                  <span className="text-sm font-medium text-neutral-300">Include VOD (Movies & Series)</span>
                </label>
              </>
            )}

            <button 
              type="submit"
              className="w-full mt-6 bg-rose-600 hover:bg-rose-700 text-white font-bold py-3 px-4 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 focus:ring-offset-neutral-900"
            >
              Connect to TV
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
