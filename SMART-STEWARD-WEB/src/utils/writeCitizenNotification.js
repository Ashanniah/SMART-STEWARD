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

const KIND_COPY = {
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_SUBMITTED]: {
    category: 'Report lifecycle',
    title: 'Submitted',
    body: 'Report submitted successfully.',
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_RECEIVED]: {
    category: 'Report lifecycle',
    title: 'Received / Acknowledged',
    body: null, // formatted with agency
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_UNDER_REVIEW]: {
    category: 'Report lifecycle',
    title: 'Under Review',
    body: 'Your report is under review.',
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_IN_PROGRESS]: {
    category: 'Report lifecycle',
    title: 'In Progress',
    body: 'Action is being taken on your report.',
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_RESOLVED]: {
    category: 'Report lifecycle',
    title: 'Resolved',
    body: 'Your report has been resolved.',
  },
  [CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_REJECTED]: {
    category: 'Report lifecycle',
    title: 'Rejected',
    body: 'Your report was not accepted. See details.',
  },
  [CITIZEN_NOTIFICATION_KINDS.ADMIN_COMMENT]: {
    category: 'Admin',
    title: 'Comment / Feedback',
    body: 'An officer responded to your report.',
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
 */
export async function writeCitizenNotification({
  userId,
  kind,
  agency = 'Agency',
  reportId = '',
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
  let body = customBody != null ? String(customBody).trim() : meta.body;
  if (!body) {
    if (kind === CITIZEN_NOTIFICATION_KINDS.LIFECYCLE_RECEIVED) {
      body = `Your report has been received by ${ag}.`;
    } else {
      body = '';
    }
  }

  const payload = {
    kind,
    categoryLine: `${meta.category} • ${ag}`,
    title: meta.title,
    body: body.slice(0, 2000),
    agency: ag,
    reportId: String(reportId ?? '').trim(),
    read: false,
    createdAt: serverTimestamp(),
  };

  await addDoc(collection(db, 'users', uid, 'citizenInbox'), payload);
}
