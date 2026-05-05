import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  XMarkIcon,
  ChevronRightIcon,
  CalendarDaysIcon,
  MapPinIcon,
  PlayIcon,
} from '@heroicons/react/24/solid';
import { statusBadgeLabel } from '../utils/mapClusterHelpers';

const PLACEHOLDER_IMG =
  'https://images.unsplash.com/photo-1448375240586-882707db8887?w=160&h=100&fit=crop&q=80';

function buildLocationDescription(incidents) {
  const activities = incidents
    .map((inc) => String(inc?.reportSummary?.activity ?? inc?.title ?? '').trim())
    .filter(Boolean);
  if (activities.length === 0) {
    return 'Reports from this area are related to environmental issues and incident activity that require agency verification.';
  }
  const unique = [...new Set(activities)];
  const listed = unique.slice(0, 4).join(', ').toLowerCase();
  return `Reports from this area are related to environmental issues such as ${listed}${unique.length > 4 ? ', and others' : ''}.`;
}

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
  const [showClusterDetails, setShowClusterDetails] = useState(false);

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
    const { counts, headline, sub, recent, incidents = [] } = clusterPayload;
    const rs = recent?.reportSummary || {};
    const recentStatus = recent?.markerStatus ?? recent?.status ?? 'pending';
    const recentImg = (rs.imageUrl || '').trim() || PLACEHOLDER_IMG;
    const locationLabel = [headline, sub].filter(Boolean).join(', ');

    const detailRows = [...incidents]
      .sort((a, b) => (b.reportSummary?.createdAtMs ?? 0) - (a.reportSummary?.createdAtMs ?? 0))
      .slice(0, 12);
    const locationDescription = buildLocationDescription(incidents);

    if (showClusterDetails) {
      return (
        <div className="map-overlay-root" role="dialog" aria-modal="true" aria-label="Location details">
          <button
            type="button"
            className="map-overlay-backdrop"
            onClick={() => {
              setShowClusterDetails(false);
              onClose();
            }}
            aria-label="Close"
          />
          <div className="map-overlay-panel map-overlay-panel--cluster-detail">
            <button
              type="button"
              className="map-overlay-close"
              onClick={() => setShowClusterDetails(false)}
              aria-label="Close"
            >
              <XMarkIcon className="map-overlay-close__icon" />
            </button>
            <header className="map-overlay-detail__head">
              <h3 className="map-overlay-detail__title">LOCATION DETAILS</h3>
              <p className="map-overlay-detail__loc">
                <MapPinIcon aria-hidden />
                <span>{headline}</span>
                {sub ? <em>{sub}</em> : null}
              </p>
            </header>
            <div className="map-overlay-detail__stats">
              <StatChip color="#64748b" label="Total Reports" value={counts.total} />
              <StatChip color="#6b7280" label="Pending" value={counts.pending} />
              <StatChip color="#eab308" label="In progress" value={counts.review} />
              <StatChip color="#22c55e" label="Resolved" value={counts.resolved} />
            </div>
            <section className="map-overlay-detail__about">
              <h4>ABOUT THIS LOCATION</h4>
              <p>{locationDescription}</p>
            </section>
            <section className="map-overlay-detail__reports">
              <div className="map-overlay-detail__reports-head">
                <h4>RECENT REPORTS ({counts.total})</h4>
              </div>
              <div className="map-overlay-detail__list">
                {detailRows.map((inc) => {
                  const summary = inc.reportSummary || {};
                  const status = inc.markerStatus ?? inc.status ?? 'pending';
                  const img = (summary.imageUrl || '').trim() || PLACEHOLDER_IMG;
                  return (
                    <button
                      key={inc.id}
                      type="button"
                      className="map-overlay-detail__row"
                      onClick={() => {
                        onClose();
                        setShowClusterDetails(false);
                        navigate(`/reports/${encodeURIComponent(String(summary.docId ?? inc.id))}`);
                      }}
                    >
                      <span className="map-overlay-detail__row-thumb-wrap">
                        <img src={img} alt="" className="map-overlay-detail__row-thumb" />
                        {summary.hasVideo ? (
                          <span className="map-overlay-detail__row-play" aria-hidden>
                            <PlayIcon />
                          </span>
                        ) : null}
                      </span>
                      <span className="map-overlay-detail__row-main">
                        <strong>{summary.activity ?? inc.title}</strong>
                        <small>{summary.displayId ?? inc.id}</small>
                        <small>{summary.date ?? '—'}</small>
                      </span>
                      <span className={`map-overlay-badge map-overlay-badge--${status}`}>
                        {statusBadgeLabel(status)}
                      </span>
                    </button>
                  );
                })}
              </div>
            </section>
          </div>
        </div>
      );
    }

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
              {sub ? <p className="map-overlay-cluster__sub">{sub}</p> : <p className="map-overlay-cluster__sub">{locationLabel}</p>}
            </div>
          </header>

          <div className="map-overlay-cluster__stats">
            <StatChip color="#64748b" label="Reports" value={counts.total} />
            <StatChip color="#6b7280" label="Pending" value={counts.pending} />
            <StatChip color="#eab308" label="In progress" value={counts.review} />
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
              setShowClusterDetails(true);
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
