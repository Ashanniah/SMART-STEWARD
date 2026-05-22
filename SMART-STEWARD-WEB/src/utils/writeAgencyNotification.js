import { addDoc, collection, serverTimestamp } from 'firebase/firestore';
import { getFirestoreDb } from '../firebase/config';
import { AGENCY_NOTIFICATIONS_COLLECTION } from '../constants/agencyNotificationsCollection';

/**
 * Creates an agency inbox notification (web bell).
 * @param {object} params
 * @param {string} params.targetAgency Canonical agency key (BFP, DENR, …)
 * @param {string} params.title
 * @param {string} params.body
 * @param {string} params.kind See agencyNotificationKinds
 * @param {string} [params.reportDocId]
 * @param {'info'|'warning'|'critical'} [params.severity]
 * @param {boolean} [params.pinned]
 * @param {number|null} [params.confidence] AI confidence 0–100
 * @param {Record<string, unknown>} [params.extra] Additional serializable fields
 */
export async function writeAgencyNotification({
  targetAgency,
  title,
  body,
  kind,
  reportDocId = '',
  severity = 'info',
  pinned = false,
  confidence = null,
  extra = {},
}) {
  const db = getFirestoreDb();
  if (!db) {
    throw new Error('Firestore is not available.');
  }
  const payload = {
    targetAgency: String(targetAgency ?? '').trim(),
    title: String(title ?? '').slice(0, 500),
    body: String(body ?? '').slice(0, 2000),
    kind: String(kind ?? 'info'),
    severity,
    pinned: Boolean(pinned),
    createdAt: serverTimestamp(),
  };
  if (reportDocId) payload.reportDocId = String(reportDocId);
  if (confidence != null && typeof confidence === 'number' && !Number.isNaN(confidence)) {
    payload.confidence = Math.round(confidence);
  }
  Object.entries(extra).forEach(([k, v]) => {
    if (v !== undefined && v !== null && ['string', 'number', 'boolean'].includes(typeof v)) {
      payload[k] = v;
    }
  });
  return addDoc(collection(db, AGENCY_NOTIFICATIONS_COLLECTION), payload);
}
