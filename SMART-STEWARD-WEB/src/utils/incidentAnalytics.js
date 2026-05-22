/** Keyword buckets for `activity` / `incidentType` (case-insensitive). */

export const TREND_SERIES_COLORS = {
  burning: '#ef4444',
  dumping: '#f97316',
  otherViolations: '#22c55e',
};

export const DONUT_COLORS = {
  burning: '#ef4444',
  dumping: '#f97316',
  logging: '#22c55e',
  other: '#9ca3af',
};

/**
 * @param {string | undefined} activity From normalized report `.activity`
 * @returns {'burning' | 'dumping' | 'logging' | 'other'}
 */
export function categorizeIncidentActivity(activity) {
  const s = String(activity ?? '').toLowerCase();
  if (/\b(burn|burning|smoke|open\s*burn)/.test(s)) return 'burning';
  if (/\b(dump|dumping|illegal\s*dump)/.test(s)) return 'dumping';
  if (/\b(log|logging|quarry|timber|illegal\s*log)/.test(s)) return 'logging';
  return 'other';
}

function startOfDay(d) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

/**
 * Daily counts for line/area chart: burning, dumping, otherViolations (logging + other).
 * @param {Array<{ activity: string, createdAt?: Date | null }>} reports
 * @param {number} rangeDays Number of calendar days ending today (inclusive)
 */
export function buildVolumeTrendSeries(reports, rangeDays) {
  const end = startOfDay(new Date());
  const start = new Date(end);
  start.setDate(start.getDate() - (rangeDays - 1));

  /** @type {Array<{ label: string, dateLabel: string, burning: number, dumping: number, otherViolations: number }>} */
  const rows = [];
  for (let t = start.getTime(); t <= end.getTime(); t += 86400000) {
    const date = new Date(t);
    rows.push({
      label: date.toLocaleDateString('en-US', { weekday: 'short' }),
      dateLabel: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
      burning: 0,
      dumping: 0,
      otherViolations: 0,
    });
  }

  const keyToRow = new Map();
  rows.forEach((row, i) => {
    const dayStart = startOfDay(new Date(start.getTime() + i * 86400000));
    keyToRow.set(dayStart.getTime(), row);
  });

  for (const r of reports) {
    const dt = r.createdAt instanceof Date ? r.createdAt : null;
    if (!dt) continue;
    const dayKey = startOfDay(dt).getTime();
    const row = keyToRow.get(dayKey);
    if (!row) continue;

    const cat = categorizeIncidentActivity(r.activity);
    if (cat === 'burning') row.burning += 1;
    else if (cat === 'dumping') row.dumping += 1;
    else row.otherViolations += 1;
  }

  return rows;
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

/**
 * Donut segments for the current calendar month (local time), by created date.
 */
export function buildMonthlyTypeBreakdown(reports) {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999);

  let burning = 0;
  let dumping = 0;
  let logging = 0;
  let other = 0;

  for (const r of reports) {
    const dt = r.createdAt instanceof Date ? r.createdAt : null;
    if (!dt || dt < start || dt > end) continue;
    const cat = categorizeIncidentActivity(r.activity);
    if (cat === 'burning') burning += 1;
    else if (cat === 'dumping') dumping += 1;
    else if (cat === 'logging') logging += 1;
    else other += 1;
  }

  const segments = [
    { key: 'burning', name: 'Burning', value: burning, color: DONUT_COLORS.burning },
    { key: 'dumping', name: 'Dumping', value: dumping, color: DONUT_COLORS.dumping },
    { key: 'logging', name: 'Logging', value: logging, color: DONUT_COLORS.logging },
    { key: 'other', name: 'Other', value: other, color: DONUT_COLORS.other },
  ];

  const percents = finalizePercents(segments.map((s) => s.value));
  return segments.map((s, i) => ({ ...s, percent: percents[i] }));
}

export const TREND_RANGE_OPTIONS = [
  { days: 7, label: 'This week', subtitle: 'Last 7 days · all incident types' },
  { days: 14, label: 'Last 14 days', subtitle: 'Last 14 days · all incident types' },
  { days: 30, label: 'Last 30 days', subtitle: 'Last 30 days · all incident types' },
];
