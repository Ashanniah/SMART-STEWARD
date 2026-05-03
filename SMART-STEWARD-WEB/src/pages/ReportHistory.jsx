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
import {
  HISTORY_STATS,
  HISTORY_TOTAL_COUNT,
  getHistoryPageRows,
  getPaginationRange,
} from '../data/reportHistoryMock';

const STAT_CONFIG = [
  {
    key: 'total',
    title: 'Total Reports',
    Icon: ClipboardDocumentListIcon,
    accent: 'green',
  },
  {
    key: 'pending',
    title: 'Pending Reports',
    Icon: ClockIcon,
    accent: 'orange',
  },
  {
    key: 'review',
    title: 'Under Review',
    Icon: MagnifyingGlassIcon,
    accent: 'blue',
  },
  {
    key: 'resolved',
    title: 'Resolved Reports',
    Icon: CheckCircleIcon,
    accent: 'teal',
  },
];

function HistoryStatusPill({ status }) {
  const labels = {
    pending: 'Pending',
    review: 'Under Review',
    resolved: 'Resolved',
    in_progress: 'In Progress',
  };
  return (
    <span className={`history-pill history-pill--${status}`}>
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
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const totalPages = Math.max(1, Math.ceil(HISTORY_TOTAL_COUNT / pageSize));
  const effectivePage = Math.min(Math.max(1, page), totalPages);

  const rows = useMemo(
    () => getHistoryPageRows(effectivePage, pageSize),
    [effectivePage, pageSize]
  );

  const startIdx = (effectivePage - 1) * pageSize;
  const showingFrom = HISTORY_TOTAL_COUNT === 0 ? 0 : startIdx + 1;
  const showingTo = Math.min(startIdx + pageSize, HISTORY_TOTAL_COUNT);

  const pageItems = useMemo(
    () => getPaginationRange(effectivePage, totalPages, 2),
    [effectivePage, totalPages]
  );

  const dateRangeLabel = 'May 13, 2025 – May 20, 2025';

  return (
    <div className="report-history-page fade-in">
      <div className="report-history-page__stats">
        {STAT_CONFIG.map(({ key, title, Icon, accent }) => {
          const s = HISTORY_STATS[key];
          return (
            <div
              key={key}
              className={`history-stat-card history-stat-card--${accent}`}
            >
              <div className="history-stat-card__icon" aria-hidden>
                <Icon />
              </div>
              <div className="history-stat-card__body">
                <div className="history-stat-card__label">{title}</div>
                <div className="history-stat-card__value">{s.value}</div>
                <div className="history-stat-card__hint">{s.hint}</div>
                <div className="history-stat-card__trend">
                  <span className="history-stat-card__trend-value">{s.trend}</span>{' '}
                  {s.trendHint}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <header className="report-history-page__header">
        <div>
          <h1 className="report-history-page__title">All Reports History</h1>
          <p className="report-history-page__subtitle">
            Complete history of all reports submitted to the system.
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
              {rows.map((row, idx) => (
                <tr key={`${row.id}-${startIdx + idx}`}>
                  <td className="history-table__id">{row.id}</td>
                  <td className="history-table__thumb-cell">
                    <img
                      src={row.thumb}
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
                        <div className="history-type__title">{row.typeTitle}</div>
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
                  <td>{row.agency}</td>
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
                          navigate(`/reports/${encodeURIComponent(row.id)}`)
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
              ))}
            </tbody>
          </table>
        </div>

        <footer className="history-footer">
          <p className="history-footer__meta">
            Showing {showingFrom} to {showingTo} of {HISTORY_TOTAL_COUNT.toLocaleString()}{' '}
            reports
          </p>

          <nav className="history-pagination" aria-label="Pagination">
            <button
              type="button"
              className="history-pagination__arrow"
              disabled={effectivePage <= 1}
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
              disabled={effectivePage >= totalPages}
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
