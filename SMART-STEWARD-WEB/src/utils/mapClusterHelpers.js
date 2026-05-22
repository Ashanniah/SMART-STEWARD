/**
 * Helpers for Google Maps marker clustering (dashboard).
 */

export function buildClusterSvgString(count, accentHex) {
  const n = Math.min(999, Math.max(1, Number(count) || 1));
  const text = n > 99 ? '99+' : String(n);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="60" height="60" viewBox="0 0 60 60">
  <circle cx="30" cy="30" r="25" fill="${accentHex}" fill-opacity="0.34"/>
  <circle cx="30" cy="30" r="16" fill="${accentHex}" stroke="#ffffff" stroke-width="2.5"/>
  <text x="30" y="30" text-anchor="middle" dominant-baseline="central" fill="#ffffff" font-size="13" font-weight="700" font-family="system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif">${text}</text>
</svg>`;
}

export function clusterIconDataUrl(count, accentHex) {
  const svg = buildClusterSvgString(count, accentHex);
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

export function aggregateReportCounts(incidents) {
  const out = { total: incidents.length, pending: 0, review: 0, resolved: 0, rejected: 0 };
  for (const inc of incidents) {
    const s = inc.markerStatus ?? inc.status ?? 'pending';
    if (s === 'review' || s === 'in_progress') out.review += 1;
    else if (s === 'resolved') out.resolved += 1;
    else if (s === 'rejected') out.rejected += 1;
    else out.pending += 1;
  }
  return out;
}

export function dominantAccentFromIncidents(incidents) {
  if (!incidents.length) return '#6b7280';
  const c = aggregateReportCounts(incidents);
  const ranked = [
    ['pending', '#6b7280'],
    ['review', '#eab308'],
    ['resolved', '#22c55e'],
    ['rejected', '#ef4444'],
  ];
  let bestColor = '#6b7280';
  let best = -1;
  for (const [key, color] of ranked) {
    const v = c[key];
    if (v > best) {
      best = v;
      bestColor = color;
    }
  }
  return bestColor;
}

export function inferClusterHeadline(incidents) {
  const locs = incidents
    .map((i) => String(i.reportSummary?.location || i.location || '').trim())
    .filter(Boolean);
  const raw = locs[0] || 'Reports in this area';
  const cleaned = raw.replace(/^\s*location:\s*/i, '').trim();
  const parts = cleaned.split(',').map((s) => s.trim()).filter(Boolean);
  const brgy = parts.find((p) => /\b(brgy\.?|barangay)\b/i.test(p));
  const headline = brgy || parts[0] || cleaned;
  const sub = brgy
    ? parts.filter((p) => p !== brgy).join(', ')
    : parts.slice(1).join(', ') || '';
  return { headline, sub };
}

export function mostRecentIncident(incidents) {
  const sorted = [...incidents].sort((a, b) => {
    const ta = a.reportSummary?.createdAtMs ?? 0;
    const tb = b.reportSummary?.createdAtMs ?? 0;
    return tb - ta;
  });
  return sorted[0] ?? null;
}

export function statusBadgeLabel(status) {
  if (status === 'review' || status === 'in_progress') return 'IN PROGRESS';
  if (status === 'resolved') return 'RESOLVED';
  if (status === 'rejected') return 'REJECTED';
  return 'PENDING';
}

/** Custom cluster marker (double ring + count) for @googlemaps/markerclusterer */
export class SmartStewardClusterRenderer {
  render(cluster, _stats, _map) {
    const g = window.google.maps;
    const count = cluster.count;
    const position = cluster.position;
    const markers = cluster.markers || [];
    const incidents = markers
      .map((m) => (typeof m.get === 'function' ? m.get('incident') : null))
      .filter(Boolean);
    const accent = dominantAccentFromIncidents(incidents);
    const url = clusterIconDataUrl(count, accent);
    return new g.Marker({
      position,
      icon: {
        url,
        scaledSize: new g.Size(56, 56),
        anchor: new g.Point(28, 28),
      },
      zIndex: Number(g.Marker.MAX_ZINDEX) + count,
      title: `${count} reports in this area`,
    });
  }
}
