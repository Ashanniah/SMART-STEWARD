import { AGENCY_NOTIFICATION_KINDS } from '../constants/agencyNotificationKinds';

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

export function resolveNotificationDot(kindRaw, severity) {
  const k = String(kindRaw || '').toLowerCase();
  const s = String(severity || 'info').toLowerCase();
  if (
    k === AGENCY_NOTIFICATION_KINDS.CRITICAL_INCIDENT ||
    k === AGENCY_NOTIFICATION_KINDS.SLA_ESCALATION ||
    s === 'critical'
  ) {
    return 'red';
  }
  if (
    k === AGENCY_NOTIFICATION_KINDS.SLA_WARNING ||
    k === AGENCY_NOTIFICATION_KINDS.AI_LOW_CONFIDENCE ||
    s === 'warning'
  ) {
    return 'yellow';
  }
  if (k === AGENCY_NOTIFICATION_KINDS.NEW_REPORT || k === AGENCY_NOTIFICATION_KINDS.CITIZEN_NOTIFY) {
    return k === AGENCY_NOTIFICATION_KINDS.CITIZEN_NOTIFY ? 'yellow' : 'blue';
  }
  return 'green';
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
