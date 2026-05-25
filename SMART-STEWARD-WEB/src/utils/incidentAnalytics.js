import { toJsDate } from './normalizeReportDoc';

export const CHART_PALETTE = [
  '#ef4444',
  '#f97316',
  '#22c55e',
  '#3b82f6',
  '#8b5cf6',
  '#14b8a6',
  '#9ca3af',
];

export const STATUS_CHART_COLORS = {
  pending: '#6b7280',
  in_progress: '#eab308',
  resolved: '#22c55e',
  rejected: '#ef4444',
};

/**
 * Donut colors for the AI-assigned severity. They match the pill colors used
 * by `<SeverityBadge>` (see `.severity-badge__pill--*` in `index.css`) so the
 * chart, legend, and detail badges read as the same visual language.
 */
export const SEVERITY_CHART_COLORS = {
  low: '#22c55e',
  medium: '#f97316',
  high: '#ef4444',
  critical: '#7f1d1d',
};

export const TREND_RANGE_OPTIONS = [
  { days: 7, label: 'This week' },
  { days: 14, label: 'Last 14 days' },
  { days: 30, label: 'Last 30 days' },
];

function startOfDay(d) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

/** Inclusive calendar-day window ending today. */
export function rangeBounds(rangeDays) {
  const end = startOfDay(new Date());
  end.setHours(23, 59, 59, 999);
  const start = startOfDay(new Date());
  start.setDate(start.getDate() - (Math.max(1, rangeDays) - 1));
  return { start, end };
}

export function inReportRange(createdAt, rangeDays) {
  if (!(createdAt instanceof Date) || Number.isNaN(createdAt.getTime())) return false;
  const { start, end } = rangeBounds(rangeDays);
  return createdAt >= start && createdAt <= end;
}

export function getAnalyticsRangeMeta(rangeDays) {
  const days = Math.max(1, rangeDays);
  const option =
    TREND_RANGE_OPTIONS.find((o) => o.days === days) ?? {
      days,
      label: `Last ${days} days`,
    };
  return {
    days,
    label: option.label,
    periodSubtitle: `Reports submitted · ${option.label.toLowerCase()}`,
    trendSubtitle: `${option.label} · patterns by incident type`,
    emptyInRange: `No reports were submitted in this period (${option.label.toLowerCase()}). Try a longer range or check back later.`,
  };
}

function monthBounds() {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999);
  return { start, end };
}

export function normalizeActivityLabel(activity) {
  const s = String(activity ?? '').trim();
  return s || 'Unspecified';
}

/** Shorter location line for insight cards (drops plus codes when possible). */
export function formatInsightLocation(location) {
  const raw = String(location ?? '').trim();
  if (!raw) return '—';
  const parts = raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const withoutPlusCode = parts.filter((p) => !/^[A-Z0-9]{4,}\+[A-Z0-9]{2,}$/i.test(p));
  const use = withoutPlusCode.length > 0 ? withoutPlusCode : parts;
  if (use.length >= 2) {
    return `${use[0]}, ${use[use.length - 1]}`;
  }
  return use[0] ?? raw;
}

function activitySlug(activity) {
  return normalizeActivityLabel(activity)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_|_$/g, '')
    .slice(0, 40) || 'unspecified';
}

function finalizePercents(counts) {
  const total = counts.reduce((a, b) => a + b, 0);
  if (total === 0) return counts.map(() => 0);
  const rounded = counts.map((v) => Math.round((v / total) * 100));
  let drift = 100 - rounded.reduce((a, b) => a + b, 0);
  if (drift !== 0) {
    let idx = rounded.indexOf(Math.max(...rounded));
    if (idx < 0) idx = 0;
    rounded[idx] += drift;
  }
  return rounded;
}

function colorForIndex(i) {
  return CHART_PALETTE[i % CHART_PALETTE.length];
}

/**
 * KPI row for analytics header.
 */
export function buildAnalyticsSummaryStats(reports, counts) {
  const { start: monthStart } = monthBounds();
  let newThisMonth = 0;
  let resolvedMonth = 0;

  for (const r of reports) {
    const created = r.createdAt instanceof Date ? r.createdAt : null;
    if (created && created >= monthStart) {
      newThisMonth += 1;
    }
    if (r.status === 'resolved') {
      const updated = toJsDate(r.raw?.statusUpdatedAt) ?? created;
      if (updated instanceof Date && updated >= monthStart) {
        resolvedMonth += 1;
      }
    }
  }

  return {
    total: counts.total,
    pending: counts.pending,
    review: counts.review,
    resolvedMonth,
    newMonth: newThisMonth,
  };
}

