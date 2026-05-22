import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { collection, onSnapshot } from 'firebase/firestore';
import { getFirebaseAuth, getFirestoreDb, isFirebaseConfigured } from '../firebase/config';
import { REPORTS_COLLECTION } from '../constants/reportsCollection';
import { normalizeReportDocument } from '../utils/normalizeReportDoc';
import { agenciesMatch } from '../utils/agencyScope';
import { useAgencyUser } from './AgencyUserContext';

const ReportsDataContext = createContext(null);

export function ReportsDataProvider({ children }) {
  const [rawReports, setRawReports] = useState([]);
  const [rawLoading, setRawLoading] = useState(() => Boolean(isFirebaseConfigured));
  const [firestoreError, setFirestoreError] = useState(null);

  const {
    agencyReady,
    viewerAgencyKey,
    profileError,
    skipAgencyScope,
  } = useAgencyUser();

  useEffect(() => {
    if (!isFirebaseConfigured) {
      queueMicrotask(() => {
        setRawReports([]);
        setRawLoading(false);
        setFirestoreError(null);
      });
      return undefined;
    }

    const auth = getFirebaseAuth();
    const db = getFirestoreDb();
    if (!auth || !db) {
      queueMicrotask(() => {
        setRawReports([]);
        setRawLoading(false);
        setFirestoreError(null);
      });
      return undefined;
    }

    let unsubSnap = () => {};

    const unsubAuth = onAuthStateChanged(auth, (user) => {
      unsubSnap();
      setFirestoreError(null);
      if (!user) {
        setRawReports([]);
        setRawLoading(false);
        return;
      }

      setRawLoading(true);
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
          setRawReports(list);
          setRawLoading(false);
          setFirestoreError(null);
        },
        (err) => {
          console.error(err);
          setRawReports([]);
          setRawLoading(false);
          setFirestoreError(
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

  const reports = useMemo(() => {
    if (skipAgencyScope) return rawReports;
    if (!agencyReady) return [];
    if (profileError || !viewerAgencyKey) return [];
    return rawReports.filter((r) => agenciesMatch(r.assignedAgency, viewerAgencyKey));
  }, [
    rawReports,
    agencyReady,
    viewerAgencyKey,
    profileError,
    skipAgencyScope,
  ]);

  const loading =
    rawLoading || (!skipAgencyScope && !agencyReady);

  const error = firestoreError || profileError || null;

  const value = useMemo(() => {
    const reportByDocId = (docId) => reports.find((r) => r.docId === docId);

    const counts = reports.reduce(
      (acc, r) => {
        acc.total += 1;
        if (r.status === 'pending') acc.pending += 1;
        else if (r.status === 'review' || r.status === 'in_progress') acc.review += 1;
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

// eslint-disable-next-line react-refresh/only-export-components -- hook colocated with provider
export function useReportsData() {
  const ctx = useContext(ReportsDataContext);
  if (!ctx) {
    throw new Error('useReportsData must be used within ReportsDataProvider');
  }
  return ctx;
}
