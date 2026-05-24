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
  buildAgencyFilterOptions,
  buildTypeFilterOptions,
  DEFAULT_DASHBOARD_FILTERS,
  filterAndSortReports,
  filterClosedReports,
  historyArchiveCountsFromReports,
  FILTER_ALL_AGENCIES,
  FILTER_ALL_STATUS,
  FILTER_ALL_TYPES,
} from '../utils/dashboardFilters';
import { parseAssignedAgencies } from '../utils/agencyScope';
import {
  formatReportDateOnly,
  formatReportTimeOnly,
} from '../utils/normalizeReportDoc';

const PLACEHOLDER_THUMB =
  'https://images.unsplash.com/photo-1611287157826-4e513e77ba9a?w=120&h=120&fit=crop&q=80';

const STAT_CONFIG = [
  { key: 'total', title: 'Total Archived', Icon: ClipboardDocumentListIcon, accent: 'green' },
  { key: 'resolved', title: 'Resolved', Icon: CheckCircleIcon, accent: 'teal' },
  { key: 'rejected', title: 'Rejected', Icon: XCircleIcon, accent: 'red' },
];

const STATUS_FILTER_OPTIONS = [FILTER_ALL_STATUS, 'Resolved', 'Rejected'];

const COL_COUNT = 8;

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

function AgencyChips({ raw }) {
  const agencies = parseAssignedAgencies(raw);
  if (agencies.length === 0) {
    const fallback = String(raw ?? '').trim();
    if (!fallback) return <span className="reports-agency-empty">—</span>;
    return <span className="reports-agency-chip">{fallback}</span>;
  }
  return (
    <div className="reports-agency-chips">
      {agencies.map((a) => (
        <span key={a} className="reports-agency-chip">
          {a}
        </span>
      ))}
    </div>
  );
}

function fmtNum(n) {
  return Number(n || 0).toLocaleString('en-US');
}

