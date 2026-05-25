import { addDoc, collection, serverTimestamp } from 'firebase/firestore';
import { getFirestoreDb } from '../firebase/config';

/** Must match [CitizenNotificationKind] keys in the Android app. */
export const CITIZEN_NOTIFICATION_KINDS = {
  LIFECYCLE_SUBMITTED: 'lifecycle_submitted',
  LIFECYCLE_RECEIVED: 'lifecycle_received',
  LIFECYCLE_UNDER_REVIEW: 'lifecycle_under_review',
  LIFECYCLE_IN_PROGRESS: 'lifecycle_in_progress',
  LIFECYCLE_RESOLVED: 'lifecycle_resolved',
  LIFECYCLE_REJECTED: 'lifecycle_rejected',
  ADMIN_COMMENT: 'admin_comment',
};

/**
 * Each `body` is a function that receives `{ incidentType, agency, publicReportId }`
 * so every notification card surfaces the exact report it refers to (instead of the
 * generic "Report submitted successfully." copy the inbox previously showed for
 * every entry).
 */
const KIND_COPY = {
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_SUBMITTED]: {
    category: 'Report lifecycle',
    title: 'Report submitted',
    body: ({ incidentType, publicReportId }) =>
      `Your ${incidentType} report (${publicReportId}) was submitted successfully and is now awaiting review.`,
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_RECEIVED]: {
    category: 'Report lifecycle',
    title: 'Report acknowledged',
    body: ({ incidentType, agency, publicReportId }) =>
      `${agency} has acknowledged your ${incidentType} report (${publicReportId}).`,
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_UNDER_REVIEW]: {
    category: 'Report lifecycle',
    title: 'Report under review',
    body: ({ incidentType, agency, publicReportId }) =>
      `${agency} is now reviewing your ${incidentType} report (${publicReportId}).`,
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_IN_PROGRESS]: {
    category: 'Report lifecycle',
    title: 'Report in progress',
    body: ({ incidentType, agency, publicReportId }) =>
      `${agency} is acting on your ${incidentType} report (${publicReportId}).`,
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_RESOLVED]: {
    category: 'Report lifecycle',
    title: 'Report resolved',
    body: ({ incidentType, agency, publicReportId }) =>
      `${agency} has marked your ${incidentType} report (${publicReportId}) as resolved.`,
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_REJECTED]: {
    category: 'Report lifecycle',
    title: 'Report rejected',
    body: ({ incidentType, agency, publicReportId }) =>
      `${agency} did not accept your ${incidentType} report (${publicReportId}). Tap to see details.`,
  },
  [CITIZEN_NOTIFICATION_KINDS.ADMIN_COMMENT]: {
    category: 'Agency communication',
    title: 'Agency added remarks',
    body: ({ incidentType, agency, publicReportId }) =>
      `${agency} left remarks on your ${incidentType} report (${publicReportId}).`,
  },
};

export function workflowStatusFingerprint(workflowKey) {
  const key = String(workflowKey ?? 'pending');
  if (key === 'resolved') return 'RESOLVED';
  if (key === 'rejected') return 'REJECTED';
  if (key === 'pending') return 'PENDING';
  if (key === 'review') return 'IN_REVIEW';
  if (key === 'in_progress') return 'IN_WORK';
  return 'PENDING';
}

/**
 * Mirrors Android [ReportStatusNotificationSync.transitionKind] for web admin updates.
 * @returns {string|null} [CITIZEN_NOTIFICATION_KINDS] value
 */
export function citizenKindForStatusTransition(prevWorkflowKey, nextWorkflowKey) {
  const prev = workflowStatusFingerprint(prevWorkflowKey);
  const next = workflowStatusFingerprint(nextWorkflowKey);
  if (prev === next) return null;

  if (next === 'REJECTED') return CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_REJECTED;
  if (next === 'RESOLVED') return CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_RESOLVED;

  if (prev === 'PENDING' && next === 'IN_REVIEW') {
    return CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_UNDER_REVIEW;
  }
  if (prev === 'PENDING' && next === 'IN_WORK') {
    return CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_RECEIVED;
  }
  if (prev === 'IN_REVIEW' && next === 'IN_WORK') {
    return CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_IN_PROGRESS;
  }
  if (prev === 'IN_WORK' && next === 'IN_REVIEW') {
    return CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_UNDER_REVIEW;
  }

  if (next === 'IN_WORK') return CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_IN_PROGRESS;
  if (next === 'IN_REVIEW') return CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_UNDER_REVIEW;

  return null;
}

/**
 * Writes to `users/{uid}/citizenInbox` (same schema as Android CitizenNotificationsRepository).
 *
 * @param {object}  args
 * @param {string}  args.userId         Auth UID of the citizen who owns the report.
 * @param {string}  args.kind           One of [CITIZEN_NOTIFICATION_KINDS].
 * @param {string}  [args.agency]       Agency short label (e.g. "DENR").
 * @param {string}  [args.reportId]     Firestore docId of the report.
 * @param {string}  [args.incidentType] Human-readable incident type ("Illegal Gambling").
 * @param {string}  [args.publicReportId] Public reference shown in the UI ("REP-…").
 * @param {string|null} [args.customBody] Override the localized body — used for admin remarks
 *                                        so the citizen sees the actual remark verbatim.
 */
export async function writeCitizenNotification({
  userId,
  kind,
  agency = 'Agency',
  reportId = '',
  incidentType = '',
  publicReportId = '',
  customBody = null,
}) {
  const uid = String(userId ?? '').trim();
  if (!uid) return;

  const meta = KIND_COPY[kind];
  if (!meta) return;

  const db = getFirestoreDb();
  if (!db) {
    throw new Error('Firestore is not available.');
  }

  const ag = String(agency ?? '').trim() || 'Agency';
  const incident = String(incidentType ?? '').trim() || 'incident';
  const publicId = String(publicReportId ?? '').trim() || 'your report';

  const trimmedCustom = customBody != null ? String(customBody).trim() : '';
  let body = trimmedCustom;
  if (!body) {
    body =
      typeof meta.body === 'function'
        ? meta.body({ incidentType: incident, agency: ag, publicReportId: publicId })
        : String(meta.body ?? '');
  }

  const payload = {
    kind,
    categoryLine: `${meta.category} • ${ag}`,
    title: meta.title,
    body: body.slice(0, 2000),
    agency: ag,
    incidentType: incident,
    publicReportId: publicId,
    reportId: String(reportId ?? '').trim(),
    read: false,
    createdAt: serverTimestamp(),
  };

  await addDoc(collection(db, 'users', uid, 'citizenInbox'), payload);
}
