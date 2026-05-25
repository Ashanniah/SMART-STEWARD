import { isMapMarkerVisible, resolveMapMarkerStatus } from './mapMarkerStatus';

/**
 * Maps Firestore `reports` documents to the web dashboard shape.
 * Mobile / backend can use any of the alternate field names below.
 */

const DEFAULT_CENTER = { lat: 10.3547, lng: 123.8986 };
const PLACEHOLDER_MEDIA =
  'https://images.unsplash.com/photo-1448375240586-882707db8887?w=960&q=80&fit=crop';

/** Below this AI score (0–100), agency UI suggests verifying the classification. */
export const AI_CLASSIFICATION_REVIEW_THRESHOLD = 70;

const INCIDENT_SEVERITY_LABELS = {
  low: 'Low',
  medium: 'Medium',
  high: 'High',
  critical: 'Critical',
};

function parseAiConfidenceFromDoc(d) {
  if (typeof d.aiConfidence === 'number' && !Number.isNaN(d.aiConfidence)) {
    const n = d.aiConfidence;
    return n > 0 && n <= 1 ? Math.round(n * 100) : Math.round(n);
  }
  if (
    typeof d.aiClassificationConfidence === 'number' &&
    !Number.isNaN(d.aiClassificationConfidence)
  ) {
    const n = d.aiClassificationConfidence;
    return n > 0 && n <= 1 ? Math.round(n * 100) : Math.round(n);
  }
  if (typeof d.confidence_score === 'number' && !Number.isNaN(d.confidence_score)) {
    const n = d.confidence_score;
    return n > 0 && n <= 1 ? Math.round(n * 100) : Math.round(n);
  }
  if (typeof d.confidence === 'number' && !Number.isNaN(d.confidence)) {
    const n = d.confidence;
    return n > 0 && n <= 1 ? Math.round(n * 100) : Math.round(n);
  }
  return null;
}

function normalizeIncidentSeverityKey(d) {
  const raw = String(d.severity ?? d.incidentSeverity ?? d.incidentPriority ?? '')
    .trim()
    .toLowerCase();
  if (['low', 'medium', 'high', 'critical'].includes(raw)) return raw;
  return '';
}

export function formatIncidentSeverityLabel(key) {
  const k = String(key ?? '').toLowerCase();
  return INCIDENT_SEVERITY_LABELS[k] ?? '';
}

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

export function formatReportDateOnly(d) {
  if (!d) return '—';
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

export function formatReportTimeOnly(d) {
  if (!d) return '—';
  return d.toLocaleString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  });
}