/**
 * Donut segments by workflow status for reports in the selected period.
 */
export function buildStatusBreakdown(reports, rangeDays = 30) {
  let pending = 0;
  let inProgress = 0;
  let resolved = 0;
  let rejected = 0;

  for (const r of reports) {
    if (!inReportRange(r.createdAt, rangeDays)) continue;
    if (r.status === 'pending') pending += 1;
    else if (r.status === 'review' || r.status === 'in_progress') inProgress += 1;
    else if (r.status === 'resolved') resolved += 1;
    else if (r.status === 'rejected') rejected += 1;
  }

  const segments = [
    { key: 'pending', name: 'Pending', value: pending, color: STATUS_CHART_COLORS.pending },
    {
      key: 'in_progress',
      name: 'In progress',
      value: inProgress,
      color: STATUS_CHART_COLORS.in_progress,
    },
    { key: 'resolved', name: 'Resolved', value: resolved, color: STATUS_CHART_COLORS.resolved },
    { key: 'rejected', name: 'Rejected', value: rejected, color: STATUS_CHART_COLORS.rejected },
  ];

  const percents = finalizePercents(segments.map((s) => s.value));
  return segments.map((s, i) => ({ ...s, percent: percents[i] }));
}

/**
 * Bar segments by AI-assigned severity for reports in the selected period.
 *
 * Only the four real severity tiers (Critical → High → Medium → Low) are
 * returned. Reports without an AI-assigned severity are intentionally
 * excluded so percentages reflect the *severity profile of rated reports*
 * — i.e. "of the reports the AI has classified, how serious are they?"
 * — instead of mixing in a meta-state like "pending classification" that
 * isn't itself a severity level.
 *
 * Severity values come straight from the AI response (`severity` field in
 * Firestore, normalized by `normalizeIncidentSeverityKey`).
 */
export function buildSeverityBreakdown(reports, rangeDays = 30) {
  let low = 0;
  let medium = 0;
  let high = 0;
  let critical = 0;

  for (const r of reports) {
    if (!inReportRange(r.createdAt, rangeDays)) continue;
    const key = String(r.incidentSeverityKey ?? '').toLowerCase();
    if (key === 'low') low += 1;
    else if (key === 'medium') medium += 1;
    else if (key === 'high') high += 1;
    else if (key === 'critical') critical += 1;
  }

  // Priority-first ordering: the chart reads left-to-right from "look at this
  // first" (Critical) down to "informational" (Low).
  const segments = [
    {
      key: 'critical',
      name: 'Critical',
      value: critical,
      color: SEVERITY_CHART_COLORS.critical,
    },
    { key: 'high', name: 'High', value: high, color: SEVERITY_CHART_COLORS.high },
    { key: 'medium', name: 'Medium', value: medium, color: SEVERITY_CHART_COLORS.medium },
    { key: 'low', name: 'Low', value: low, color: SEVERITY_CHART_COLORS.low },
  ];

  const percents = finalizePercents(segments.map((s) => s.value));
  return segments.map((s, i) => ({ ...s, percent: percents[i] }));
}

/**
 * Donut segments by actual incident type labels for the selected period.
 */
export function buildIncidentTypeSegments(reports, rangeDays = 30, limit = 6) {
  const counts = new Map();

  for (const r of reports) {
    if (!inReportRange(r.createdAt, rangeDays)) continue;
    const label = normalizeActivityLabel(r.activity);
    counts.set(label, (counts.get(label) || 0) + 1);
  }

  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const top = sorted.slice(0, limit);
  const rest = sorted.slice(limit);
  const otherSum = rest.reduce((a, [, n]) => a + n, 0);

  const entries = [...top];
  if (otherSum > 0) {
    entries.push(['Other', otherSum]);
  }

  const segments = entries.map(([name, value], i) => ({
    key: activitySlug(name),
    name,
    value,
    color: colorForIndex(i),
  }));

  const percents = finalizePercents(segments.map((s) => s.value));
  return segments.map((s, i) => ({ ...s, percent: percents[i] }));
}

/** @deprecated Use buildIncidentTypeSegments */
export function buildMonthlyTypeBreakdown(reports) {
  return buildIncidentTypeSegments(reports);
}

