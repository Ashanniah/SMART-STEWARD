import { useMemo, useState } from 'react';
import { useNavigate, useParams, Navigate } from 'react-router-dom';
import { doc, serverTimestamp, updateDoc } from 'firebase/firestore';
import {
  SparklesIcon,
  ClipboardDocumentListIcon,
  VideoCameraIcon,
  LockClosedIcon,
} from '@heroicons/react/24/outline';
import {
  WORKFLOW_STATUS_ORDER,
  WORKFLOW_STATUS_META,
  workflowStatusIndex,
} from '../data/reportsMock';
import { useReportsData } from '../context/ReportsDataContext';
import { useAgencyUser } from '../context/AgencyUserContext';
import { writeAgencyNotification } from '../utils/writeAgencyNotification';
import {
  AGENCY_NOTIFICATION_KINDS,
  AGENCY_NOTIFICATION_SEVERITY,
} from '../constants/agencyNotificationKinds';
import { normalizedToDetailView } from '../utils/normalizeReportDoc';
import { workflowKeyToFirestoreStatus } from '../utils/workflowStatusFirestore';
import { getFirestoreDb, isFirebaseConfigured } from '../firebase/config';
import { REPORTS_COLLECTION } from '../constants/reportsCollection';

const STATUS_DOT_CLASS = {
  pending: 'status-update-legend__dot--pending',
  review: 'status-update-legend__dot--review',
  in_progress: 'status-update-legend__dot--progress',
  resolved: 'status-update-legend__dot--resolved',
  rejected: 'status-update-legend__dot--rejected',
};

const CURRENT_BADGE_CLASS = {
  pending: 'status-update-current__badge--pending',
  review: 'status-update-current__badge--review',
  in_progress: 'status-update-current__badge--progress',
  resolved: 'status-update-current__badge--resolved',
  rejected: 'status-update-current__badge--rejected',
};

const TIMELINE_DOT_CLASS = {
  pending: 'status-update-timeline__marker--pending',
  review: 'status-update-timeline__marker--review',
  in_progress: 'status-update-timeline__marker--progress',
  resolved: 'status-update-timeline__marker--resolved',
  rejected: 'status-update-timeline__marker--rejected',
};

const STATUS_UPDATE_KEYS = WORKFLOW_STATUS_ORDER.filter((key) => key !== 'review');

