import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { collection, onSnapshot, query, where } from 'firebase/firestore';
import { AGENCY_NOTIFICATIONS_COLLECTION } from '../constants/agencyNotificationsCollection';
import { getFirebaseAuth, getFirestoreDb, isFirebaseConfigured } from '../firebase/config';
import { useAgencyUser } from './AgencyUserContext';

function toDate(val) {
  if (!val) return null;
  if (typeof val.toDate === 'function') return val.toDate();
  if (val instanceof Date) return val;
  if (typeof val.seconds === 'number') return new Date(val.seconds * 1000);
  return null;
}

const AgencyNotificationsContext = createContext(null);

export function AgencyNotificationsProvider({ children }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { viewerAgencyKey, agencyReady, profileError, skipAgencyScope } = useAgencyUser();
  const unsubSnapRef = useRef(() => {});

  useEffect(() => {
    if (!isFirebaseConfigured || skipAgencyScope) {
      unsubSnapRef.current();
      unsubSnapRef.current = () => {};
      queueMicrotask(() => {
        setItems([]);
        setLoading(false);
        setError(null);
      });
      return undefined;
    }

    const auth = getFirebaseAuth();
    const db = getFirestoreDb();
    if (!auth || !db) {
      queueMicrotask(() => {
        setItems([]);
        setLoading(false);
      });
      return undefined;
    }

    const attach = () => {
      unsubSnapRef.current();
      unsubSnapRef.current = () => {};
      setError(null);

      const user = auth.currentUser;
      if (!user) {
        setItems([]);
        setLoading(false);
        return;
      }

      if (!agencyReady || profileError || !viewerAgencyKey) {
        setItems([]);
        setLoading(false);
        return;
      }

      setLoading(true);
      const q = query(
        collection(db, AGENCY_NOTIFICATIONS_COLLECTION),
        where('targetAgency', '==', viewerAgencyKey)
      );

      unsubSnapRef.current = onSnapshot(
        q,
        (snap) => {
          const list = snap.docs.map((d) => {
            const data = d.data() || {};
            const createdAt = toDate(data.createdAt);
            const sev = String(data.severity ?? 'info').toLowerCase();
            const severity =
              sev === 'critical' || sev === 'warning' || sev === 'info' ? sev : 'info';
            return {
              id: d.id,
              title: String(data.title ?? 'Notification'),
              body: String(data.body ?? ''),
              kind: String(data.kind ?? 'citizen_notify'),
              reportDocId: data.reportDocId != null ? String(data.reportDocId) : '',
              createdAt,
              severity,
              pinned: Boolean(data.pinned),
              confidence:
                typeof data.confidence === 'number' && !Number.isNaN(data.confidence)
                  ? Math.round(data.confidence)
                  : null,
              synthetic: false,
            };
          });
          list.sort((a, b) => {
            const pa = a.pinned ? 1 : 0;
            const pb = b.pinned ? 1 : 0;
            if (pa !== pb) return pb - pa;
            const rank = { critical: 3, warning: 2, info: 1 };
            const ra = rank[a.severity] ?? 1;
            const rb = rank[b.severity] ?? 1;
            if (ra !== rb) return rb - ra;
            const ta = a.createdAt instanceof Date ? a.createdAt.getTime() : 0;
            const tb = b.createdAt instanceof Date ? b.createdAt.getTime() : 0;
            return tb - ta;
          });
          setItems(list);
          setLoading(false);
          setError(null);
        },
        (err) => {
          console.error(err);
          setItems([]);
          setLoading(false);
          setError(err.message || 'Could not load notifications.');
        }
      );
    };

    const unsubAuth = onAuthStateChanged(auth, attach);
    attach();

    return () => {
      unsubSnapRef.current();
      unsubSnapRef.current = () => {};
      unsubAuth();
    };
  }, [agencyReady, viewerAgencyKey, profileError, skipAgencyScope]);

  const value = useMemo(
    () => ({
      notifications: items,
      loading,
      error,
    }),
    [items, loading, error]
  );

  return (
    <AgencyNotificationsContext.Provider value={value}>{children}</AgencyNotificationsContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components -- hook colocated with provider
export function useAgencyNotifications() {
  const ctx = useContext(AgencyNotificationsContext);
  if (!ctx) {
    throw new Error('useAgencyNotifications must be used within AgencyNotificationsProvider');
  }
  return ctx;
}
