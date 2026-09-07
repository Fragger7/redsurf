import { initializeApp, getApps, getApp } from 'firebase/app';
import { getFirestore, initializeFirestore } from 'firebase/firestore';
import { getAuth } from 'firebase/auth';

const firebaseConfig = {
  projectId: "fourth-surge-1txfk",
  appId: "1:235290029551:web:69bd54a54b8b96cef75a1a",
  apiKey: "AIzaSyAEMXqNKt4IVSYEx_SYKySjty_gDhyDUAM",
  authDomain: "fourth-surge-1txfk.firebaseapp.com",
  storageBucket: "fourth-surge-1txfk.firebasestorage.app",
  messagingSenderId: "235290029551",
};

const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
const db = getFirestore(app, "ai-studio-streammateiptv-78859c44-ff72-4eb1-ad03-6166dc68ed30");
const auth = getAuth(app);

export { app, db, auth };