function topTypesInRange(reports, rangeDays, limit) {
  const { start, end } = rangeBounds(rangeDays);

  const counts = new Map();
  for (const r of reports) {
    const dt = r.createdAt instanceof Date ? r.createdAt : null;
    if (!dt || dt < start || dt > end) continue;
    const label = normalizeActivityLabel(r.activity);
    counts.set(label, (counts.get(label) || 0) + 1);
  }

  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const top = sorted.slice(0, limit);
  const rest = sorted.slice(limit);
  const otherSum = rest.reduce((a, [, n]) => a + n, 0);

  const types = top.map(([name], i) => ({
    key: activitySlug(name),
    name,
    color: colorForIndex(i),
  }));

  if (otherSum > 0) {
    types.push({ key: 'other_types', name: 'Other', color: colorForIndex(types.length) });
  }

  if (types.length === 0) {
    types.push({ key: 'all_reports', name: 'All reports', color: CHART_PALETTE[0] });
  }

  return { types, start, end };
}

/**
 * Daily volume by top incident types in range.
 * @returns {{ data: Array<Record<string, unknown>>, series: Array<{ key: string, name: string, color: string }> }}
 */
export function buildVolumeTrendSeries(reports, rangeDays, typeLimit = 4) {
  const { types, start, end } = topTypesInRange(reports, rangeDays, typeLimit);
  const labelToKey = new Map(
    types.map((t) => [t.name, t.key]).filter(([name]) => name !== 'Other' && name !== 'All reports')
  );

  const rows = [];
  for (let t = start.getTime(); t <= end.getTime(); t += 86400000) {
    const date = new Date(t);
    const row = {
      label: date.toLocaleDateString('en-US', { weekday: 'short' }),
      dateLabel: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    };
    for (const s of types) {
      row[s.key] = 0;
    }
    rows.push(row);
  }

  const keyToRow = new Map();
  rows.forEach((row, i) => {
    const dayStart = startOfDay(new Date(start.getTime() + i * 86400000));
    keyToRow.set(dayStart.getTime(), row);
  });

  const useSingleSeries = types.length === 1 && types[0].key === 'all_reports';

  for (const r of reports) {
    const dt = r.createdAt instanceof Date ? r.createdAt : null;
    if (!dt) continue;
    const dayKey = startOfDay(dt).getTime();
    const row = keyToRow.get(dayKey);
    if (!row) continue;

    if (useSingleSeries) {
      row.all_reports += 1;
      continue;
    }

    const label = normalizeActivityLabel(r.activity);
    let key = labelToKey.get(label);
    if (!key) key = 'other_types';
    if (row[key] != null) row[key] += 1;
  }

  return { data: rows, series: types };
}

export function countReportsInRange(reports, rangeDays) {
  let n = 0;
  for (const r of reports) {
    if (inReportRange(r.createdAt, rangeDays)) n += 1;
  }
  return n;
}

/**
 * Top locations by report count in the selected period (frequent hotspots).
 */
export function buildTopLocations(reports, rangeDays = 30, limit = 6) {
  const counts = new Map();
  for (const r of reports) {
    if (!inReportRange(r.createdAt, rangeDays)) continue;
    const loc = String(r.location ?? '').trim() || 'Location not specified';
    counts.set(loc, (counts.get(loc) || 0) + 1);
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([location, count]) => ({ location, count }));
}

/**
 * Short insight lines for strategy planning (selected period).
 */
export function buildAnalyticsInsights(reports, rangeDays = 30) {
  const total = countReportsInRange(reports, rangeDays);
  if (total === 0) return null;

  const typeSegments = buildIncidentTypeSegments(reports, rangeDays, 1);
  const topType = typeSegments.find((s) => s.value > 0);
  const topLocation = buildTopLocations(reports, rangeDays, 1)[0];
  const statusSegments = buildStatusBreakdown(reports, rangeDays);
  const pending =
    statusSegments.find((s) => s.key === 'pending')?.value ?? 0;

  return {
    total,
    topActivity: topType?.name ?? '—',
    topActivityShare: topType?.percent ?? 0,
    topLocation: topLocation?.location ?? '—',
    topLocationCount: topLocation?.count ?? 0,
    pending,
  };
}

/**
 * Most recent reports for analytics sidebar.
 */
export function buildRecentAnalyticsReports(reports, limit = 8) {
  return [...reports]
    .sort((a, b) => {
      const ta = a.createdAt instanceof Date ? a.createdAt.getTime() : 0;
      const tb = b.createdAt instanceof Date ? b.createdAt.getTime() : 0;
      return tb - ta;
    })
    .slice(0, limit);
}
