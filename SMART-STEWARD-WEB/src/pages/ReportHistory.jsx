import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircleIcon,
  XCircleIcon,
  ArrowDownTrayIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ArrowPathIcon,
  ClipboardDocumentListIcon,
  FunnelIcon,
} from '@heroicons/react/24/outline';
import { getPaginationRange } from '../data/reportHistoryMock';
import { useReportsData } from '../context/ReportsDataContext';
import { useAgencyUser } from '../context/AgencyUserContext';
import MediaLightbox from '../components/MediaLightbox';
import {
  buildTypeFilterOptions,
  DEFAULT_DASHBOARD_FILTERS,
  filterAndSortReports,
  filterClosedReports,
  historyArchiveCountsFromReports,
  FILTER_ALL_AGENCIES,
  FILTER_ALL_STATUS,
  FILTER_ALL_TYPES,
} from '../utils/dashboardFilters';
import { viewerScopedAgencyLabel } from '../utils/agencyScope';
import {
  formatReportDateOnly,
  formatReportTimeOnly,
} from '../utils/normalizeReportDoc';
import { nextMarkerExpiryMs, TERMINAL_MARKER_TTL_MS } from '../utils/mapMarkerStatus';

const PLACEHOLDER_THUMB =
  'https://images.unsplash.com/photo-1611287157826-4e513e77ba9a?w=120&h=120&fit=crop&q=80';

const STAT_CONFIG = [
  { key: 'total', title: 'Total Reports', Icon: ClipboardDocumentListIcon, accent: 'green' },
  { key: 'resolved', title: 'Resolved', Icon: CheckCircleIcon, accent: 'teal' },
  { key: 'rejected', title: 'Rejected', Icon: XCircleIcon, accent: 'red' },
];

const STATUS_FILTER_OPTIONS = [FILTER_ALL_STATUS, 'Resolved', 'Rejected'];

const COL_COUNT = 9;

/**
 * Builds the "Expires" cell shown next to each closed report.
 *
 * The Dashboard map only shows resolved / rejected pins for
 * [TERMINAL_MARKER_TTL_MS] after the agency's status change; this helper
 * surfaces the matching deadline (or "Expired" when the window has lapsed)
 * so reviewers know exactly when the marker was removed from the map.
 */
function formatExpiry(statusUpdatedAt, nowMs = Date.now()) {
  if (!(statusUpdatedAt instanceof Date) || Number.isNaN(statusUpdatedAt.getTime())) {
    return { label: '—', sub: '', expired: false };
  }
  const expiresAt = new Date(statusUpdatedAt.getTime() + TERMINAL_MARKER_TTL_MS);
  const label = expiresAt.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  });
  const expired = nowMs > expiresAt.getTime();
  return {
    label,
    sub: expired ? 'Removed from map' : 'Visible on map until',
    expired,
  };
}

function HistoryStatusPill({ status }) {
  const labels = {
    pending: 'Pending',
    review: 'In Progress',
    in_progress: 'In Progress',
    resolved: 'Resolved',
    rejected: 'Rejected',
  };
  const pillStatus =
    status === 'rejected'
      ? 'rejected'
      : status === 'in_progress' || status === 'review'
        ? 'in_progress'
        : status;
  return (
    <span className={`reports-status reports-status--${pillStatus}`}>
      {labels[status] ?? status}
    </span>
  );
}

/**
 * Each agency dashboard is scoped to its own viewer, so even if a report is assigned
 * to several agencies in Firestore we only display the viewer's own agency here.
 */
function ViewerAgencyChip({ raw, viewerAgencyKey }) {
  const label = viewerScopedAgencyLabel(raw, viewerAgencyKey);
  if (!label) return <span className="reports-agency-empty">—</span>;
  return <span className="reports-agency-chip">{label}</span>;
}

function fmtNum(n) {
  return Number(n || 0).toLocaleString('en-US');
}