/** `yyyy-MM-dd` for &lt;input type="date"&gt; comparison */
export function reportDateIso(d) {
  if (!d || Number.isNaN(d.getTime())) return '';
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
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
  const statusUpdatedAt = toJsDate(d.statusUpdatedAt ?? d.statusChangedAt ?? null);

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

  const locationRaw = String(
    d.locationLine ??
      d.locationName ??
      d.address ??
      (typeof d.location === 'string' ? d.location : '') ??
      ''
  ).trim();
  const location = locationRaw.replace(/^location:\s*/i, '').trim() || 'Location not specified';

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

  const reportedByRaw = String(d.reportedBy ?? d.userName ?? d.submitterName ?? '').trim();
  /**
   * Reports stored without an explicit submitter name, or whose stored value
   * is the legacy placeholder "Citizen", are surfaced as "Anonymous" so the
   * dashboard never reveals an individual's identity unless one was provided.
   */
  const reportedBy =
    !reportedByRaw || reportedByRaw.toLowerCase() === 'citizen'
      ? 'Anonymous'
      : reportedByRaw;

  /** Must match the viewer's canonical agency (see `agencyScope.js`). Omit on older docs until backfilled. */
  const assignedAgency = String(d.assignedAgency ?? d.agency ?? '').trim();

  /** Internal only — not shown as a % in agency UI. */
  const aiConfidenceValue = parseAiConfidenceFromDoc(d);

  const incidentSeverityKey = normalizeIncidentSeverityKey(d);
  const incidentSeverity = incidentSeverityKey;
  const incidentSeverityLabel = incidentSeverityKey
    ? formatIncidentSeverityLabel(incidentSeverityKey)
    : '';
  const incidentSeverityReason = String(
    d.severityReason ?? d.severity_reason ?? d.incidentSeverityReason ?? ''
  ).trim();

  const needsAiReview =
    d.needsAiReview === true ||
    (aiConfidenceValue != null && aiConfidenceValue < AI_CLASSIFICATION_REVIEW_THRESHOLD);

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
    statusUpdatedAt,
    statusUpdatedAtMs: statusUpdatedAt instanceof Date ? statusUpdatedAt.getTime() : null,
    description,
    imageUrl,
    videoUrl,
    hasVideo,
    reportedBy,
    userId: String(d.userId ?? d.submitterId ?? '').trim(),
    assignedAgency,
    aiConfidenceValue,
    aiLabel,
    incidentSeverity,
    incidentSeverityKey,
    incidentSeverityLabel,
    incidentSeverityReason,
    needsAiReview,
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
    incidentSeverityKey: n.incidentSeverityKey,
    incidentSeverityLabel: n.incidentSeverityLabel,
    incidentSeverityReason: n.incidentSeverityReason,
    needsAiReview: n.needsAiReview,
    hasVideo: n.hasVideo,
    videoUrl: n.videoUrl,
    mediaUrl: n.mediaUrl,
    mapCenter: n.mapCenter,
    mapZoom: n.mapZoom,
  };
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

/** Summary payload for map floating panel / cluster lists. */
export function buildMapReportSummary(r) {
  return {
    docId: r.docId,
    activity: r.activity,
    location: r.location,
    date: formatReportDateOnly(r.createdAt),
    time: formatReportTimeOnly(r.createdAt),
    displayId: r.id,
    imageUrl: r.imageUrl || '',
    hasVideo: Boolean(r.hasVideo),
    videoUrl: r.videoUrl || '',
    createdAtMs: r.createdAt instanceof Date ? r.createdAt.getTime() : 0,
    assignedAgency: r.assignedAgency || '',
    statusLabel: statusToLabel(r.status),
    statusKey: r.status,
  };
}

/**
 * Builds the map-pin payload for every report that has a usable coordinate.
 *
 * Resolved / rejected reports remain pinned only inside the
 * [TERMINAL_MARKER_TTL_MS] window after the agency's status change so the
 * outcome is briefly visible, then the marker auto-disappears. The original
 * report still lives in the dashboard list, History page, and Firestore.
 *
 * @param reports Source normalized report rows.
 * @param nowMs Optional reference timestamp used for the TTL filter.
 * @param options.ignoreVisibilityTtl When true, every report with valid
 *        coordinates is rendered regardless of status TTL — used by the
 *        Report Detail page so the user always sees their own pin.
 */
export function reportsToMapIncidents(reports, nowMs = Date.now(), options = {}) {
  const { ignoreVisibilityTtl = false } = options;
  return reports
    .filter((r) => r.lat != null && r.lng != null && Number.isFinite(r.lat) && Number.isFinite(r.lng))
    .filter((r) => ignoreVisibilityTtl || isMapMarkerVisible(r, nowMs))
    .map((r) => {
      const markerStatus = resolveMapMarkerStatus(r.status);
      const summary = buildMapReportSummary(r);
      return {
        id: r.docId,
        lat: r.lat,
        lng: r.lng,
        title: r.activity,
        type: r.activity,
        status: markerStatus,
        markerStatus,
        location: r.location,
        statusUpdatedAtMs:
          r.statusUpdatedAtMs ??
          (r.statusUpdatedAt instanceof Date ? r.statusUpdatedAt.getTime() : null),
        reportSummary: {
          ...summary,
          statusKey: markerStatus,
          statusLabel: statusToLabel(r.status),
        },
      };
    });
}
