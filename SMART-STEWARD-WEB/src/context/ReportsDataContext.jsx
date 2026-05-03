import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { collection, onSnapshot } from 'firebase/firestore';
import { getFirebaseAuth, getFirestoreDb, isFirebaseConfigured } from '../firebase/config';
import { REPORTS_COLLECTION } from '../constants/reportsCollection';
import { normalizeReportDocument } from '../utils/normalizeReportDoc';

const ReportsDataContext = createContext(null);

export function ReportsDataProvider({ children }) {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!isFirebaseConfigured) {
      setReports([]);
      setLoading(false);
      setError(null);
      return;
    }

    const auth = getFirebaseAuth();
    const db = getFirestoreDb();
    if (!auth || !db) {
      setReports([]);
      setLoading(false);
      setError(null);
      return;
    }

    let unsubSnap = () => {};

    const unsubAuth = onAuthStateChanged(auth, (user) => {
      unsubSnap();
      setError(null);
      if (!user) {
        setReports([]);
        setLoading(false);
        return;
      }

      setLoading(true);
      const colRef = collection(db, REPORTS_COLLECTION);
      unsubSnap = onSnapshot(
        colRef,
        (snapshot) => {
          const list = snapshot.docs.map((docSnap) =>
            normalizeReportDocument(docSnap.id, docSnap.data())
          );
          list.sort((a, b) => {
            const ta = a.createdAt instanceof Date ? a.createdAt.getTime() : 0;
            const tb = b.createdAt instanceof Date ? b.createdAt.getTime() : 0;
            return tb - ta;
          });
          setReports(list);
          setLoading(false);
          setError(null);
        },
        (err) => {
          console.error(err);
          setReports([]);
          setLoading(false);
          setError(
            err.code === 'permission-denied'
              ? 'No permission to read reports. Check Firestore rules for signed-in agency users.'
              : err.message || 'Failed to load reports.'
          );
        }
      );
    });

    return () => {
      unsubSnap();
      unsubAuth();
    };
  }, []);

  const value = useMemo(() => {
    const reportByDocId = (docId) => reports.find((r) => r.docId === docId);

    const counts = reports.reduce(
      (acc, r) => {
        acc.total += 1;
        if (r.status === 'pending') acc.pending += 1;
        else if (r.status === 'review') acc.review += 1;
        else if (r.status === 'resolved') acc.resolved += 1;
        else if (r.status === 'rejected') acc.rejected += 1;
        return acc;
      },
      { total: 0, pending: 0, review: 0, resolved: 0, rejected: 0 }
    );

    return {
      reports,
      loading,
      error,
      reportByDocId,
      counts,
    };
  }, [reports, loading, error]);

  return <ReportsDataContext.Provider value={value}>{children}</ReportsDataContext.Provider>;
}

export function useReportsData() {
  const ctx = useContext(ReportsDataContext);
  if (!ctx) {
    throw new Error('useReportsData must be used within ReportsDataProvider');
  }
  return ctx;
}
