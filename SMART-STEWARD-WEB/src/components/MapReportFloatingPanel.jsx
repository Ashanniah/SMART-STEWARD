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
import { resolveMapMarkerStatus } from '../utils/mapMarkerStatus';
import { statusToLabel } from '../utils/normalizeReportDoc';
import RecentReportRow from './RecentReportRow';
import MediaLightbox from './MediaLightbox';

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

function resolveSingleReportProps(incident) {
  const s = incident.reportSummary || {};
  const statusKey = resolveMapMarkerStatus(
    incident.markerStatus ?? incident.status ?? s.statusKey ?? 'pending'
  );
  const imageUrl = (s.imageUrl || '').trim();
  const videoUrl = (s.videoUrl || '').trim();
  const hasVideo = Boolean(s.hasVideo) || videoUrl.length > 0;

  return {
    docId: String(s.docId ?? incident.id ?? '').trim(),
    reportType: s.activity ?? incident.title ?? 'Report',
    location: s.location ?? incident.location ?? '—',
    dateSubmitted: s.date ?? '—',
    timeOfReport: s.time ?? '—',
    assignedAgency: s.assignedAgency ?? '',
    imageUrl: imageUrl || undefined,
    hasVideo,
    videoUrl,
    statusLabel: s.statusLabel ?? statusToLabel(statusKey),
    statusKey,
  };
}

function ClusterThumb({ summary, activity, title }) {
  const imageUrl = (summary.imageUrl || '').trim();
  const hasVideo = Boolean(summary.hasVideo);
  if (!imageUrl) {
    return (
      <span
        className="map-overlay-detail__row-thumb map-overlay-detail__row-thumb--empty"
        aria-hidden
      />
    );
  }
  return (
    <span className="map-overlay-detail__row-thumb-wrap">
      <span
        className="map-overlay-detail__row-thumb"
        style={{ backgroundImage: `url(${imageUrl})` }}
        role="img"
        aria-label={activity ?? title ?? 'Report media'}
      />
      {hasVideo ? (
        <span className="map-overlay-detail__row-play" aria-hidden>
          <PlayIcon />
        </span>
      ) : null}
    </span>
  );
}

export default function MapReportFloatingPanel({ open, variant, onClose, incident, clusterPayload }) {
  const navigate = useNavigate();
  const [showClusterDetails, setShowClusterDetails] = useState(false);
  const [mediaPreview, setMediaPreview] = useState({ open: false, type: 'image', src: '' });

  if (!open) return null;

  if (variant === 'single' && incident) {
    const row = resolveSingleReportProps(incident);

    return (
      <div className="map-overlay-root" role="dialog" aria-modal="true" aria-label="Report details">
        <button type="button" className="map-overlay-backdrop" onClick={onClose} aria-label="Close" />
        <div className="map-overlay-panel map-overlay-panel--single">
          <button type="button" className="map-overlay-close" onClick={onClose} aria-label="Close">
            <XMarkIcon className="map-overlay-close__icon" />
          </button>
          <RecentReportRow
            docId={row.docId}
            reportType={row.reportType}
            location={row.location}
            dateSubmitted={row.dateSubmitted}
            timeOfReport={row.timeOfReport}
            assignedAgency={row.assignedAgency}
            imageUrl={row.imageUrl}
            hasVideo={row.hasVideo}
            statusLabel={row.statusLabel}
            statusKey={row.statusKey}
            onMediaClick={() =>
              setMediaPreview({
                open: true,
                type: row.hasVideo && row.videoUrl ? 'video' : 'image',
                src: row.hasVideo && row.videoUrl ? row.videoUrl : row.imageUrl || '',
              })
            }
          />
        </div>
        <MediaLightbox
          open={mediaPreview.open}
          type={mediaPreview.type}
          src={mediaPreview.src}
          alt={row.reportType}
          onClose={() => setMediaPreview({ open: false, type: 'image', src: '' })}
        />
      </div>
    );
  }

  if (variant === 'cluster' && clusterPayload) {
    const { counts, headline, sub, recent, incidents = [] } = clusterPayload;
    const rs = recent?.reportSummary || {};
    const recentStatus = recent?.markerStatus ?? recent?.status ?? 'pending';
    const recentImage = (rs.imageUrl || '').trim();
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
                      <ClusterThumb
                        summary={summary}
                        activity={summary.activity}
                        title={inc.title}
                      />
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
              {sub ? (
                <p className="map-overlay-cluster__sub">{sub}</p>
              ) : (
                <p className="map-overlay-cluster__sub">{locationLabel}</p>
              )}
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
                {recentImage ? (
                  <span
                    className="map-overlay-cluster__recent-thumb"
                    style={{ backgroundImage: `url(${recentImage})` }}
                    role="img"
                    aria-label="Recent report"
                  />
                ) : (
                  <span className="map-overlay-cluster__recent-thumb map-overlay-cluster__recent-thumb--empty" />
                )}
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
