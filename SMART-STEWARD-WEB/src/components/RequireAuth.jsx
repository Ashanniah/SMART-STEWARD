import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { onAuthStateChanged } from 'firebase/auth';
import { getFirebaseAuth, isFirebaseConfigured } from '../firebase/config';

/**
 * Renders children only when Firebase Auth has a signed-in user.
 * When the app is not configured with Firebase env vars, children render (local dev).
 */
export default function RequireAuth({ children }) {
  const location = useLocation();
  const [state, setState] = useState(() => ({
    ready: !isFirebaseConfigured,
    user: null,
  }));

  useEffect(() => {
    if (!isFirebaseConfigured) return;

    const auth = getFirebaseAuth();
    if (!auth) {
      setState({ ready: true, user: null });
      return;
    }

    const unsub = onAuthStateChanged(auth, (user) => {
      setState({ ready: true, user });
    });
    return () => unsub();
  }, []);

  if (!isFirebaseConfigured) {
    return children;
  }

  if (!state.ready) {
    return (
      <div
        className="denr-dashboard__muted"
        style={{ padding: '2.5rem 1.5rem', textAlign: 'center' }}
      >
        Checking your session…
      </div>
    );
  }

  if (!state.user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}
