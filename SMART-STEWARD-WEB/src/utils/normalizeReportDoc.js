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
  if (['in_progress', 'in-progress', 'investigating', 'progress'].includes(s)) {
    return 'in_progress';
  }
  if (['review', 'under_review', 'under-review', 'reviewing'].includes(s)) {
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
  const unit = (value, singular, plural) => `${value} ${value === 1 ? singular : plural} ago`;
  if (sec < 10) return 'Just now';
  if (sec < 60) return unit(sec, 'sec', 'secs');
  if (sec < 3600) return unit(Math.floor(sec / 60), 'min', 'mins');
  if (sec < 86400) return unit(Math.floor(sec / 3600), 'hour', 'hours');
  return unit(Math.floor(sec / 86400), 'day', 'days');
}

/** Citizen-facing reference, e.g. REP-20260505-WY5 (matches mobile). */
export function formatPublicReportId(docId, submittedAt) {
  const id = String(docId ?? '').trim();
  const date = submittedAt instanceof Date && !Number.isNaN(submittedAt.getTime()) ? submittedAt : new Date();
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const ymd = `${y}${m}${day}`;
  const compact = id.replace(/[^a-zA-Z0-9]/g, '');
  let suffix;
  if (compact.length >= 3) {
    suffix = compact.slice(-3).toUpperCase();
  } else if (id.length >= 3) {
    suffix = id.slice(-3).toUpperCase();
  } else {
    suffix = id.toUpperCase().padEnd(3, 'X');
  }
  return `REP-${ymd}-${suffix}`;
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

  const storedPublic = String(d.publicReportId ?? '').trim();
  const legacyReportId = String(d.reportId ?? d.reportNumber ?? '').trim();
  const displayId =
    storedPublic ||
    (legacyReportId.startsWith('REP-') ? legacyReportId : '') ||
    formatPublicReportId(docId, createdAt);

  const description =
    String(d.description ?? d.details ?? d.notes ?? '').trim() ||
    `Citizen report regarding ${activity} in ${location}.`;

  const imageUrl = String(d.imageUrl ?? d.photoUrl ?? d.mediaUrl ?? d.image ?? '').trim();
  const videoUrl = String(d.videoUrl ?? '').trim();
  const hasVideo =
    d.hasVideo === true ||
    (typeof d.hasVideo === 'string' && d.hasVideo.toLowerCase() === 'true') ||
    videoUrl.length > 0;

  const reportedBy = String(d.reportedBy ?? d.userName ?? d.submitterName ?? 'Citizen').trim();

  /** Must match the viewer's canonical agency (see `agencyScope.js`). Omit on older docs until backfilled. */
  const assignedAgency = String(d.assignedAgency ?? d.agency ?? '').trim();

  const confidence =
    typeof d.confidence === 'number' && !Number.isNaN(d.confidence) ? Math.round(d.confidence) : 88;

  /** Explicit AI score when present (avoid treating default confidence as AI). */
  const aiConfidenceValue =
    typeof d.aiConfidence === 'number' && !Number.isNaN(d.aiConfidence)
      ? Math.round(d.aiConfidence)
      : typeof d.aiClassificationConfidence === 'number' && !Number.isNaN(d.aiClassificationConfidence)
        ? Math.round(d.aiClassificationConfidence)
        : null;

  const incidentSeverity = String(d.incidentSeverity ?? d.incidentPriority ?? '').toLowerCase();

  const aiLabel = String(d.aiLabel ?? d.aiClassification ?? '').trim();

  const priorityRaw = String(d.priority ?? 'medium').toLowerCase();
  const priority = ['high', 'medium', 'low'].includes(priorityRaw) ? priorityRaw : 'medium';

  const numericTail = displayId.replace(/\D/g, '').slice(-5).padStart(5, '0') || docId.slice(-6);

  return {
    docId,
    id: displayId,
    publicReportId: displayId,
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
    videoUrl,
    hasVideo,
    reportedBy,
    assignedAgency,
    confidence,
    aiConfidenceValue,
    aiLabel,
    incidentSeverity,
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
    hasVideo: n.hasVideo,
    videoUrl: n.videoUrl,
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
  review: 'In Progress',
  in_progress: 'In Progress',
  resolved: 'Resolved',
  rejected: 'Rejected',
};

export function statusToLabel(status) {
  return STATUS_LABEL[status] ?? STATUS_LABEL.pending;
}
