import { AGENCY_NOTIFICATION_KINDS } from '../constants/agencyNotificationKinds';
import { formatReportDateOnly, formatReportTimeOnly } from './normalizeReportDoc';

/**
 * Maps stored `kind` to NotificationIcon variant.
 */
export function resolveNotificationVisualKind(kindRaw) {
  const k = String(kindRaw || '').toLowerCase();
  switch (k) {
    case AGENCY_NOTIFICATION_KINDS.NEW_REPORT:
      return 'new_report';
    case AGENCY_NOTIFICATION_KINDS.CITIZEN_NOTIFY:
      return 'citizen_urgent';
    case AGENCY_NOTIFICATION_KINDS.STATUS_CHANGED:
      return 'status_changed';
    case AGENCY_NOTIFICATION_KINDS.REASSIGNED:
      return 'reassigned';
    case AGENCY_NOTIFICATION_KINDS.AI_CLASSIFIED:
      return 'ai_classified';
    case AGENCY_NOTIFICATION_KINDS.AI_LOW_CONFIDENCE:
      return 'ai_low_confidence';
    case AGENCY_NOTIFICATION_KINDS.AI_OVERRIDE:
      return 'ai_override';
    case AGENCY_NOTIFICATION_KINDS.SLA_WARNING:
      return 'sla_warning';
    case AGENCY_NOTIFICATION_KINDS.SLA_ESCALATION:
      return 'sla_escalation';
    case AGENCY_NOTIFICATION_KINDS.CRITICAL_INCIDENT:
      return 'critical_incident';
    case AGENCY_NOTIFICATION_KINDS.SYSTEM_ACCESS:
      return 'system_access';
    default:
      return 'default';
  }
}

/** Unread indicator — always green (matches mobile). */
export function resolveNotificationDot() {
  return 'green';
}

export const CITIZEN_NOTIFY_DISPLAY_TITLE =
  'A citizen is requesting an update on this report.';

export const NEW_REPORT_DISPLAY_TITLE = 'New citizen report';

/** Normalize stored rows (legacy titles / duplicate location bodies). */
export function resolveNotificationDisplayCopy(notification) {
  const kind = String(notification?.kind ?? '').toLowerCase();
  const title = String(notification?.title ?? '').trim();
  const body = String(notification?.body ?? '').trim();

  if (kind === AGENCY_NOTIFICATION_KINDS.CITIZEN_NOTIFY) {
    return {
      title: CITIZEN_NOTIFY_DISPLAY_TITLE,
      body: '',
    };
  }

  if (kind === AGENCY_NOTIFICATION_KINDS.NEW_REPORT) {
    return {
      title: NEW_REPORT_DISPLAY_TITLE,
      body: '',
    };
  }

  if (kind === AGENCY_NOTIFICATION_KINDS.AI_LOW_CONFIDENCE) {
    return {
      title: title.toLowerCase().includes('confidence')
        ? 'Verify AI classification'
        : title || 'Verify AI classification',
      body: body.includes('%') ? '' : body,
    };
  }

  return { title: title || 'Notification', body };
}

export function findReportForNotification(reportDocId, reports) {
  const id = String(reportDocId ?? '').trim();
  if (!id || !Array.isArray(reports)) return null;
  return (
    reports.find((r) => String(r.docId) === id) ||
    reports.find((r) => String(r.id) === id) ||
    reports.find((r) => String(r.deptReportId ?? '') === id) ||
    null
  );
}

/**
 * Preview lines for the notification dropdown (through Location only).
 * @returns {Array<{ label: string, value: string }>}
 */
export function buildNotificationDetailLines(report) {
  if (!report) return [];
  const reportRef =
    String(report.id ?? report.publicReportId ?? '').trim() || '—';
  return [
    { label: 'Report Type', value: report.activity || '—' },
    { label: 'Report ID', value: reportRef },
    {
      label: 'Date Submitted',
      value: formatReportDateOnly(report.createdAt),
    },
    {
      label: 'Time of Report',
      value: formatReportTimeOnly(report.createdAt),
    },
    { label: 'Location', value: report.location || '—' },
  ];
}

export function mergeAndSortAgencyNotifications(serverItems, syntheticItems) {
  const merged = [...serverItems, ...syntheticItems];
  const rankSev = { critical: 3, warning: 2, info: 1 };
  merged.sort((a, b) => {
    const pa = a.pinned ? 1 : 0;
    const pb = b.pinned ? 1 : 0;
    if (pa !== pb) return pb - pa;
    const ra = rankSev[a.severity] ?? 1;
    const rb = rankSev[b.severity] ?? 1;
    if (ra !== rb) return rb - ra;
    const ta = a.createdAt instanceof Date ? a.createdAt.getTime() : 0;
    const tb = b.createdAt instanceof Date ? b.createdAt.getTime() : 0;
    return tb - ta;
  });
  return merged.slice(0, 50);
}
