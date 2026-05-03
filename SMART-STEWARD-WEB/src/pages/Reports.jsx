import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  MagnifyingGlassIcon,
  FunnelIcon,
  ClockIcon,
  ArrowDownTrayIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
} from '@heroicons/react/24/outline';
import { useReportsData } from '../context/ReportsDataContext';

const PAGE_SIZE = 5;

function StatusBadge({ status }) {
  const labels = {
    pending: 'Pending',
    review: 'Under Review',
    resolved: 'Resolved',
    rejected: 'Rejected',
  };
  return (
    <span className={`reports-status reports-status--${status}`}>
      {labels[status] ?? status}
    </span>
  );
}

export default function Reports() {
  const navigate = useNavigate();
  const { reports, loading, error } = useReportsData();
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return reports;
    return reports.filter(
      (r) =>
        r.id.toLowerCase().includes(q) ||
        r.docId.toLowerCase().includes(q) ||
        r.location.toLowerCase().includes(q) ||
        r.activity.toLowerCase().includes(q)
    );
  }, [query, reports]);

  const filteredTotal = filtered.length;
  const filteredPages = Math.max(1, Math.ceil(filteredTotal / PAGE_SIZE));
  const effectivePage = Math.min(Math.max(1, page), filteredPages);
  const start = (effectivePage - 1) * PAGE_SIZE;
  const pageRows = filtered.slice(start, start + PAGE_SIZE);
  const showingFrom = filteredTotal === 0 ? 0 : start + 1;
  const showingTo = Math.min(start + PAGE_SIZE, filteredTotal);

  return (
    <div className="reports-page fade-in">
      <h1 className="reports-page__title">REPORTS</h1>

      {error ? (
        <p className="reports-page__banner-msg" role="alert">
          {error}
        </p>
      ) : null}

      <div className="reports-toolbar">
        <div className="reports-search">
          <MagnifyingGlassIcon className="reports-search__icon" aria-hidden />
          <input
            type="search"
            placeholder="Search reports..."
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(1);
            }}
            aria-label="Search reports"
          />
        </div>
        <div className="reports-toolbar__actions">
          <button type="button" className="reports-btn reports-btn--muted">
            <FunnelIcon aria-hidden />
            Filter
          </button>
          <button type="button" className="reports-btn reports-btn--muted">
            <ClockIcon aria-hidden />
            History
          </button>
          <button type="button" className="reports-btn reports-btn--export">
            <ArrowDownTrayIcon aria-hidden />
            Export
          </button>
        </div>
      </div>

      <div className="reports-table-card">
        <div className="reports-table-wrap">
          <table className="reports-table">
            <thead>
              <tr>
                <th>Report ID</th>
                <th>Date &amp; Time</th>
                <th>Location</th>
                <th>Activity type</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {loading && reports.length === 0 ? (
                <tr>
                  <td colSpan={6} className="reports-table__loading">
                    Loading reports…
                  </td>
                </tr>
              ) : pageRows.length === 0 ? (
                <tr>
                  <td colSpan={6} className="reports-table__loading">
                    No reports to display.
                  </td>
                </tr>
              ) : (
                pageRows.map((row) => (
                  <tr key={row.docId}>
                    <td>{row.id}</td>
                    <td>{row.date}</td>
                    <td>{row.location}</td>
                    <td className="reports-table__activity">{row.activity}</td>
                    <td>
                      <StatusBadge status={row.status} />
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

        <div className="reports-footer">
          <p className="reports-footer__meta">
            {filteredTotal === 0 ? (
              query.trim()
                ? 'No reports match your search.'
                : 'No reports to display.'
            ) : (
              <>
                Showing {showingFrom} to {showingTo} of {filteredTotal} reports
              </>
            )}
          </p>

          {filteredTotal > 0 && (
            <nav className="reports-pagination" aria-label="Pagination">
              <button
                type="button"
                className="reports-pagination__arrow"
                disabled={effectivePage <= 1}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                aria-label="Previous page"
              >
                <ChevronLeftIcon />
              </button>
              {Array.from({ length: filteredPages }, (_, i) => i + 1).map((p) => (
                <button
                  key={p}
                  type="button"
                  className={`reports-pagination__num ${p === effectivePage ? 'is-active' : ''}`}
                  onClick={() => setPage(p)}
                  aria-current={p === effectivePage ? 'page' : undefined}
                >
                  {p}
                </button>
              ))}
              <button
                type="button"
                className="reports-pagination__arrow"
                disabled={effectivePage >= filteredPages}
                onClick={() => setPage((p) => Math.min(filteredPages, p + 1))}
                aria-label="Next page"
              >
                <ChevronRightIcon />
              </button>
            </nav>
          )}
        </div>
      </div>
    </div>
  );
}
