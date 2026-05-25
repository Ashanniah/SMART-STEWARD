/**
 * Normalizes workflow status for map pin colors and cluster counts.
 * Keep in sync with dashboard legend and GoogleMap pinFillColor.
 */
export function resolveMapMarkerStatus(status) {
  const s = String(status?.status ?? status ?? 'pending').toLowerCase().trim();
  if (s === 'resolved') return 'resolved';
  if (s === 'rejected') return 'rejected';
  if (s === 'review' || s === 'in_progress' || s === 'in-progress' || s === 'under_review') {
    return 'in_progress';
  }
  return 'pending';
}

export function mapIncidentsStatusSignature(incidents) {
  return incidents
    .map((i) => `${i.id}:${resolveMapMarkerStatus(i.markerStatus ?? i.status)}`)
    .sort()
    .join('|');
}

/**
 * Resolved / rejected reports linger on the map for this many milliseconds
 * after the status change so the agency sees the outcome marker briefly,
 * then automatically disappears while the report stays in history.
 */
export const TERMINAL_MARKER_TTL_MS = 60_000;

function toEpochMs(value) {
  if (value == null) return 0;
  if (value instanceof Date) return value.getTime();
  if (typeof value === 'number') return value;
  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  if (typeof value === 'object') {
    if (typeof value.toMillis === 'function') {
      try {
        return value.toMillis();
      } catch {
        return 0;
      }
    }
    if (typeof value.toDate === 'function') {
      try {
        return value.toDate().getTime();
      } catch {
        return 0;
      }
    }
    if (typeof value.seconds === 'number') {
      return value.seconds * 1000;
    }
  }
  return 0;
}

/**
 * Closed reports (resolved / rejected) only stay pinned to the map for a
 * short post-update window so the agency dashboard reflects the final
 * outcome briefly; afterwards the marker disappears but the report itself
 * is preserved in history / the database.
 */
export function isMapMarkerVisible(report, nowMs = Date.now()) {
  const status = resolveMapMarkerStatus(report?.status);
  if (status !== 'resolved' && status !== 'rejected') return true;
  const raw =
    report?.statusUpdatedAtMs ??
    report?.statusUpdatedAt ??
    report?.raw?.statusUpdatedAt ??
    report?.raw?.statusChangedAt ??
    null;
  const updatedMs = toEpochMs(raw);
  if (!updatedMs) return false;
  return nowMs - updatedMs <= TERMINAL_MARKER_TTL_MS;
}

/**
 * Returns the next moment (epoch ms) when the visibility filter changes
 * for at least one report, or `Infinity` if nothing is about to expire.
 */
export function nextMarkerExpiryMs(reports, nowMs = Date.now()) {
  let soonest = Infinity;
  for (const r of reports) {
    const status = resolveMapMarkerStatus(r?.status);
    if (status !== 'resolved' && status !== 'rejected') continue;
    const raw =
      r?.statusUpdatedAtMs ??
      r?.statusUpdatedAt ??
      r?.raw?.statusUpdatedAt ??
      r?.raw?.statusChangedAt ??
      null;
    const updatedMs = toEpochMs(raw);
    if (!updatedMs) continue;
    const expiresAt = updatedMs + TERMINAL_MARKER_TTL_MS;
    if (expiresAt > nowMs && expiresAt < soonest) {
      soonest = expiresAt;
    }
  }
  return soonest;
}
