import { useMemo, useState } from 'react';
import { useNavigate, useParams, Navigate } from 'react-router-dom';
import { doc, serverTimestamp, updateDoc } from 'firebase/firestore';
import {
  CheckCircleIcon,
  ClipboardDocumentListIcon,
  ClockIcon,
  IdentificationIcon,
  FireIcon,
  LockClosedIcon,
  MapPinIcon,
  ExclamationTriangleIcon,
  ChevronDownIcon,
} from '@heroicons/react/24/outline';
import {
  CheckCircleIcon as CheckCircleIconSolid,
  XCircleIcon as XCircleIconSolid,
  ExclamationTriangleIcon as ExclamationTriangleIconSolid,
} from '@heroicons/react/24/solid';
import {
  WORKFLOW_STATUS_ORDER,
  WORKFLOW_STATUS_META,
  workflowStatusIndex,
  isTerminalWorkflowStatus,
  normalizeWorkflowStatusKey,
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
import AlertModal from '../components/AlertModal';
import ConfirmModal from '../components/ConfirmModal';
import SeverityBadge from '../components/SeverityBadge';

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
  const [statusError, setStatusError] = useState(null);
  const [remarksError, setRemarksError] = useState(null);
  const [successOpen, setSuccessOpen] = useState(false);
  const [successMessage, setSuccessMessage] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);

  const rawCurrentKey = detail?.status ?? 'pending';
  const currentKey = normalizeWorkflowStatusKey(rawCurrentKey);
  const currentIndex = workflowStatusIndex(currentKey);
  const isStatusLocked = isTerminalWorkflowStatus(rawCurrentKey);
  const selectedIsTerminal = isTerminalWorkflowStatus(selectedStatus);

  function handleSubmit(e) {
    e.preventDefault();
    setSubmitError(null);
    setStatusError(null);
    setRemarksError(null);
    if (isStatusLocked) {
      setSubmitError('This report is closed and its status can no longer be changed.');
      return;
    }
    if (!selectedStatus) {
      // Field-scoped error: shows a red border on the dropdown + an inline
      // message that clears the moment the admin picks a status.
      setStatusError('Please select a status.');
      return;
    }
    if (!remarks.trim()) {
      setRemarksError('Please add remarks explaining this status change before submitting.');
      return;
    }
    if (selectedIsTerminal) {
      setConfirmOpen(true);
      return;
    }
    performStatusUpdate();
  }

  async function performStatusUpdate() {
    setSubmitError(null);
    if (!isFirebaseConfigured) {
      setSubmitError('Firebase is not configured.');
      return;
    }
    const trimmed = remarks.trim();
    if (!trimmed) {
      setConfirmOpen(false);
      setRemarksError('Please add remarks explaining this status change before submitting.');
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
      // Citizen inbox writes are intentionally NOT performed from the web admin:
      //  - Firestore rules scope `users/{uid}/citizenInbox/*` to the citizen's own
      //    UID, so a different-user admin can never write there. The mobile app
      //    (signed in as the citizen) writes the notification itself the next time
      //    it observes this report's status / remarks change via its snapshot
      //    listener — see ReportStatusNotificationSync.kt.
      //
      //  - For the same reason we no longer set `lastCitizenNotifyFingerprint`
      //    here. Setting it used to make mobile think the web had already
      //    notified the citizen and silently skip its own notification, which is
      //    exactly the bug that caused empty inboxes on web-driven status changes.
      const payload = {
        status: workflowKeyToFirestoreStatus(selectedStatus),
        statusUpdatedAt: serverTimestamp(),
        lastStatusNote: trimmed,
      };
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

      // The citizen's mobile inbox notification (lifecycle + remarks) is
      // emitted by the mobile app from its own snapshot listener once it
      // observes the updated `status` / `lastStatusNote` written above. We
      // intentionally do not call writeCitizenNotification here — see the
      // payload comment in this same function for the full rationale.

      setRemarks('');
      setSelectedStatus('');
      setStatusError(null);
      setRemarksError(null);
      setSuccessMessage(
        `This report is now marked as ${statusLabel}. Your remarks were saved and the citizen will be notified.`
      );
      setSuccessOpen(true);
    } catch (err) {
      console.error(err);
      setSubmitError(err.message || 'Could not update status. Check permissions and try again.');
    } finally {
      setSaving(false);
      setConfirmOpen(false);
    }
  }

  if (!id) {
    return <Navigate to="/reports" replace />;
  }

  if (loading && !row) {
    return (
      <div className="reports-page report-status-update-page fade-in">
        <div className="report-status-update-page__inner">
          <p className="reports-table__loading">Loading report…</p>
        </div>
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

  const pendingTerminalLabel =
    selectedStatus === 'resolved'
      ? 'Resolved'
      : selectedStatus === 'rejected'
        ? 'Rejected'
        : '';

  const confirmIcon =
    selectedStatus === 'resolved'
      ? CheckCircleIconSolid
      : selectedStatus === 'rejected'
        ? XCircleIconSolid
        : ExclamationTriangleIconSolid;
  const confirmVariant =
    selectedStatus === 'resolved'
      ? 'primary'
      : selectedStatus === 'rejected'
        ? 'danger'
        : 'warning';

  return (
    <div className="reports-page report-status-update-page fade-in">
      <AlertModal
        open={successOpen}
        title="Status updated"
        message={successMessage}
        buttonLabel="OK"
        onClose={dismissSuccess}
      />

      <ConfirmModal
        open={confirmOpen}
        title="Are you sure you want to confirm this action?"
        message={`This will mark the report as ${pendingTerminalLabel}. Once saved, the status is final and can no longer be changed.`}
        confirmLabel={saving ? 'Saving…' : `Yes, mark as ${pendingTerminalLabel}`}
        cancelLabel="Cancel"
        icon={confirmIcon}
        variant={confirmVariant}
        onCancel={() => {
          if (!saving) setConfirmOpen(false);
        }}
        onConfirm={() => {
          if (!saving) performStatusUpdate();
        }}
      />

      <div className="report-status-update-page__inner">
        <section className="report-detail-card report-detail-card--info reports-table-card status-update-summary">
          <h2 className="report-detail-card__heading report-detail-card__heading--primary">
            <ClipboardDocumentListIcon aria-hidden />
            REPORT SUMMARY
          </h2>
          <dl className="report-detail-info">
            <div className="report-detail-info__row">
              <dt>
                <IdentificationIcon aria-hidden />
                Report ID
              </dt>
              <dd>{detail.id}</dd>
            </div>
            <div className="report-detail-info__row report-detail-info__row--status">
              <dt>
                <CheckCircleIcon aria-hidden />
                Current Status
              </dt>
              <dd>
                <WorkflowStatusPill status={rawCurrentKey} />
              </dd>
            </div>
            <div className="report-detail-info__row">
              <dt>
                <FireIcon aria-hidden />
                Report Type
              </dt>
              <dd>{detail.reportTypeLabel}</dd>
            </div>
            <div className="report-detail-info__row">
              <dt>
                <MapPinIcon aria-hidden /> Location
              </dt>
              <dd>{detail.locationDisplay}</dd>
            </div>
            <div className="report-detail-info__row report-detail-info__row--severity">
              <dt>
                <ExclamationTriangleIcon aria-hidden />
                Incident severity
              </dt>
              <dd>
                <SeverityBadge
                  severityKey={severityKey}
                  label={severityLabel}
                  reason={detail.incidentSeverityReason}
                />
              </dd>
            </div>
          </dl>
        </section>

        <section className="report-detail-card reports-table-card status-update-panel status-update-timeline-card">
          <h2 className="report-detail-card__heading">
            <ClockIcon aria-hidden />
            REPORT TIMELINE
          </h2>
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
        </section>

        <form
          className={`status-update-form ${isStatusLocked ? 'status-update-form--locked' : ''}`}
          onSubmit={handleSubmit}
          noValidate
        >
          <section className="report-detail-card reports-table-card status-update-panel">
            <h2 className="report-detail-card__heading report-detail-card__heading--primary">
              <CheckCircleIcon aria-hidden />
              UPDATE STATUS
            </h2>

            {isStatusLocked ? (
              <p className="status-update-locked-msg" role="status">
                <LockClosedIcon aria-hidden />
                This report is marked as{' '}
                <strong>{WORKFLOW_STATUS_META[currentKey]?.label ?? currentKey}</strong> and can no
                longer be updated.
              </p>
            ) : null}

            <div className="status-update-form__grid">
              <div className="status-update-form__col">
                <label className="status-update-field-label" htmlFor="new-status">
                  New status
                </label>
                <div className="incident-analytics-select-wrap status-update-select-wrap">
                  <select
                    id="new-status"
                    className={`incident-analytics-select status-update-select ${
                      statusError ? 'status-update-select--error' : ''
                    }`.trim()}
                    value={selectedStatus}
                    onChange={(e) => {
                      const next = e.target.value;
                      setSelectedStatus(next);
                      // Field-scoped validation: as soon as the admin picks a
                      // status, drop the red border + inline message so the
                      // form stops nagging them about it.
                      if (statusError && next) setStatusError(null);
                    }}
                    disabled={saving || isStatusLocked}
                    aria-required="true"
                    aria-invalid={statusError ? 'true' : 'false'}
                    aria-describedby={statusError ? 'new-status-error' : undefined}
                  >
                    <option value="">
                      {isStatusLocked ? 'Status locked' : 'Select status'}
                    </option>
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
                {statusError ? (
                  <p
                    id="new-status-error"
                    className="status-update-field-error"
                    role="alert"
                  >
                    {statusError}
                  </p>
                ) : null}
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
                  <span
                    className="status-update-field-label__required"
                    aria-hidden
                  >
                    *
                  </span>
                </label>
                <textarea
                  id="remarks"
                  className={`status-update-textarea ${
                    remarksError ? 'status-update-textarea--error' : ''
                  }`}
                  rows={5}
                  placeholder="Explain why you are changing this status. The citizen will see this note."
                  value={remarks}
                  onChange={(e) => {
                    setRemarks(e.target.value);
                    if (remarksError && e.target.value.trim()) setRemarksError(null);
                  }}
                  disabled={saving || isStatusLocked}
                  aria-required="true"
                  aria-invalid={remarksError ? 'true' : 'false'}
                  aria-describedby={remarksError ? 'remarks-error' : undefined}
                />
                {remarksError ? (
                  <p
                    id="remarks-error"
                    className="status-update-field-error"
                    role="alert"
                  >
                    {remarksError}
                  </p>
                ) : null}
                {!isStatusLocked ? (
                  <p className="status-update-footnote">
                    <LockClosedIcon aria-hidden />
                    Required. Recorded on the report timeline and sent to the citizen with the new status.
                  </p>
                ) : null}
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
            <button
              type="submit"
              className="reports-btn reports-btn--export"
              disabled={saving || isStatusLocked}
            >
              {saving ? 'Saving…' : 'Update status'}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