export default function ReportHistory() {
  const navigate = useNavigate();
  const { viewerAgencyKey } = useAgencyUser();
  const { reports, loading, error } = useReportsData();
  const [mediaPreview, setMediaPreview] = useState({ open: false, type: 'image', src: '' });
  const [draftFilters, setDraftFilters] = useState({
    ...DEFAULT_DASHBOARD_FILTERS,
    sort: 'newest',
  });
  const [appliedFilters, setAppliedFilters] = useState({
    ...DEFAULT_DASHBOARD_FILTERS,
    sort: 'newest',
  });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const agencyFilterInitialized = useRef(false);

  const archiveReports = useMemo(() => filterClosedReports(reports), [reports]);

  const typeOptions = useMemo(() => buildTypeFilterOptions(archiveReports), [archiveReports]);
  const agencyOptions = useMemo(
    () => buildAgencyFilterOptions(archiveReports, viewerAgencyKey),
    [archiveReports, viewerAgencyKey]
  );

  const defaultAgencyFilter = useMemo(() => {
    if (agencyOptions.length === 0) return FILTER_ALL_AGENCIES;
    if (agencyOptions.length === 1) return agencyOptions[0];
    if (viewerAgencyKey && agencyOptions.includes(viewerAgencyKey)) return viewerAgencyKey;
    return FILTER_ALL_AGENCIES;
  }, [agencyOptions, viewerAgencyKey]);

  useEffect(() => {
    if (!agencyFilterInitialized.current && viewerAgencyKey && defaultAgencyFilter !== FILTER_ALL_AGENCIES) {
      agencyFilterInitialized.current = true;
      const next = { ...DEFAULT_DASHBOARD_FILTERS, agency: defaultAgencyFilter, sort: 'newest' };
      setDraftFilters(next);
      setAppliedFilters(next);
    }
  }, [viewerAgencyKey, defaultAgencyFilter]);

  useEffect(() => {
    const fixAgency = (prev) => {
      if (prev.agency !== FILTER_ALL_AGENCIES && !agencyOptions.includes(prev.agency)) {
        return { ...prev, agency: defaultAgencyFilter };
      }
      return prev;
    };
    setDraftFilters(fixAgency);
    setAppliedFilters(fixAgency);
  }, [agencyOptions, defaultAgencyFilter]);

  const filteredReports = useMemo(
    () => filterAndSortReports(archiveReports, appliedFilters, appliedFilters.sort),
    [archiveReports, appliedFilters]
  );

  const historyCounts = useMemo(
    () => historyArchiveCountsFromReports(filteredReports),
    [filteredReports]
  );

  const totalCount = filteredReports.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const effectivePage = Math.min(Math.max(1, page), totalPages);

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
    }));
  }, [filteredReports, effectivePage, pageSize]);

  const startIdx = (effectivePage - 1) * pageSize;
  const showingFrom = totalCount === 0 ? 0 : startIdx + 1;
  const showingTo = Math.min(startIdx + pageSize, totalCount);

  const pageItems = useMemo(
    () => getPaginationRange(effectivePage, totalPages, 2),
    [effectivePage, totalPages]
  );

  const applyFilters = () => {
    setAppliedFilters({ ...draftFilters });
    setPage(1);
  };

  const resetFilters = () => {
    const reset = {
      type: FILTER_ALL_TYPES,
      status: FILTER_ALL_STATUS,
      agency: defaultAgencyFilter,
      date: '',
      sort: 'newest',
    };
    setDraftFilters(reset);
    setAppliedFilters(reset);
    setPage(1);
    setPageSize(10);
  };

  function exportCsv() {
    const headers = [
      'reportId',
      'dateSubmitted',
      'timeOfReport',
      'reportType',
      'location',
      'agency',
      'status',
    ];
    const escape = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const csvRows = filteredReports.map((r) => ({
      reportId: r.id,
      dateSubmitted: formatReportDateOnly(r.createdAt),
      timeOfReport: formatReportTimeOnly(r.createdAt),
      reportType: r.activity,
      location: r.location,
      agency: parseAssignedAgencies(r.assignedAgency).join(', ') || r.assignedAgency,
      status: r.status,
    }));
    const csv = [
      headers.join(','),
      ...csvRows.map((row) => headers.map((h) => escape(row[h])).join(',')),
    ].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `report-history-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
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
          </h3>
          <p className="denr-filters__hint">
            Resolved and rejected reports only — filter by type, outcome, agency, or date
          </p>
        </div>
        <div className="denr-filters__row">
          <label className="denr-filter-field">
            <span>By Type</span>
            <select
              value={draftFilters.type}
              onChange={(e) => setDraftFilters((f) => ({ ...f, type: e.target.value }))}
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
              value={draftFilters.status}
              onChange={(e) => setDraftFilters((f) => ({ ...f, status: e.target.value }))}
            >
              {STATUS_FILTER_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Agency</span>
            <select
              value={draftFilters.agency}
              onChange={(e) => setDraftFilters((f) => ({ ...f, agency: e.target.value }))}
              disabled={agencyOptions.length === 0}
            >
              {agencyOptions.length === 0 ? (
                <option value={FILTER_ALL_AGENCIES}>No agencies</option>
              ) : (
                agencyOptions.map((opt) => (
                  <option key={opt} value={opt}>
                    {opt}
                  </option>
                ))
              )}
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Date</span>
            <input
              type="date"
              value={draftFilters.date}
              onChange={(e) => setDraftFilters((f) => ({ ...f, date: e.target.value }))}
            />
          </label>
          <label className="denr-filter-field">
            <span>Sort</span>
            <select
              value={draftFilters.sort}
              onChange={(e) => setDraftFilters((f) => ({ ...f, sort: e.target.value }))}
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
            <button type="button" className="denr-btn-reset" onClick={resetFilters}>
              <ArrowPathIcon aria-hidden />
              Reset
            </button>
            <button type="button" className="history-btn history-btn--export" onClick={exportCsv}>
              <ArrowDownTrayIcon aria-hidden />
              Export
            </button>
          </div>
        </div>
      </section>

      <section className="history-table-card">
        <div className="history-table-card__head">
          <h2 className="history-table-card__title">
            <ClipboardDocumentListIcon aria-hidden />
            Report archive
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
                      ? 'No archived reports yet. Resolved and rejected reports will appear here.'
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
                      <AgencyChips raw={row.assignedAgency} />
                    </td>
                    <td>
                      <HistoryStatusPill status={row.status} />
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
