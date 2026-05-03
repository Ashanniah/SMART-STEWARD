import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ClipboardDocumentListIcon,
  ClockIcon,
  MagnifyingGlassIcon,
  CheckCircleIcon,
  ArrowDownTrayIcon,
  MapPinIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ArrowPathIcon,
  EllipsisVerticalIcon,
} from '@heroicons/react/24/outline';
import { CheckCircleIcon as CheckCircleSolidIcon } from '@heroicons/react/24/solid';
import { useReportsData } from '../context/ReportsDataContext';

const STAT_CONFIG = [
  {
    key: 'total',
    title: 'Total Reports',
    Icon: ClipboardDocumentListIcon,
    accent: 'green',
    hint: 'All reports received',
  },
  {
    key: 'pending',
    title: 'Pending Reports',
    Icon: ClockIcon,
    accent: 'orange',
    hint: 'Awaiting initial review',
  },
  {
    key: 'review',
    title: 'Under Review',
    Icon: MagnifyingGlassIcon,
    accent: 'blue',
    hint: 'Being reviewed by agency',
  },
  {
    key: 'resolved',
    title: 'Resolved Reports',
    Icon: CheckCircleIcon,
    accent: 'teal',
    hint: 'Closed as resolved',
  },
];

function getPaginationRange(current, total, delta = 2) {
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

function HistoryStatusPill({ status }) {
  const labels = {
    pending: 'Pending',
    review: 'Under Review',
    resolved: 'Resolved',
    rejected: 'Rejected',
    in_progress: 'In Progress',
  };
  const pillStatus =
    status === 'rejected'
      ? 'rejected'
      : status === 'in_progress'
        ? 'in_progress'
        : status;
  return (
    <span className={`history-pill history-pill--${pillStatus}`}>
      {labels[status] ?? status}
    </span>
  );
}

function PriorityLabel({ priority }) {
  const labels = { high: 'High', medium: 'Medium', low: 'Low' };
  return (
    <span className={`history-priority history-priority--${priority}`}>
      {labels[priority] ?? priority}
    </span>
  );
}

export default function ReportHistory() {
  const navigate = useNavigate();
  const { reports, loading, error, counts } = useReportsData();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const totalCount = reports.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const effectivePage = Math.min(Math.max(1, page), totalPages);

  const rows = useMemo(() => {
    const start = (effectivePage - 1) * pageSize;
    return reports.slice(start, start + pageSize);
  }, [reports, effectivePage, pageSize]);

  const startIdx = (effectivePage - 1) * pageSize;
  const showingFrom = totalCount === 0 ? 0 : startIdx + 1;
  const showingTo = Math.min(startIdx + pageSize, totalCount);

  const pageItems = useMemo(
    () => getPaginationRange(effectivePage, totalPages, 2),
    [effectivePage, totalPages]
  );

  const dateRangeLabel = 'Newest first';

  return (
    <div className="report-history-page fade-in">
      {error ? (
        <p className="reports-page__banner-msg" role="alert">
          {error}
        </p>
      ) : null}

      <div className="report-history-page__stats">
        {STAT_CONFIG.map(({ key, title, Icon, accent, hint }) => (
          <div
            key={key}
            className={`history-stat-card history-stat-card--${accent}`}
          >
            <div className="history-stat-card__icon" aria-hidden>
              <Icon />
            </div>
            <div className="history-stat-card__body">
              <div className="history-stat-card__label">{title}</div>
              <div className="history-stat-card__value">
                {(counts[key] ?? 0).toLocaleString()}
              </div>
              <div className="history-stat-card__hint">{hint}</div>
              <div className="history-stat-card__trend">
                <span className="history-stat-card__trend-value">Live</span> counts refresh automatically
              </div>
            </div>
          </div>
        ))}
      </div>

      <header className="report-history-page__header">
        <div>
          <h1 className="report-history-page__title">All Reports History</h1>
          <p className="report-history-page__subtitle">
            Full history of all reports in the system, newest first.
          </p>
        </div>
        <button type="button" className="history-btn history-btn--export-outline">
          <ArrowDownTrayIcon aria-hidden />
          Export Report
        </button>
      </header>

      <div className="history-filters">
        <label className="history-filters__field">
          <span className="history-filters__label">By Type</span>
          <select className="history-filters__select" defaultValue="">
            <option value="">All Types</option>
            <option value="dumping">Illegal Dumping</option>
            <option value="burning">Open Burning</option>
            <option value="trees">Tree Cutting</option>
          </select>
        </label>
        <label className="history-filters__field">
          <span className="history-filters__label">By Status</span>
          <select className="history-filters__select" defaultValue="">
            <option value="">All Status</option>
            <option value="pending">Pending</option>
            <option value="review">Under Review</option>
            <option value="in_progress">In Progress</option>
            <option value="resolved">Resolved</option>
            <option value="rejected">Rejected</option>
          </select>
        </label>
        <label className="history-filters__field">
          <span className="history-filters__label">By Agency</span>
          <select className="history-filters__select" defaultValue="">
            <option value="">All Agencies</option>
            <option value="denr">DENR</option>
          </select>
        </label>
        <label className="history-filters__field history-filters__field--date">
          <span className="history-filters__label">By Date</span>
          <div className="history-filters__range">{dateRangeLabel}</div>
        </label>
        <div className="history-filters__actions">
          <button type="button" className="history-btn history-btn--apply">
            Apply Filters
          </button>
          <button
            type="button"
            className="history-btn history-btn--reset"
            onClick={() => {
              setPage(1);
              setPageSize(10);
            }}
          >
            <ArrowPathIcon aria-hidden />
            Reset
          </button>
        </div>
      </div>

      <div className="history-table-card">
        <div className="history-table-wrap">
          <table className="history-table">
            <thead>
              <tr>
                <th>ID</th>
                <th aria-label="Thumbnail" />
                <th>Report Type</th>
                <th>Location</th>
                <th>Date &amp; Time</th>
                <th>Reported By</th>
                <th>Agency</th>
                <th>Status</th>
                <th>Priority</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading && reports.length === 0 ? (
                <tr>
                  <td colSpan={10} className="reports-table__loading">
                    Loading reports…
                  </td>
                </tr>
              ) : rows.length === 0 ? (
                <tr>
                  <td colSpan={10} className="reports-table__loading">
                    No reports to display.
                  </td>
                </tr>
              ) : (
                rows.map((row) => (
                  <tr key={row.docId}>
                    <td className="history-table__id">{row.id}</td>
                    <td className="history-table__thumb-cell">
                      <img
                        src={
                          row.imageUrl ||
                          'https://images.unsplash.com/photo-1448375240586-882707db8887?w=120&h=120&fit=crop&q=80'
                        }
                        alt=""
                        className="history-table__thumb"
                        width={48}
                        height={48}
                      />
                    </td>
                    <td>
                      <div className="history-type">
                        <CheckCircleSolidIcon
                          className="history-type__check"
                          aria-hidden
                        />
                        <div>
                          <div className="history-type__title">{row.activity}</div>
                          <div className="history-type__cat">{row.categoryLabel}</div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="history-loc">
                        <span>{row.location}</span>
                        <a
                          className="history-map-link"
                          href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(row.location)}`}
                          target="_blank"
                          rel="noreferrer"
                        >
                          <MapPinIcon aria-hidden />
                          View on Map
                        </a>
                      </div>
                    </td>
                    <td>{row.dateTime}</td>
                    <td>{row.reportedBy}</td>
                    <td>{row.assignedAgency}</td>
                    <td>
                      <HistoryStatusPill status={row.status} />
                    </td>
                    <td>
                      <PriorityLabel priority={row.priority} />
                    </td>
                    <td>
                      <div className="history-actions">
                        <button
                          type="button"
                          className="history-btn-view"
                          onClick={() =>
                            navigate(`/reports/${encodeURIComponent(row.docId)}`)
                          }
                        >
                          View Details
                        </button>
                        <button
                          type="button"
                          className="history-btn-more"
                          aria-label="More actions"
                        >
                          <EllipsisVerticalIcon aria-hidden />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <footer className="history-footer">
          <p className="history-footer__meta">
            {totalCount === 0 ? (
              'No reports to show.'
            ) : (
              <>
                Showing {showingFrom} to {showingTo} of {totalCount.toLocaleString()} reports
              </>
            )}
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
      </div>
    </div>
  );
}
