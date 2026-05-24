import { parseAssignedAgencies } from './agencyScope';
import { reportDateIso, statusToLabel } from './normalizeReportDoc';

export const FILTER_ALL_TYPES = 'All Types';
export const FILTER_ALL_STATUS = 'All Status';
export const FILTER_ALL_AGENCIES = 'All Agencies';

export const DEFAULT_DASHBOARD_FILTERS = {
  type: FILTER_ALL_TYPES,
  status: FILTER_ALL_STATUS,
  agency: FILTER_ALL_AGENCIES,
  date: '',
};

export function buildTypeFilterOptions(reports) {
  const types = new Set();
  reports.forEach((r) => {
    const t = String(r.activity ?? '').trim();
    if (t) types.add(t);
  });
  return [FILTER_ALL_TYPES, ...Array.from(types).sort((a, b) => a.localeCompare(b))];
}

export function buildAgencyFilterOptions(reports, viewerAgencyKey) {
  const set = new Set();
  if (viewerAgencyKey) set.add(viewerAgencyKey);
  reports.forEach((r) => {
    parseAssignedAgencies(r.assignedAgency).forEach((a) => set.add(a));
  });
  const agencies = Array.from(set).sort((a, b) => a.localeCompare(b));
  if (agencies.length <= 1) return agencies;
  return [FILTER_ALL_AGENCIES, ...agencies];
}

export function filterDashboardReports(reports, filters) {
  const type = filters.type ?? FILTER_ALL_TYPES;
  const status = filters.status ?? FILTER_ALL_STATUS;
  const agency = filters.agency ?? FILTER_ALL_AGENCIES;
  const date = filters.date ?? '';

  return reports.filter((r) => {
    if (type !== FILTER_ALL_TYPES && r.activity !== type) return false;

    if (status !== FILTER_ALL_STATUS && statusToLabel(r.status) !== status) return false;

    if (agency !== FILTER_ALL_AGENCIES) {
      const assigned = parseAssignedAgencies(r.assignedAgency);
      if (!assigned.includes(agency)) return false;
    }

    if (date) {
      const iso = reportDateIso(r.createdAt);
      if (!iso || iso !== date) return false;
    }

    return true;
  });
}

/** Resolved or rejected — eligible for report history / archive. */
export function isClosedReportStatus(status) {
  return status === 'resolved' || status === 'rejected';
}

export function filterClosedReports(reports) {
  return reports.filter((r) => isClosedReportStatus(r.status));
}

export function historyArchiveCountsFromReports(reports) {
  return reports.reduce(
    (acc, r) => {
      acc.total += 1;
      if (r.status === 'resolved') acc.resolved += 1;
      else if (r.status === 'rejected') acc.rejected += 1;
      return acc;
    },
    { total: 0, resolved: 0, rejected: 0 }
  );
}

/** Filter + sort for history / archive views. */
export function filterAndSortReports(reports, filters, sortOrder = 'newest') {
  const filtered = filterDashboardReports(reports, filters);
  return [...filtered].sort((a, b) => {
    const ta = a.createdAt instanceof Date ? a.createdAt.getTime() : 0;
    const tb = b.createdAt instanceof Date ? b.createdAt.getTime() : 0;
    return sortOrder === 'oldest' ? ta - tb : tb - ta;
  });
}

export function dashboardCountsFromReports(reports) {
  return reports.reduce(
    (acc, r) => {
      acc.total += 1;
      if (r.status === 'pending') acc.pending += 1;
      else if (r.status === 'review' || r.status === 'in_progress') acc.review += 1;
      else if (r.status === 'resolved') acc.resolved += 1;
      else if (r.status === 'rejected') acc.rejected += 1;
      return acc;
    },
    { total: 0, pending: 0, review: 0, resolved: 0, rejected: 0 }
  );
}
