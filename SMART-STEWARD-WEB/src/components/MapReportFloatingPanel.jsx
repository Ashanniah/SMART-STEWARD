import { useNavigate } from 'react-router-dom';
import {
  XMarkIcon,
  ChevronRightIcon,
  CalendarDaysIcon,
  MapPinIcon,
} from '@heroicons/react/24/solid';
import { statusBadgeLabel } from '../utils/mapClusterHelpers';

const PLACEHOLDER_IMG =
  'https://images.unsplash.com/photo-1448375240586-882707db8887?w=160&h=100&fit=crop&q=80';

function StatChip({ color, label, value }) {
  return (
    <div className="map-overlay-cluster__stat">
      <span className="map-overlay-cluster__stat-dot" style={{ background: color }} />
      <span className="map-overlay-cluster__stat-label">{label}</span>
      <span className="map-overlay-cluster__stat-value">{value}</span>
    </div>
  );
}

export default function MapReportFloatingPanel({ open, variant, onClose, incident, clusterPayload }) {
  const navigate = useNavigate();

  if (!open) return null;

  if (variant === 'single' && incident) {
    const s = incident.reportSummary || {};
    const docId = s.docId ?? incident.id;
    const title = s.activity ?? incident.title ?? 'Report';
    const loc = s.location ?? '—';
    const date = s.date ?? '—';
    const status = incident.markerStatus ?? incident.status ?? 'pending';
    const img = (s.imageUrl || '').trim() || PLACEHOLDER_IMG;

    return (
      <div className="map-overlay-root" role="dialog" aria-modal="true" aria-label="Report details">
        <button type="button" className="map-overlay-backdrop" onClick={onClose} aria-label="Close" />
        <div className="map-overlay-panel map-overlay-panel--single">
          <button type="button" className="map-overlay-close" onClick={onClose} aria-label="Close">
            <XMarkIcon className="map-overlay-close__icon" />
          </button>
          <div className="map-overlay-single__row">
            <div className="map-overlay-single__text">
              <h3 className="map-overlay-single__title">{title}</h3>
              <p className="map-overlay-single__meta">{loc}</p>
              <span className={`map-overlay-badge map-overlay-badge--${status}`}>
                {statusBadgeLabel(status)}
              </span>
              <p className="map-overlay-single__date">
                <CalendarDaysIcon className="map-overlay-single__cal" aria-hidden />
                {date}
              </p>
            </div>
            <img className="map-overlay-single__thumb" src={img} alt="" />
          </div>
          <button
            type="button"
            className="map-overlay-cta"
            onClick={() => {
              onClose();
              navigate(`/reports/${encodeURIComponent(String(docId))}`);
            }}
          >
            View report
            <ChevronRightIcon className="map-overlay-cta__chev" aria-hidden />
          </button>
        </div>
      </div>
    );
  }

  if (variant === 'cluster' && clusterPayload) {
    const { counts, headline, sub, recent } = clusterPayload;
    const rs = recent?.reportSummary || {};
    const recentStatus = recent?.markerStatus ?? recent?.status ?? 'pending';
    const recentImg = (rs.imageUrl || '').trim() || PLACEHOLDER_IMG;

    return (
      <div className="map-overlay-root" role="dialog" aria-modal="true" aria-label="Area reports">
        <button type="button" className="map-overlay-backdrop" onClick={onClose} aria-label="Close" />
        <div className="map-overlay-panel map-overlay-panel--cluster">
          <button type="button" className="map-overlay-close" onClick={onClose} aria-label="Close">
            <XMarkIcon className="map-overlay-close__icon" />
          </button>

          <header className="map-overlay-cluster__head">
            <MapPinIcon className="map-overlay-cluster__pin" aria-hidden />
            <div>
              <h3 className="map-overlay-cluster__title">{headline}</h3>
              {sub ? <p className="map-overlay-cluster__sub">{sub}</p> : null}
            </div>
          </header>

          <div className="map-overlay-cluster__stats">
            <StatChip color="#64748b" label="Reports" value={counts.total} />
            <StatChip color="#6b7280" label="Pending" value={counts.pending} />
            <StatChip color="#3b82f6" label="In progress" value={counts.review} />
            <StatChip color="#22c55e" label="Resolved" value={counts.resolved} />
            <StatChip color="#ef4444" label="Rejected" value={counts.rejected} />
          </div>

          {recent ? (
            <section className="map-overlay-cluster__recent">
              <p className="map-overlay-cluster__recent-label">Most recent report</p>
              <div className="map-overlay-cluster__recent-row">
                <div>
                  <p className="map-overlay-cluster__recent-id">{rs.displayId ?? recent.id}</p>
                  <span className={`map-overlay-badge map-overlay-badge--${recentStatus}`}>
                    {statusBadgeLabel(recentStatus)}
                  </span>
                  <p className="map-overlay-cluster__recent-cat">{rs.activity ?? recent.title}</p>
                  <p className="map-overlay-cluster__recent-date">
                    <CalendarDaysIcon className="map-overlay-single__cal" aria-hidden />
                    {rs.date ?? '—'}
                  </p>
                </div>
                <img className="map-overlay-cluster__recent-thumb" src={recentImg} alt="" />
              </div>
            </section>
          ) : null}

          <button
            type="button"
            className="map-overlay-cta map-overlay-cta--cluster"
            onClick={() => {
              onClose();
              navigate('/reports');
            }}
          >
            View all {counts.total} reports
            <ChevronRightIcon className="map-overlay-cta__chev" aria-hidden />
          </button>
        </div>
      </div>
    );
  }

  return null;
}
