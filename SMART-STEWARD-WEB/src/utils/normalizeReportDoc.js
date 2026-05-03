/**
 * Maps Firestore `reports` documents to the web dashboard shape.
 * Mobile / backend can use any of the alternate field names below.
 */

const DEFAULT_CENTER = { lat: 10.3547, lng: 123.8986 };
const PLACEHOLDER_MEDIA =
  'https://images.unsplash.com/photo-1448375240586-882707db8887?w=960&q=80&fit=crop';

export function toJsDate(val) {
  if (!val) return null;
  if (typeof val.toDate === 'function') return val.toDate();
  if (val instanceof Date) return val;
  if (typeof val.seconds === 'number') return new Date(val.seconds * 1000);
  return null;
}

function normalizeStatus(raw) {
  const s = String(raw ?? 'pending')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '_');
  if (s === 'pending') return 'pending';
  if (['review', 'under_review', 'under-review', 'in_progress', 'in-progress'].includes(s)) {
    return 'review';
  }
  if (['resolved', 'closed', 'complete', 'completed'].includes(s)) return 'resolved';
  if (['rejected', 'denied', 'invalid'].includes(s)) return 'rejected';
  return 'pending';
}

export function formatReportDateTime(d) {
  if (!d) return '—';
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  });
}

export function formatReportDateTimeHistory(d) {
  if (!d) return '—';
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  });
}

export function formatRelativeTime(d) {
  if (!d) return '—';
  const sec = Math.floor((Date.now() - d.getTime()) / 1000);
  if (sec < 10) return 'Just now';
  if (sec < 60) return `${sec} secs ago`;
  if (sec < 3600) return `${Math.floor(sec / 60)} mins ago`;
  if (sec < 86400) return `${Math.floor(sec / 3600)} hours ago`;
  return `${Math.floor(sec / 86400)} days ago`;
}

/**
 * @returns Normalized report row used across Reports, Dashboard, History, Map, Notifications
 */
export function normalizeReportDocument(docId, data) {
  const d = data && typeof data === 'object' ? data : {};

  const createdAt = toJsDate(d.createdAt ?? d.submittedAt ?? d.timestamp ?? d.date);

  let lat =
    typeof d.lat === 'number'
      ? d.lat
      : typeof d.latitude === 'number'
        ? d.latitude
        : null;
  let lng =
    typeof d.lng === 'number'
      ? d.lng
      : typeof d.longitude === 'number'
        ? d.longitude
        : null;

  if ((lat == null || lng == null) && d.location && typeof d.location === 'object') {
    if (typeof d.location.latitude === 'number') lat = d.location.latitude;
    if (typeof d.location.longitude === 'number') lng = d.location.longitude;
  }

  const location =
    String(d.locationName ?? d.address ?? (typeof d.location === 'string' ? d.location : '') ?? '').trim() ||
    'Location not specified';

  const activity = String(
    d.activity ?? d.incidentType ?? d.title ?? d.category ?? d.type ?? 'Environmental report'
  ).trim();

  const status = normalizeStatus(d.status);

  const displayId = String(d.reportId ?? d.publicReportId ?? d.reportNumber ?? docId);

  const description =
    String(d.description ?? d.details ?? d.notes ?? '').trim() ||
    `Citizen report regarding ${activity} in ${location}.`;

  const imageUrl = String(d.imageUrl ?? d.photoUrl ?? d.mediaUrl ?? d.image ?? '').trim();

  const reportedBy = String(d.reportedBy ?? d.userName ?? d.submitterName ?? 'Citizen').trim();

  const assignedAgency = String(d.assignedAgency ?? d.agency ?? 'DENR').trim();

  const confidence =
    typeof d.confidence === 'number' && !Number.isNaN(d.confidence) ? Math.round(d.confidence) : 88;

  const priorityRaw = String(d.priority ?? 'medium').toLowerCase();
  const priority = ['high', 'medium', 'low'].includes(priorityRaw) ? priorityRaw : 'medium';

  const numericTail = displayId.replace(/\D/g, '').slice(-5).padStart(5, '0') || docId.slice(-6);

  return {
    docId,
    id: displayId,
    date: formatReportDateTime(createdAt),
    dateTime: formatReportDateTimeHistory(createdAt),
    location,
    activity,
    status,
    lat,
    lng,
    createdAt,
    description,
    imageUrl,
    reportedBy,
    assignedAgency,
    confidence,
    priority,
    deptReportId: d.deptReportId ? String(d.deptReportId) : `DEPT – ${createdAt?.getFullYear() ?? new Date().getFullYear()} – ${numericTail}`,
    categoryLabel: String(d.categoryLabel ?? 'Environment'),
    raw: d,
    mapCenter:
      lat != null && lng != null && Number.isFinite(lat) && Number.isFinite(lng)
        ? { lat, lng }
        : DEFAULT_CENTER,
    mapZoom: typeof d.mapZoom === 'number' ? d.mapZoom : 14,
    mediaUrl: imageUrl || PLACEHOLDER_MEDIA,
  };
}

/**
 * Full detail object for Report Detail page (matches prior getReportDetail shape)
 */
export function normalizedToDetailView(n) {
  if (!n) return null;
  return {
    ...n,
    status: n.status,
    deptReportId: n.deptReportId,
    submittedAt: n.date,
    reportTypeLabel: n.activity,
    locationDisplay: n.location,
    description: n.description,
    reportedBy: n.reportedBy,
    assignedAgency: n.assignedAgency,
    confidence: n.confidence,
    mediaUrl: n.mediaUrl,
    mapCenter: n.mapCenter,
    mapZoom: n.mapZoom,
  };
}

export function reportsToMapIncidents(reports) {
  return reports
    .filter((r) => r.lat != null && r.lng != null && Number.isFinite(r.lat) && Number.isFinite(r.lng))
    .map((r) => ({
      id: r.docId,
      lat: r.lat,
      lng: r.lng,
      title: r.activity,
      type: r.activity,
      status: r.status === 'resolved' ? 'resolved' : 'pending',
    }));
}

const STATUS_LABEL = {
  pending: 'Pending',
  review: 'Under Review',
  resolved: 'Resolved',
  rejected: 'Rejected',
};

export function statusToLabel(status) {
  return STATUS_LABEL[status] ?? STATUS_LABEL.pending;
}
