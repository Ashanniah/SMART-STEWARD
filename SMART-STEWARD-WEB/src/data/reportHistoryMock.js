/** All Reports History — totals, trends, and repeatable row templates */

import { REPORTS_LIST } from './reportsMock';

export const HISTORY_TOTAL_COUNT = 1248;

export const HISTORY_STATS = {
  total: { value: '1,248', hint: 'All reports submitted', trend: '+18.6%', trendHint: 'from last month' },
  pending: { value: '356', hint: 'Awaiting initial review', trend: '+12.4%', trendHint: 'from last month' },
  review: { value: '278', hint: 'Currently being reviewed', trend: '+8.7%', trendHint: 'from last month' },
  resolved: { value: '614', hint: 'Successfully resolved', trend: '+22.1%', trendHint: 'from last month' },
};

const UNSPLASH = {
  dump: 'https://images.unsplash.com/photo-1530587191325-3db325581d71?w=120&h=120&fit=crop&q=80',
  fire: 'https://images.unsplash.com/photo-1476234251651-3533a066bd4b?w=120&h=120&fit=crop&q=80',
  trees: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=120&h=120&fit=crop&q=80',
  water: 'https://images.unsplash.com/photo-1432405972618-c60b0225b8f9?w=120&h=120&fit=crop&q=80',
  waste: 'https://images.unsplash.com/photo-1611287157826-4e513e77ba9a?w=120&h=120&fit=crop&q=80',
};

/** Cycle through templates for any row index in the virtual 1,248 list */
export const HISTORY_ROW_TEMPLATES = [
  {
    thumb: UNSPLASH.dump,
    typeTitle: 'Illegal Dumping',
    location: 'Brgy. San Isidro, Quezon City',
    dateTime: 'May 20, 2025 10:30 AM',
    reportedBy: 'Juan Dela Cruz',
    status: 'pending',
    priority: 'high',
  },
  {
    thumb: UNSPLASH.fire,
    typeTitle: 'Open Burning',
    location: 'Brgy. Payatas, Quezon City',
    dateTime: 'May 20, 2025 9:15 AM',
    reportedBy: 'Anonymous',
    status: 'review',
    priority: 'high',
  },
  {
    thumb: UNSPLASH.trees,
    typeTitle: 'Tree Cutting',
    location: 'Brgy. Project 8, Quezon City',
    dateTime: 'May 19, 2025 4:45 PM',
    reportedBy: 'Maria Santos',
    status: 'in_progress',
    priority: 'medium',
  },
  {
    thumb: UNSPLASH.water,
    typeTitle: 'Water Pollution',
    location: 'Brgy. Talamban, Cebu City',
    dateTime: 'May 19, 2025 2:00 PM',
    reportedBy: 'Anonymous',
    status: 'resolved',
    priority: 'low',
  },
  {
    thumb: UNSPLASH.waste,
    typeTitle: 'Solid Waste',
    location: 'Mandaue City',
    dateTime: 'May 18, 2025 11:20 AM',
    reportedBy: 'Pedro Reyes',
    status: 'pending',
    priority: 'medium',
  },
  {
    thumb: UNSPLASH.fire,
    typeTitle: 'Open Burning',
    location: 'Toledo City',
    dateTime: 'May 18, 2025 8:00 AM',
    reportedBy: 'Anonymous',
    status: 'review',
    priority: 'low',
  },
  {
    thumb: UNSPLASH.dump,
    typeTitle: 'Illegal Dumping',
    location: 'Lapu-Lapu City',
    dateTime: 'May 17, 2025 3:30 PM',
    reportedBy: 'Ana Cruz',
    status: 'resolved',
    priority: 'high',
  },
  {
    thumb: UNSPLASH.trees,
    typeTitle: 'Tree Cutting',
    location: 'Cordova',
    dateTime: 'May 17, 2025 10:00 AM',
    reportedBy: 'Luis Gomez',
    status: 'in_progress',
    priority: 'medium',
  },
];

const CATEGORY_LABEL = 'Environment';
const AGENCY = 'DENR';

function idForIndex(globalIndex) {
  return `RPT-2025-${String(Math.max(1, HISTORY_TOTAL_COUNT - globalIndex)).padStart(4, '0')}`;
}

function rowFromGlobalIndex(globalIndex) {
  const t = HISTORY_ROW_TEMPLATES[globalIndex % HISTORY_ROW_TEMPLATES.length];
  const base =
    globalIndex < REPORTS_LIST.length ? REPORTS_LIST[globalIndex] : null;

  if (base) {
    return {
      id: base.id,
      thumb: t.thumb,
      typeTitle: base.activity,
      location: base.location,
      dateTime: base.date,
      reportedBy: t.reportedBy,
      status: base.status === 'review' ? 'review' : base.status,
      priority: t.priority,
      categoryLabel: CATEGORY_LABEL,
      agency: AGENCY,
    };
  }

  return {
    ...t,
    id: idForIndex(globalIndex),
    categoryLabel: CATEGORY_LABEL,
    agency: AGENCY,
  };
}

/**
 * @param {number} page 1-based
 * @param {number} pageSize
 */
export function getHistoryPageRows(page, pageSize) {
  const start = (page - 1) * pageSize;
  const rows = [];
  for (let i = 0; i < pageSize; i += 1) {
    const globalIndex = start + i;
    if (globalIndex >= HISTORY_TOTAL_COUNT) break;
    rows.push(rowFromGlobalIndex(globalIndex));
  }
  return rows;
}

export function getPaginationRange(current, total, delta = 2) {
  if (total <= 0) return [];
  const pages = new Set([1, total]);
  for (let i = current - delta; i <= current + delta; i += 1) {
    if (i >= 1 && i <= total) pages.add(i);
  }
  const sorted = [...pages].sort((a, b) => a - b);
  const out = [];
  let prev = 0;
  for (const p of sorted) {
    if (prev && p - prev > 1) out.push('ellipsis');
    out.push(p);
    prev = p;
  }
  return out;
}
