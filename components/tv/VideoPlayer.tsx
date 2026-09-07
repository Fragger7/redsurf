'use client';

import { useEffect, useRef } from 'react';
import Hls from 'hls.js';

interface VideoPlayerProps {
  url: string;
  isPlaying?: boolean;
}

export function VideoPlayer({ url, isPlaying = true }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !url) return;

    if (Hls.isSupported()) {
      if (hlsRef.current) {
        hlsRef.current.destroy();
      }
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
      });
      hlsRef.current = hls;
      
      hls.loadSource(url);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (isPlaying) {
          video.play().catch(e => console.log("Autoplay blocked:", e));
        }
      });
      
      hls.on(Hls.Events.ERROR, (event, data) => {
         if (data.fatal) {
           switch (data.type) {
             case Hls.ErrorTypes.NETWORK_ERROR:
               hls.startLoad();
               break;
             case Hls.ErrorTypes.MEDIA_ERROR:
               hls.recoverMediaError();
               break;
             default:
               hls.destroy();
               break;
           }
         }
      });

    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari fallback
      video.src = url;
      video.addEventListener('loadedmetadata', () => {
        if (isPlaying) {
          video.play().catch(e => console.log("Autoplay blocked:", e));
        }
      });
    }

    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
      }
    };
  }, [url, isPlaying]);

  useEffect(() => {
    if (videoRef.current) {
      if (isPlaying) {
        videoRef.current.play().catch(() => {});
      } else {
        videoRef.current.pause();
      }
    }
  }, [isPlaying]);

  return (
    <video
      ref={videoRef}
      className="absolute inset-0 w-full h-full object-contain bg-black z-0"
      playsInline
      muted={true} // Start muted to guarantee autoplay in browsers. User can unmute.
      autoPlay
    />
  );
}
