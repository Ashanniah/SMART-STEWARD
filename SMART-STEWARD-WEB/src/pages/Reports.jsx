import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  MagnifyingGlassIcon,
  FunnelIcon,
  ArrowDownTrayIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  PhotoIcon,
} from '@heroicons/react/24/outline';
import { PlayIcon } from '@heroicons/react/24/solid';
import { useReportsData } from '../context/ReportsDataContext';
import MediaLightbox from '../components/MediaLightbox';

const PAGE_SIZE = 5;

function ReportMediaThumb({ imageUrl, hasVideo, onOpen }) {
  return (
    <button
      type="button"
      className="reports-media"
      aria-label={hasVideo ? 'Open report video' : 'Open report image'}
      onClick={onOpen}
    >
      {imageUrl ? (
        <div className="reports-media__img" style={{ backgroundImage: `url(${imageUrl})` }} />
      ) : (
        <div className="reports-media__img reports-media__img--empty" aria-hidden>
          <PhotoIcon />
        </div>
      )}
      {hasVideo ? (
        <span className="reports-media__play" aria-hidden>
          <PlayIcon />
        </span>
      ) : null}
    </button>
  );
}

function StatusBadge({ status }) {
  const labels = {
    pending: 'Pending',
    review: 'In Progress',
    in_progress: 'In Progress',
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
  const [filterOpen, setFilterOpen] = useState(false);
  const [statusFilter, setStatusFilter] = useState('all');
  const [mediaFilter, setMediaFilter] = useState('all');
  const [mediaPreview, setMediaPreview] = useState({ open: false, type: 'image', src: '' });

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return reports.filter((r) => {
      const statusPass = statusFilter === 'all' ? true : r.status === statusFilter;
      const mediaPass =
        mediaFilter === 'all' ? true : mediaFilter === 'video' ? Boolean(r.hasVideo) : !r.hasVideo;
      if (!statusPass || !mediaPass) return false;
      if (!q) return true;
      const statusText =
        r.status === 'review' || r.status === 'in_progress'
          ? 'in progress'
          : r.status;
      return (
        r.id.toLowerCase().includes(q) ||
        r.docId.toLowerCase().includes(q) ||
        r.location.toLowerCase().includes(q) ||
        r.activity.toLowerCase().includes(q) ||
        statusText.includes(q)
      );
    });
  }, [query, reports, statusFilter, mediaFilter]);

  const filteredTotal = filtered.length;
  const filteredPages = Math.max(1, Math.ceil(filteredTotal / PAGE_SIZE));
  const effectivePage = Math.min(Math.max(1, page), filteredPages);
  const start = (effectivePage - 1) * PAGE_SIZE;
  const pageRows = filtered.slice(start, start + PAGE_SIZE);
  const showingFrom = filteredTotal === 0 ? 0 : start + 1;
  const showingTo = Math.min(start + PAGE_SIZE, filteredTotal);

  function exportCsv() {
    const rows = filtered.map((r) => ({
      reportId: r.id,
      docId: r.docId,
      dateTime: r.date,
      location: r.location,
      activityType: r.activity,
      status:
        r.status === 'review' || r.status === 'in_progress'
          ? 'In Progress'
          : r.status.charAt(0).toUpperCase() + r.status.slice(1),
      hasVideo: r.hasVideo ? 'Yes' : 'No',
    }));
    const headers = ['reportId', 'docId', 'dateTime', 'location', 'activityType', 'status', 'hasVideo'];
    const escape = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const csv = [headers.join(','), ...rows.map((row) => headers.map((h) => escape(row[h])).join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const stamp = new Date().toISOString().slice(0, 10);
    a.href = url;
    a.download = `reports-export-${stamp}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

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
          <button
            type="button"
            className="reports-btn reports-btn--muted"
            onClick={() => setFilterOpen((v) => !v)}
          >
            <FunnelIcon aria-hidden />
            Filter
          </button>
          <button type="button" className="reports-btn reports-btn--export" onClick={exportCsv}>
            <ArrowDownTrayIcon aria-hidden />
            Export
          </button>
        </div>
      </div>
      {filterOpen ? (
        <div className="reports-filter-panel">
          <label className="reports-filter-panel__field">
            <span>Status</span>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(1);
              }}
            >
              <option value="all">All</option>
              <option value="pending">Pending</option>
              <option value="review">In Progress (Review)</option>
              <option value="in_progress">In Progress</option>
              <option value="resolved">Resolved</option>
              <option value="rejected">Rejected</option>
            </select>
          </label>
          <label className="reports-filter-panel__field">
            <span>Media</span>
            <select
              value={mediaFilter}
              onChange={(e) => {
                setMediaFilter(e.target.value);
                setPage(1);
              }}
            >
              <option value="all">All</option>
              <option value="video">Video reports</option>
              <option value="image">Image-only reports</option>
            </select>
          </label>
          <button
            type="button"
            className="reports-btn reports-btn--muted"
            onClick={() => {
              setStatusFilter('all');
              setMediaFilter('all');
              setPage(1);
            }}
          >
            Reset Filters
          </button>
        </div>
      ) : null}

      <div className="reports-table-card">
        <div className="reports-table-wrap">
          <table className="reports-table">
            <thead>
              <tr>
                <th>Media / Report ID</th>
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
                    No reports yet. Mobile submissions will show here.
                  </td>
                </tr>
              ) : (
                pageRows.map((row) => (
                  <tr key={row.docId}>
                    <td>
                      <div className="reports-id-cell">
                        <ReportMediaThumb
                          imageUrl={row.imageUrl || ''}
                          hasVideo={Boolean(row.hasVideo)}
                          onOpen={() =>
                            setMediaPreview({
                              open: true,
                              type: row.hasVideo && row.videoUrl ? 'video' : 'image',
                              src: row.hasVideo && row.videoUrl ? row.videoUrl : (row.imageUrl || row.mediaUrl || ''),
                            })
                          }
                        />
                        <span className="reports-id-cell__id">{row.id}</span>
                      </div>
                    </td>
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
      <MediaLightbox
        open={mediaPreview.open}
        type={mediaPreview.type}
        src={mediaPreview.src}
        onClose={() => setMediaPreview({ open: false, type: 'image', src: '' })}
      />
    </div>
  );
}
