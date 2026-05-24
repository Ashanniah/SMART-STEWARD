/**
 * Normalizes workflow status for map pin colors and cluster counts.
 * Keep in sync with dashboard legend and GoogleMap pinFillColor.
 */
export function resolveMapMarkerStatus(status) {
  const s = String(status ?? 'pending').toLowerCase().trim();
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
