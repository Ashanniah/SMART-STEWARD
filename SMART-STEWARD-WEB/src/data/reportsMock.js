/** Shared list + detail resolver for Reports / Report detail views */

export const REPORTS_LIST = [
  {
    id: 'RPT-2025-000128',
    date: 'May 20, 2025, 10:30 AM',
    location: 'Brgy. San Isidro, Cebu City',
    activity: 'Illegal Dumping',
    status: 'pending',
  },
  {
    id: 'RPT-2025-000127',
    date: 'May 19, 2025, 3:15 PM',
    location: 'Talamban, Cebu City',
    activity: 'Open Burning',
    status: 'pending',
  },
  {
    id: 'RPT-2025-000126',
    date: 'May 18, 2025, 9:00 AM',
    location: 'Mandaue City',
    activity: 'Chemical Spill',
    status: 'resolved',
  },
  {
    id: 'RPT-2025-000125',
    date: 'May 17, 2025, 11:45 AM',
    location: 'Toledo City',
    activity: 'Wildlife Violation',
    status: 'pending',
  },
  {
    id: 'RPT-2025-000124',
    date: 'May 16, 2025, 8:20 AM',
    location: 'Lapu-Lapu City',
    activity: 'illegal Quarry',
    status: 'review',
  },
  {
    id: 'RPT-2025-000123',
    date: 'May 15, 2025, 4:00 PM',
    location: 'Busay, Cebu City',
    activity: 'illegal logging',
    status: 'resolved',
  },
  {
    id: 'RPT-2025-000122',
    date: 'May 14, 2025, 1:30 PM',
    location: 'Guadalupe, Cebu City',
    activity: 'Solid Waste',
    status: 'pending',
  },
  {
    id: 'RPT-2025-000121',
    date: 'May 13, 2025, 10:00 AM',
    location: 'Mabolo, Cebu City',
    activity: 'Noise Complaint',
    status: 'review',
  },
  {
    id: 'RPT-2025-000120',
    date: 'May 12, 2025, 5:45 PM',
    location: 'Cordova',
    activity: 'Water Pollution',
    status: 'resolved',
  },
  {
    id: 'RPT-2025-000119',
    date: 'May 11, 2025, 7:15 AM',
    location: 'Consolacion',
    activity: 'Air Quality',
    status: 'pending',
  },
];

const DEFAULT_CENTER = { lat: 10.3547, lng: 123.8986 };

/** Order for status update timeline (IDs match `REPORTS_LIST[].status` + extended values). */
export const WORKFLOW_STATUS_ORDER = [
  'pending',
  'review',
  'in_progress',
  'resolved',
  'rejected',
];

/** Labels, timeline copy, and current-status card copy for the status update panel. */
export const WORKFLOW_STATUS_META = {
  pending: {
    label: 'Pending',
    timelineSub: 'Report submitted by user',
    currentDescription: 'Report is received and waiting for initial review',
  },
  review: {
    label: 'Under Review',
    timelineSub: 'Waiting for agency review',
    currentDescription: 'The report is being reviewed by the assigned agency.',
  },
  in_progress: {
    label: 'In Progress',
    timelineSub: 'Agency is taking action',
    currentDescription: 'Field work or enforcement action is in progress for this report.',
  },
  resolved: {
    label: 'Resolved',
    timelineSub: 'Issue has been resolved',
    currentDescription: 'This report has been closed as resolved.',
  },
  rejected: {
    label: 'Rejected',
    timelineSub: 'Report is invalid or rejected',
    currentDescription: 'This report was rejected or deemed invalid.',
  },
};

export function workflowStatusIndex(status) {
  const i = WORKFLOW_STATUS_ORDER.indexOf(status);
  return i >= 0 ? i : 0;
}

/** Rich overrides — keys match `REPORTS_LIST[].id` */
const DETAIL_EXTRA = {
  'RPT-2025-000127': {
    deptReportId: 'DEPT – 2025 – 11247',
    submittedAt: 'May 25, 2026 10:30 AM',
    reportTypeLabel: 'Open burning',
    locationDisplay: 'Brgy. Sto. Nino, Cebu City',
    description: 'There is an open burning of trash near the vacant lot',
    reportedBy: 'Anonymous',
    assignedAgency: 'DENR',
    confidence: 96,
    mediaUrl:
      'https://images.unsplash.com/photo-1476234251651-3533a066bd4b?w=960&q=80&fit=crop',
    mapCenter: { lat: 10.3547, lng: 123.8986 },
    mapZoom: 15,
  },
};

export function getReportDetail(id) {
  const row = REPORTS_LIST.find((r) => r.id === id);
  if (!row) return null;
  const x = DETAIL_EXTRA[id] || {};

  const numericTail = row.id.replace(/\D/g, '').slice(-5).padStart(5, '0');

  return {
    ...row,
    status: x.status ?? row.status,
    deptReportId: x.deptReportId ?? `DEPT – 2025 – ${numericTail}`,
    submittedAt: x.submittedAt ?? row.date,
    reportTypeLabel: x.reportTypeLabel ?? row.activity,
    locationDisplay: x.locationDisplay ?? row.location,
    description:
      x.description ??
      `Citizen report regarding ${row.activity} in ${row.location}. Field verification recommended.`,
    reportedBy: x.reportedBy ?? 'Anonymous',
    assignedAgency: x.assignedAgency ?? 'DENR',
    confidence: x.confidence ?? 88,
    mediaUrl:
      x.mediaUrl ??
      'https://images.unsplash.com/photo-1448375240586-882707db8887?w=960&q=80&fit=crop',
    mapCenter: x.mapCenter ?? DEFAULT_CENTER,
    mapZoom: x.mapZoom ?? 14,
  };
}
