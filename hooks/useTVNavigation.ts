'use client';

import { useEffect } from 'react';

type NavigationActions = {
  onUp?: () => void;
  onDown?: () => void;
  onLeft?: () => void;
  onRight?: () => void;
  onEnter?: () => void;
  onBack?: () => void;
  onAny?: () => void;
};

export function useTVNavigation(actions: NavigationActions, active: boolean = true) {
  useEffect(() => {
    if (!active) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      actions.onAny?.();
      
      switch (e.key) {
        case 'ArrowUp':
          e.preventDefault();
          actions.onUp?.();
          break;
        case 'ArrowDown':
          e.preventDefault();
          actions.onDown?.();
          break;
        case 'ArrowLeft':
          e.preventDefault();
          actions.onLeft?.();
          break;
        case 'ArrowRight':
          e.preventDefault();
          actions.onRight?.();
          break;
        case 'Enter':
          e.preventDefault();
          actions.onEnter?.();
          break;
        case 'Escape':
        case 'Backspace':
          e.preventDefault();
          actions.onBack?.();
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [actions, active]);
}