export default function ReportStatusUpdate() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const id = reportId ? decodeURIComponent(reportId) : '';

  const { reports, loading, reportByDocId } = useReportsData();
  const { viewerAgencyKey } = useAgencyUser();

  const detail = useMemo(() => {
    const row = id ? reportByDocId(id) : null;
    return row ? normalizedToDetailView(row) : null;
  }, [id, reportByDocId, reports]);

  const [selectedStatus, setSelectedStatus] = useState('');
  const [remarks, setRemarks] = useState('');
  const [saving, setSaving] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const rawCurrentKey = detail?.status ?? 'pending';
  const currentKey = rawCurrentKey === 'review' ? 'in_progress' : rawCurrentKey;
  const currentIndex = workflowStatusIndex(currentKey);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitError(null);
    if (!selectedStatus) {
      setSubmitError('Please select a status.');
      return;
    }
    if (!isFirebaseConfigured) {
      setSubmitError('Firebase is not configured.');
      return;
    }
    const db = getFirestoreDb();
    if (!db) {
      setSubmitError('Could not connect to the database.');
      return;
    }
    setSaving(true);
    try {
      const ref = doc(db, REPORTS_COLLECTION, id);
      const payload = {
        status: workflowKeyToFirestoreStatus(selectedStatus),
        statusUpdatedAt: serverTimestamp(),
      };
      const trimmed = remarks.trim();
      if (trimmed) payload.lastStatusNote = trimmed;
      await updateDoc(ref, payload);

      if (viewerAgencyKey && detail) {
        try {
          const label = WORKFLOW_STATUS_META[selectedStatus]?.label ?? selectedStatus;
          await writeAgencyNotification({
            targetAgency: viewerAgencyKey,
            title: 'Report status updated',
            body: `${label}: ${detail.reportTypeLabel ?? detail.activity} — ${detail.locationDisplay ?? detail.location}`,
            kind: AGENCY_NOTIFICATION_KINDS.STATUS_CHANGED,
            reportDocId: id,
            severity: AGENCY_NOTIFICATION_SEVERITY.INFO,
          });
        } catch (notifyErr) {
          console.warn('Could not record inbox notification:', notifyErr);
        }
      }

      navigate(`/reports/${encodeURIComponent(id)}`, { replace: false });
    } catch (err) {
      console.error(err);
      setSubmitError(err.message || 'Could not update status. Check permissions and try again.');
    } finally {
      setSaving(false);
    }
  }

  if (!id) {
    return <Navigate to="/reports" replace />;
  }

  if (loading && !detail) {
    return (
      <div className="status-update fade-in">
        <p className="denr-dashboard__muted" style={{ padding: '2rem' }}>
          Loading report…
        </p>
      </div>
    );
  }

  if (!detail) {
    return <Navigate to="/reports" replace />;
  }

  return (
    <div className="status-update fade-in">
      <header className="status-update-header">
        <h1 className="status-update__title">STATUS UPDATE PANEL</h1>
      </header>

      <p className="status-update__lead">Update the status of this Report</p>

      <section className="status-update-card status-update-card--status">
        <div className="status-update-current">
          <h2 className="status-update-current__heading">CURRENT STATUS</h2>
          <div
            className={`status-update-current__badge ${CURRENT_BADGE_CLASS[currentKey] ?? CURRENT_BADGE_CLASS.pending}`}
          >
            {WORKFLOW_STATUS_META[currentKey]?.label ?? 'Pending'}
          </div>
          <p className="status-update-current__desc">
            {WORKFLOW_STATUS_META[currentKey]?.currentDescription}
          </p>
        </div>

        <div className="status-update-timeline-wrap">
          <h2 className="status-update-timeline__heading">REPORT TIMELINE</h2>
          <ol className="status-update-timeline" aria-label="Report timeline">
            {STATUS_UPDATE_KEYS.map((key, i) => {
              const meta = WORKFLOW_STATUS_META[key];
              const isDone = i < currentIndex;
              const isCurrent = i === currentIndex;
              let markerClass = 'status-update-timeline__marker--future';
              if (isDone) markerClass = 'status-update-timeline__marker--done';
              else if (isCurrent) markerClass = TIMELINE_DOT_CLASS[key] ?? markerClass;

              return (
                <li
                  key={key}
                  className={`status-update-timeline__item ${isCurrent ? 'is-current' : ''} ${isDone ? 'is-done' : ''}`}
                >
                  <span
                    className={`status-update-timeline__marker ${markerClass}`}
                    aria-hidden
                  />
                  <div className="status-update-timeline__body">
                    <div className="status-update-timeline__title">{meta.label}</div>
                    {key === 'pending' && (
                      <time className="status-update-timeline__time">{detail.submittedAt}</time>
                    )}
                    <p className="status-update-timeline__sub">{meta.timelineSub}</p>
                  </div>
                </li>
              );
            })}
          </ol>
        </div>
      </section>

      <form className="status-update-form" onSubmit={handleSubmit}>
        <div className="status-update-form__grid">
          <div className="status-update-form__col">
            <p id="new-status-label" className="status-update-label">
              <SparklesIcon className="status-update-label__icon status-update-label__icon--green" aria-hidden />
              Select New Status
            </p>
            <select
              id="new-status"
              className="status-update-select"
              aria-labelledby="new-status-label"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
              disabled={saving}
            >
              <option value="">Select status</option>
              {STATUS_UPDATE_KEYS.map((key) => (
                <option key={key} value={key}>
                  {WORKFLOW_STATUS_META[key].label}
                </option>
              ))}
            </select>
            {submitError ? (
              <p className="status-update-error" role="alert">
                {submitError}
              </p>
            ) : null}
            <ul className="status-update-legend" aria-hidden>
              {STATUS_UPDATE_KEYS.map((key) => (
                <li key={key}>
                  <span
                    className={`status-update-legend__dot ${STATUS_DOT_CLASS[key] ?? ''}`}
                  />
                  {WORKFLOW_STATUS_META[key].label}
                </li>
              ))}
            </ul>
          </div>

          <div className="status-update-form__col">
            <label className="status-update-label" htmlFor="remarks">
              <ClipboardDocumentListIcon
                className="status-update-label__icon status-update-label__icon--blue"
                aria-hidden
              />
              Add Remarks/Notes
            </label>
            <textarea
              id="remarks"
              className="status-update-textarea"
              rows={4}
              placeholder="Write Remarks/Note about this update.."
              value={remarks}
              onChange={(e) => setRemarks(e.target.value)}
              disabled={saving}
            />

            <div className="status-update-action-block">
              <p className="status-update-label status-update-label--spaced">
                <VideoCameraIcon
                  className="status-update-label__icon status-update-label__icon--blue"
                  aria-hidden
                />
                Update report status
              </p>
              <button type="submit" className="status-update-submit" disabled={saving}>
                {saving ? 'Saving…' : 'Update Status'}
              </button>
              <p className="status-update-footnote">
                <LockClosedIcon aria-hidden />
                This action will be recorded in the report timeline and user will be notified
              </p>
            </div>
          </div>
        </div>
      </form>
    </div>
  );
}