export default function ReportHistory() {
  const navigate = useNavigate();
  const { viewerAgencyKey } = useAgencyUser();
  const { reports, loading, error } = useReportsData();
  const [mediaPreview, setMediaPreview] = useState({ open: false, type: 'image', src: '' });
  const DEFAULT_FILTERS = useMemo(
    () => ({
      ...DEFAULT_DASHBOARD_FILTERS,
      agency: FILTER_ALL_AGENCIES,
      sort: 'newest',
    }),
    []
  );

  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [actionToast, setActionToast] = useState(null);
  const [nowTick, setNowTick] = useState(() => Date.now());
  const toastTimerRef = useRef(null);
  const tableSectionRef = useRef(null);

  const archiveReports = useMemo(() => filterClosedReports(reports), [reports]);

  const typeOptions = useMemo(() => buildTypeFilterOptions(archiveReports), [archiveReports]);

  const filteredReports = useMemo(
    () => filterAndSortReports(archiveReports, filters, filters.sort),
    [archiveReports, filters]
  );

  const historyCounts = useMemo(
    () => historyArchiveCountsFromReports(filteredReports),
    [filteredReports]
  );

  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (filters.type !== FILTER_ALL_TYPES) count += 1;
    if (filters.status !== FILTER_ALL_STATUS) count += 1;
    if (filters.date) count += 1;
    if (filters.sort && filters.sort !== 'newest') count += 1;
    return count;
  }, [filters]);

  const totalCount = filteredReports.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const effectivePage = Math.min(Math.max(1, page), totalPages);

  useEffect(() => {
    setPage(1);
  }, [filters, pageSize]);

  useEffect(
    () => () => {
      if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    },
    []
  );

  function flashToast(message, variant = 'info') {
    setActionToast({ message, variant });
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    toastTimerRef.current = setTimeout(() => setActionToast(null), 2400);
  }

  const rows = useMemo(() => {
    const start = (effectivePage - 1) * pageSize;
    return filteredReports.slice(start, start + pageSize).map((r) => ({
      docId: r.docId,
      reportId: r.id,
      thumb: r.imageUrl || PLACEHOLDER_THUMB,
      typeTitle: r.activity,
      location: r.location,
      dateSubmitted: formatReportDateOnly(r.createdAt),
      timeOfReport: formatReportTimeOnly(r.createdAt),
      status: r.status,
      assignedAgency: r.assignedAgency,
      expiry: formatExpiry(r.statusUpdatedAt, nowTick),
    }));
  }, [filteredReports, effectivePage, pageSize, nowTick]);

  /**
   * The "Map Marker Expires" column flips from "Visible on map until …" to
   * "Removed from map" the moment a row's 1-minute TTL lapses. We schedule
   * a single timeout for the next pending expiry on the visible page so
   * the label updates without a perpetual 1Hz loop.
   */
  useEffect(() => {
    const pageReports = filteredReports.slice(
      (effectivePage - 1) * pageSize,
      effectivePage * pageSize
    );
    const nextExpiry = nextMarkerExpiryMs(pageReports, nowTick);
    if (!Number.isFinite(nextExpiry)) return undefined;
    const delay = Math.max(250, nextExpiry - Date.now());
    const id = window.setTimeout(() => setNowTick(Date.now()), delay);
    return () => window.clearTimeout(id);
  }, [filteredReports, effectivePage, pageSize, nowTick]);

  const startIdx = (effectivePage - 1) * pageSize;
  const showingFrom = totalCount === 0 ? 0 : startIdx + 1;
  const showingTo = Math.min(startIdx + pageSize, totalCount);

  const pageItems = useMemo(
    () => getPaginationRange(effectivePage, totalPages, 2),
    [effectivePage, totalPages]
  );

  const applyFilters = () => {
    setPage(1);
    tableSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    const matched = filteredReports.length;
    flashToast(
      activeFilterCount === 0
        ? `Showing all ${matched.toLocaleString()} report${matched === 1 ? '' : 's'}.`
        : `${activeFilterCount} filter${activeFilterCount === 1 ? '' : 's'} active · ${matched.toLocaleString()} report${matched === 1 ? '' : 's'} match.`,
      'success'
    );
  };

  const resetFilters = () => {
    setFilters(DEFAULT_FILTERS);
    setPageSize(10);
    flashToast('Filters cleared.', 'info');
  };

  function exportCsv() {
    if (filteredReports.length === 0) {
      flashToast('No reports to export.', 'warning');
      return;
    }
    const headers = [
      'reportId',
      'dateSubmitted',
      'timeOfReport',
      'reportType',
      'location',
      'agency',
      'status',
      'mapMarkerExpiresAt',
    ];
    const escape = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const csvRows = filteredReports.map((r) => {
      const agency =
        viewerScopedAgencyLabel(r.assignedAgency, viewerAgencyKey) || r.assignedAgency;
      const expiry = formatExpiry(r.statusUpdatedAt);
      return {
        reportId: r.id,
        dateSubmitted: formatReportDateOnly(r.createdAt),
        timeOfReport: formatReportTimeOnly(r.createdAt),
        reportType: r.activity,
        location: r.location,
        agency,
        status: r.status,
        mapMarkerExpiresAt: expiry.label,
      };
    });
    const csv = [
      headers.join(','),
      ...csvRows.map((row) => headers.map((h) => escape(row[h])).join(',')),
    ].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const filename = `report-history-${new Date().toISOString().slice(0, 10)}.csv`;
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 0);
    flashToast(
      `Exported ${csvRows.length.toLocaleString()} report${csvRows.length === 1 ? '' : 's'} to ${filename}.`,
      'success'
    );
  }

  return (
    <div className="report-history-page fade-in">
      {error ? (
        <p className="reports-page__banner-msg" role="alert">
          {error}
        </p>
      ) : null}

      <div className="report-history-page__stats">
        {STAT_CONFIG.map(({ key, title, Icon, accent }) => (
          <div key={key} className={`history-stat-card history-stat-card--${accent}`}>
            <span className="history-stat-card__accent-bar" aria-hidden />
            <div className="history-stat-card__icon" aria-hidden>
              <Icon />
            </div>
            <div className="history-stat-card__body">
              <div className="history-stat-card__label">{title}</div>
              <div className="history-stat-card__value">
                {(historyCounts[key] ?? 0).toLocaleString()}
              </div>
            </div>
          </div>
        ))}
      </div>

      <section className="denr-filters report-history-filters">
        <div className="denr-filters__head">
          <h3 className="denr-filters__title">
            <FunnelIcon aria-hidden />
            Quick filters
            {activeFilterCount > 0 ? (
              <span className="denr-filters__count" aria-label={`${activeFilterCount} filters active`}>
                {activeFilterCount}
              </span>
            ) : null}
          </h3>
          <p className="denr-filters__hint">
            Resolved and rejected reports only — filter by type, outcome, or date
          </p>
        </div>
        <div className="denr-filters__row">
          <label className="denr-filter-field">
            <span>By Type</span>
            <select
              value={filters.type}
              onChange={(e) => setFilters((f) => ({ ...f, type: e.target.value }))}
            >
              {typeOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Status</span>
            <select
              value={filters.status}
              onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value }))}
            >
              {STATUS_FILTER_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Date</span>
            <input
              type="date"
              value={filters.date}
              onChange={(e) => setFilters((f) => ({ ...f, date: e.target.value }))}
            />
          </label>
          <label className="denr-filter-field">
            <span>Sort</span>
            <select
              value={filters.sort}
              onChange={(e) => setFilters((f) => ({ ...f, sort: e.target.value }))}
            >
              <option value="newest">Newest first</option>
              <option value="oldest">Oldest first</option>
            </select>
          </label>
          <div className="denr-filters__actions">
            <button type="button" className="denr-btn-apply" onClick={applyFilters}>
              <FunnelIcon aria-hidden />
              Apply Filters
            </button>
            <button
              type="button"
              className="denr-btn-reset"
              onClick={resetFilters}
              disabled={activeFilterCount === 0}
            >
              <ArrowPathIcon aria-hidden />
              Reset
            </button>
            <button
              type="button"
              className="history-btn history-btn--export"
              onClick={exportCsv}
              disabled={filteredReports.length === 0}
            >
              <ArrowDownTrayIcon aria-hidden />
              Export
            </button>
          </div>
        </div>
        {actionToast ? (
          <div
            className={`report-history-toast report-history-toast--${actionToast.variant}`}
            role="status"
            aria-live="polite"
          >
            {actionToast.message}
          </div>
        ) : null}
      </section>

      <section className="history-table-card" ref={tableSectionRef}>
        <div className="history-table-card__head">
          <h2 className="history-table-card__title">
            <ClipboardDocumentListIcon aria-hidden />
            Closed Reports
          </h2>
          <span className="history-table-card__count">
            {fmtNum(totalCount)} {totalCount === 1 ? 'report' : 'reports'}
          </span>
        </div>

        <div className="history-table-wrap">
          <table className="history-table reports-table">
            <thead>
              <tr>
                <th>Media / Report ID</th>
                <th>Report Type</th>
                <th>Date Submitted</th>
                <th>Time of Report</th>
                <th>Location</th>
                <th>Agency</th>
                <th>Status</th>
                <th>Date &amp; Time of the Expiration</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {loading && reports.length === 0 ? (
                <tr>
                  <td colSpan={COL_COUNT} className="reports-table__loading">
                    Loading reports…
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={COL_COUNT} className="reports-table__loading">
                    {archiveReports.length === 0
                      ? 'No closed reports yet. Resolved and rejected reports will appear here.'
                      : 'No reports match your filters.'}
                  </td>
                </tr>
              ) : (
                rows.map((row, idx) => (
                  <tr key={`${row.docId}-${startIdx + idx}`} className="history-table__row">
                    <td>
                      <div className="history-id-cell">
                        <button
                          type="button"
                          className="history-table__thumb-btn"
                          onClick={() =>
                            setMediaPreview({ open: true, type: 'image', src: row.thumb })
                          }
                        >
                          <img src={row.thumb} alt="" className="history-table__thumb" />
                        </button>
                        <span className="history-id-cell__id">{row.reportId}</span>
                      </div>
                    </td>
                    <td className="reports-table__type">{row.typeTitle}</td>
                    <td className="history-table__date">{row.dateSubmitted}</td>
                    <td className="history-table__time">{row.timeOfReport}</td>
                    <td className="reports-table__location">{row.location}</td>
                    <td>
                      <ViewerAgencyChip
                        raw={row.assignedAgency}
                        viewerAgencyKey={viewerAgencyKey}
                      />
                    </td>
                    <td>
                      <HistoryStatusPill status={row.status} />
                    </td>
                    <td className="history-table__expiry">
                      <span
                        className={`history-expiry-cell${row.expiry.expired ? ' history-expiry-cell--expired' : ''}`}
                      >
                        <span className="history-expiry-cell__sub">{row.expiry.sub}</span>
                        <span className="history-expiry-cell__time">{row.expiry.label}</span>
                      </span>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="reports-btn-view"
                        onClick={() =>
                          navigate(`/reports/${encodeURIComponent(row.docId)}`)
                        }
                      >
                        View
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <footer className="history-footer">
          <p className="history-footer__meta">
            Showing {showingFrom} to {showingTo} of {fmtNum(totalCount)} reports
          </p>

          <nav className="history-pagination" aria-label="Pagination">
            <button
              type="button"
              className="history-pagination__arrow"
              disabled={effectivePage <= 1 || totalCount === 0}
              onClick={() => setPage(effectivePage - 1)}
              aria-label="Previous page"
            >
              <ChevronLeftIcon />
            </button>
            {pageItems.map((item, idx) =>
              item === 'ellipsis' ? (
                <span key={`e-${idx}`} className="history-pagination__ellipsis">
                  …
                </span>
              ) : (
                <button
                  key={item}
                  type="button"
                  className={`history-pagination__num ${item === effectivePage ? 'is-active' : ''}`}
                  onClick={() => setPage(item)}
                  aria-current={item === effectivePage ? 'page' : undefined}
                >
                  {item}
                </button>
              )
            )}
            <button
              type="button"
              className="history-pagination__arrow"
              disabled={effectivePage >= totalPages || totalCount === 0}
              onClick={() => setPage(effectivePage + 1)}
              aria-label="Next page"
            >
              <ChevronRightIcon />
            </button>
          </nav>

          <div className="history-footer__rpp">
            <label htmlFor="history-rpp">Rows per page</label>
            <select
              id="history-rpp"
              className="history-footer__rpp-select"
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setPage(1);
              }}
            >
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
            </select>
          </div>
        </footer>
      </section>

      <MediaLightbox
        open={mediaPreview.open}
        type={mediaPreview.type}
        src={mediaPreview.src}
        onClose={() => setMediaPreview({ open: false, type: 'image', src: '' })}
      />
    </div>
  );
}
