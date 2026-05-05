/** Values stored in Firestore `agencyNotifications.kind` */

export const AGENCY_NOTIFICATION_KINDS = {
  NEW_REPORT: 'new_report',
  STATUS_CHANGED: 'status_changed',
  REASSIGNED: 'reassigned',
  AI_CLASSIFIED: 'ai_classified',
  AI_LOW_CONFIDENCE: 'ai_low_confidence',
  AI_OVERRIDE: 'ai_override',
  SLA_WARNING: 'sla_warning',
  SLA_ESCALATION: 'sla_escalation',
  CRITICAL_INCIDENT: 'critical_incident',
  SYSTEM_ACCESS: 'system_access',
  /** Legacy mobile / dashboard demo */
  CITIZEN_NOTIFY: 'citizen_notify',
};

export const AGENCY_NOTIFICATION_SEVERITY = {
  INFO: 'info',
  WARNING: 'warning',
  CRITICAL: 'critical',
};
