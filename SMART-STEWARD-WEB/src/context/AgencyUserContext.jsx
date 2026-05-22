import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { USERS_COLLECTION } from '../constants/agencyAuth';
import { getFirebaseAuth, getFirestoreDb, isFirebaseConfigured } from '../firebase/config';
import { inferAgencyFromEmail, toCanonicalAgency } from '../utils/agencyScope';

const AgencyUserContext = createContext(null);

const emptyProfile = {
  ready: !isFirebaseConfigured,
  viewerAgencyKey: null,
  displayName: '',
  roleLabel: '',
  email: '',
  profileError: null,
};

function pickAgencyField(data) {
  if (!data || typeof data !== 'object') return '';
  return (
    data.agency ??
    data.organization ??
    data.org ??
    data.assignedAgency ??
    data.agencyName ??
    ''
  );
}

export function AgencyUserProvider({ children }) {
  const [state, setState] = useState(() => ({ ...emptyProfile }));

  useEffect(() => {
    if (!isFirebaseConfigured) return undefined;

    const auth = getFirebaseAuth();
    const db = getFirestoreDb();
    if (!auth || !db) {
      queueMicrotask(() => {
        setState({ ...emptyProfile, ready: true });
      });
      return undefined;
    }

    const unsub = onAuthStateChanged(auth, async (user) => {
      if (!user) {
        setState({
          ready: true,
          viewerAgencyKey: null,
          displayName: '',
          roleLabel: '',
          email: '',
          profileError: null,
        });
        return;
      }

      setState((s) => ({
        ...s,
        ready: false,
        email: user.email ?? '',
        profileError: null,
      }));

      try {
        const snap = await getDoc(doc(db, USERS_COLLECTION, user.uid));
        if (!snap.exists()) {
          setState({
            ready: true,
            viewerAgencyKey: null,
            displayName: user.email ?? '',
            roleLabel: '',
            email: user.email ?? '',
            profileError:
              'Your account profile was not found. Contact your administrator.',
          });
          return;
        }

        const data = snap.data();
        let agencyKey = toCanonicalAgency(pickAgencyField(data));
        if (!agencyKey) {
          agencyKey = inferAgencyFromEmail(user.email);
        }

        const displayName = String(
          data.displayName ?? data.name ?? data.fullName ?? agencyKey ?? user.email ?? ''
        ).trim();
        const roleLabel = String(
          data.roleLabel ?? data.roleTitle ?? data.role ?? 'Administrator'
        ).trim();

        if (!agencyKey) {
          setState({
            ready: true,
            viewerAgencyKey: null,
            displayName: displayName || user.email || '',
            roleLabel,
            email: user.email ?? '',
            profileError:
              'Your account is missing an agency assignment (DENR, PNP, BFP, or Barangay). Contact your administrator.',
          });
          return;
        }

        setState({
          ready: true,
          viewerAgencyKey: agencyKey,
          displayName: displayName || agencyKey,
          roleLabel: roleLabel || 'Administrator',
          email: user.email ?? '',
          profileError: null,
        });
      } catch (e) {
        console.error(e);
        setState({
          ready: true,
          viewerAgencyKey: null,
          displayName: '',
          roleLabel: '',
          email: user.email ?? '',
          profileError: e?.message ?? 'Could not load your profile.',
        });
      }
    });

    return () => unsub();
  }, []);

  const value = useMemo(() => {
    const skipAgencyScope = !isFirebaseConfigured;
    return {
      agencyReady: state.ready,
      viewerAgencyKey: state.viewerAgencyKey,
      displayName: state.displayName,
      roleLabel: state.roleLabel,
      email: state.email,
      profileError: state.profileError,
      skipAgencyScope,
    };
  }, [state]);

  return <AgencyUserContext.Provider value={value}>{children}</AgencyUserContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components -- hook colocated with provider
export function useAgencyUser() {
  const ctx = useContext(AgencyUserContext);
  if (!ctx) {
    throw new Error('useAgencyUser must be used within AgencyUserProvider');
  }
  return ctx;
}
