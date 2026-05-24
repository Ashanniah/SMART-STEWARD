import { useMemo, useState } from 'react';
import { useNavigate, useParams, Navigate } from 'react-router-dom';
import { doc, serverTimestamp, updateDoc } from 'firebase/firestore';
import {
  ArrowLeftIcon,
  CheckCircleIcon,
  ClipboardDocumentListIcon,
  ClockIcon,
  IdentificationIcon,
  FireIcon,
  LockClosedIcon,
  MapPinIcon,
  ChevronDownIcon,
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
import {
  CITIZEN_NOTIFICATION_KINDS,
  citizenKindForStatusTransition,
  workflowStatusFingerprint,
  writeCitizenNotification,
} from '../utils/writeCitizenNotification';
import { getFirestoreDb, isFirebaseConfigured } from '../firebase/config';
import { REPORTS_COLLECTION } from '../constants/reportsCollection';
import AlertModal from '../components/AlertModal';

const STATUS_DOT_CLASS = {
  pending: 'status-update-legend__dot--pending',
  review: 'status-update-legend__dot--review',
  in_progress: 'status-update-legend__dot--progress',
  resolved: 'status-update-legend__dot--resolved',
  rejected: 'status-update-legend__dot--rejected',
};

const TIMELINE_DOT_CLASS = {
  pending: 'status-update-timeline__marker--pending',
  review: 'status-update-timeline__marker--review',
  in_progress: 'status-update-timeline__marker--progress',
  resolved: 'status-update-timeline__marker--resolved',
  rejected: 'status-update-timeline__marker--rejected',
};

const STATUS_UPDATE_KEYS = WORKFLOW_STATUS_ORDER.filter((key) => key !== 'review');

const STATUS_LABELS = {
  pending: 'Pending',
  review: 'In Progress',
  in_progress: 'In Progress',
  resolved: 'Resolved',
  rejected: 'Rejected',
};

function WorkflowStatusPill({ status }) {
  const pillStatus =
    status === 'review' ? 'in_progress' : status === 'rejected' ? 'rejected' : status;
  return (
    <span className={`reports-status reports-status--${pillStatus}`}>
      {STATUS_LABELS[status] ?? status}
    </span>
  );
}

export default function ReportStatusUpdate() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const id = reportId ? decodeURIComponent(reportId) : '';

  const { loading, reportByDocId } = useReportsData();
  const { viewerAgencyKey } = useAgencyUser();
  const row = id ? reportByDocId(id) : null;
  const detail = useMemo(() => (row ? normalizedToDetailView(row) : null), [row]);
  const [selectedStatus, setSelectedStatus] = useState('');
  const [remarks, setRemarks] = useState('');
  const [saving, setSaving] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [successOpen, setSuccessOpen] = useState(false);
  const [successMessage, setSuccessMessage] = useState('');

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
      payload.lastCitizenNotifyFingerprint = workflowStatusFingerprint(selectedStatus);
      await updateDoc(ref, payload);

      const statusLabel = WORKFLOW_STATUS_META[selectedStatus]?.label ?? selectedStatus;

      if (viewerAgencyKey && detail) {
        try {
          await writeAgencyNotification({
            targetAgency: viewerAgencyKey,
            title: 'Report status updated',
            body: `${statusLabel}: ${detail.reportTypeLabel ?? detail.activity} — ${detail.locationDisplay ?? detail.location}`,
            kind: AGENCY_NOTIFICATION_KINDS.STATUS_CHANGED,
            reportDocId: id,
            severity: AGENCY_NOTIFICATION_SEVERITY.INFO,
          });
        } catch (notifyErr) {
          console.warn('Could not record agency inbox notification:', notifyErr);
        }
      }

      const citizenUserId = String(row?.userId ?? row?.raw?.userId ?? '').trim();
      const agencyLabel = detail.assignedAgency || viewerAgencyKey || 'Agency';
      if (citizenUserId) {
        try {
          const lifecycleKind = citizenKindForStatusTransition(currentKey, selectedStatus);
          if (lifecycleKind) {
            await writeCitizenNotification({
              userId: citizenUserId,
              kind: lifecycleKind,
              agency: agencyLabel,
              reportId: id,
            });
          }
          if (trimmed) {
            await writeCitizenNotification({
              userId: citizenUserId,
              kind: CITIZEN_NOTIFICATION_KINDS.ADMIN_COMMENT,
              agency: agencyLabel,
              reportId: id,
              customBody: trimmed,
            });
          }
        } catch (citizenErr) {
          console.warn('Could not notify citizen:', citizenErr);
        }
      }

      setRemarks('');
      setSelectedStatus('');
      setSuccessMessage(
        trimmed
          ? `This report is now marked as ${statusLabel}. Your remarks were saved and the citizen will be notified.`
          : `This report is now marked as ${statusLabel}. The update was recorded and the citizen will be notified.`
      );
      setSuccessOpen(true);
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

  if (loading && !row) {
    return (
      <div className="reports-page report-status-update-page fade-in">
        <p className="reports-table__loading">Loading report…</p>
      </div>
    );
  }

  if (!detail) {
    return <Navigate to="/reports" replace />;
  }

  function dismissSuccess() {
    setSuccessOpen(false);
    navigate(`/reports/${encodeURIComponent(id)}`, { replace: false });
  }

  const severityLabel = detail.incidentSeverityLabel || 'Not assessed';
  const severityKey = detail.incidentSeverityKey || 'unknown';

  return (
    <div className="reports-page report-status-update-page fade-in">
      <AlertModal
        open={successOpen}
        title="Status updated"
        message={successMessage}
        buttonLabel="OK"
        onClose={dismissSuccess}
      />

      <section className="denr-panel status-update-context">
        <div className="status-update-context__body">
          <p className="status-update-context__label">
            <IdentificationIcon aria-hidden />
            Report
          </p>
          <p className="status-update-context__id">{detail.id}</p>
          <div className="status-update-context__meta">
            <span className="status-update-context__chip">
              <FireIcon aria-hidden />
              {detail.reportTypeLabel}
            </span>
            <span className="status-update-context__chip status-update-context__chip--location">
              <MapPinIcon aria-hidden />
              {detail.locationDisplay}
            </span>
            <span
              className={`report-detail-severity report-detail-severity--${severityKey} status-update-context__severity`}
            >
              {severityLabel}
            </span>
          </div>
        </div>
        <button
          type="button"
          className="reports-btn reports-btn--muted status-update-context__back"
          onClick={() => navigate(`/reports/${encodeURIComponent(id)}`)}
        >
          <ArrowLeftIcon aria-hidden />
          Back to report
        </button>
      </section>

      <section className="denr-panel status-update-panel">
        <div className="denr-panel__head">
          <h2 className="denr-panel__title denr-panel__title--branded">
            <ClockIcon aria-hidden />
            Current status &amp; timeline
          </h2>
        </div>

        <div className="status-update-overview">
          <div className="status-update-overview__current">
            <h3 className="status-update-overview__subheading">Current status</h3>
            <WorkflowStatusPill status={rawCurrentKey} />
            <p className="status-update-overview__desc">
              {WORKFLOW_STATUS_META[currentKey]?.currentDescription}
            </p>
          </div>

          <div className="status-update-overview__timeline">
            <h3 className="status-update-overview__subheading">Report timeline</h3>
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
                      {key === 'pending' ? (
                        <time className="status-update-timeline__time">{detail.submittedAt}</time>
                      ) : null}
                      <p className="status-update-timeline__sub">{meta.timelineSub}</p>
                    </div>
                  </li>
                );
              })}
            </ol>
          </div>
        </div>
      </section>

      <form className="status-update-form" onSubmit={handleSubmit}>
        <section className="denr-panel status-update-panel">
          <div className="denr-panel__head">
            <h2 className="denr-panel__title denr-panel__title--branded">
              <CheckCircleIcon aria-hidden />
              Update status
            </h2>
          </div>
          <p className="denr-panel__subtitle status-update-form__intro">
            Choose the new workflow status and add optional remarks for the citizen record.
          </p>

          <div className="status-update-form__grid">
            <div className="status-update-form__col">
              <label className="status-update-field-label" htmlFor="new-status">
                New status
              </label>
              <div className="incident-analytics-select-wrap status-update-select-wrap">
                <select
                  id="new-status"
                  className="incident-analytics-select status-update-select"
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
                <ChevronDownIcon
                  className="incident-analytics-select__icon"
                  aria-hidden
                />
              </div>
              {submitError ? (
                <p className="status-update-error" role="alert">
                  {submitError}
                </p>
              ) : null}
              <ul className="status-update-legend" aria-label="Status colors">
                {STATUS_UPDATE_KEYS.map((key) => (
                  <li key={key}>
                    <span
                      className={`status-update-legend__dot ${STATUS_DOT_CLASS[key] ?? ''}`}
                      aria-hidden
                    />
                    {WORKFLOW_STATUS_META[key].label}
                  </li>
                ))}
              </ul>
            </div>

            <div className="status-update-form__col">
              <label className="status-update-field-label" htmlFor="remarks">
                <ClipboardDocumentListIcon aria-hidden />
                Remarks / notes
              </label>
              <textarea
                id="remarks"
                className="status-update-textarea"
                rows={5}
                placeholder="Add context for this status change (optional)."
                value={remarks}
                onChange={(e) => setRemarks(e.target.value)}
                disabled={saving}
              />
              <p className="status-update-footnote">
                <LockClosedIcon aria-hidden />
                Recorded on the report timeline; the citizen is notified of status changes.
              </p>
            </div>
          </div>
        </section>

        <footer className="status-update-form__footer">
          <button
            type="button"
            className="reports-btn reports-btn--muted"
            onClick={() => navigate(`/reports/${encodeURIComponent(id)}`)}
            disabled={saving}
          >
            Cancel
          </button>
          <button type="submit" className="reports-btn reports-btn--export" disabled={saving}>
            {saving ? 'Saving…' : 'Update status'}
          </button>
        </footer>
      </form>
    </div>
  );
}
