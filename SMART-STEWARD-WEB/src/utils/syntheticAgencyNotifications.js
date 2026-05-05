import { AGENCY_NOTIFICATION_KINDS, AGENCY_NOTIFICATION_SEVERITY } from '../constants/agencyNotificationKinds';

const AI_LOW_THRESHOLD = 70;

/**
 * Ephemeral notifications derived from current report state (no Firestore write).
 * Time-based SLA alerts are omitted — triage is manual; no automatic "pending too long" noise.
 */
export function buildSyntheticAgencyNotifications(reports) {
  /** @type {Array<object>} */
  const out = [];

  for (const r of reports) {
    const docId = r.docId;
    const activity = r.activity || 'Report';
    const loc = r.location || 'Unknown location';
    const st = r.status;
    const open = st === 'pending' || st === 'review' || st === 'in_progress';
    if (!open) continue;

    const aiConf =
      typeof r.aiConfidenceValue === 'number' && !Number.isNaN(r.aiConfidenceValue)
        ? r.aiConfidenceValue
        : null;
    const incidentSev = String(r.incidentSeverity ?? '').toLowerCase();

    if (incidentSev === 'critical') {
      out.push({
        id: `syn-critical-${docId}`,
        synthetic: true,
        title: 'Critical incident',
        body: `${activity} — ${loc}. Review immediately.`,
        kind: AGENCY_NOTIFICATION_KINDS.CRITICAL_INCIDENT,
        severity: AGENCY_NOTIFICATION_SEVERITY.CRITICAL,
        pinned: true,
        reportDocId: docId,
        createdAt: new Date(),
      });
    }

    if (aiConf != null && aiConf < AI_LOW_THRESHOLD) {
      out.push({
        id: `syn-ai-low-${docId}`,
        synthetic: true,
        title: 'Low-confidence AI classification',
        body: `${Math.round(aiConf)}% confidence on "${activity}" — review required (${loc}).`,
        kind: AGENCY_NOTIFICATION_KINDS.AI_LOW_CONFIDENCE,
        severity: AGENCY_NOTIFICATION_SEVERITY.WARNING,
        pinned: false,
        reportDocId: docId,
        confidence: aiConf,
        createdAt: new Date(),
      });
    }
  }

  return out;
}
