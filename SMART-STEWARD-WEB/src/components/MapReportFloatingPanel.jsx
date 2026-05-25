import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  MapPinIcon as MapPinSolidIcon,
  XMarkIcon,
  ChevronRightIcon,
  CalendarDaysIcon,
  PlayIcon,
} from '@heroicons/react/24/solid';
import {
  MapPinIcon as MapPinOutlineIcon,
  ClipboardDocumentListIcon,
  InformationCircleIcon,
} from '@heroicons/react/24/outline';
import { statusBadgeLabel } from '../utils/mapClusterHelpers';
import { resolveMapMarkerStatus } from '../utils/mapMarkerStatus';
import { statusToLabel } from '../utils/normalizeReportDoc';
import { useAgencyUser } from '../context/AgencyUserContext';
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

/**
 * Compact stat tile shown in the cluster popups. Matches the admin design
 * language: muted slate label with a colored leading dot, large bold value
 * underneath. The dot colors mirror the status palette used in the Reports
 * table so the legend reads the same across the app.
 */
function StatTile({ color, label, value }) {
  return (
    <div className="map-overlay-stat">
      <span className="map-overlay-stat__head">
        <span className="map-overlay-stat__dot" style={{ background: color }} aria-hidden />
        {label}
      </span>
      <span className="map-overlay-stat__value">{value}</span>
    </div>
  );
}

/**
 * Reuses the exact same status pill style as the Reports table so the
 * vocabulary and colors are consistent everywhere a status is shown.
 */
function MapStatusPill({ status }) {
  const key = String(status ?? 'pending').toLowerCase();
  const pillKey =
    key === 'review' || key === 'in_progress'
      ? 'in_progress'
      : key === 'resolved' || key === 'rejected' || key === 'pending'
        ? key
        : 'pending';
  const label = statusBadgeLabel(key);
  return (
    <span className={`reports-status reports-status--${pillKey}`}>{label}</span>
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

function MapThumb({ imageUrl, hasVideo, alt, size = 'md' }) {
  const sizeClass = size === 'sm' ? 'map-overlay-thumb__img--sm' : '';
  if (!imageUrl) {
    return (
      <span className="map-overlay-thumb">
        <span
          className={`map-overlay-thumb__img map-overlay-thumb__img--empty ${sizeClass}`}
          aria-hidden
        />
      </span>
    );
  }
  return (
    <span className="map-overlay-thumb">
      <span
        className={`map-overlay-thumb__img ${sizeClass}`}
        style={{ backgroundImage: `url(${imageUrl})` }}
        role="img"
        aria-label={alt}
      />
      {hasVideo ? (
        <span className="map-overlay-thumb__play" aria-hidden>
          <PlayIcon />
        </span>
      ) : null}
    </span>
  );
}

function OverlayHeader({ title, headline, sub }) {
  return (
    <header className="map-overlay-card__head">
      <h3 className="map-overlay-card__title">
        <MapPinOutlineIcon aria-hidden />
        {title}
      </h3>
      <p className="map-overlay-card__address">
        <MapPinSolidIcon className="map-overlay-card__pin" aria-hidden />
        <span className="map-overlay-card__plus">{headline}</span>
        {sub ? <span className="map-overlay-card__addr">{sub}</span> : null}
      </p>
    </header>
  );
}

export default function MapReportFloatingPanel({ open, variant, onClose, incident, clusterPayload }) {
  const navigate = useNavigate();
  const { viewerAgencyKey } = useAgencyUser();
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
            viewerAgencyKey={viewerAgencyKey}
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
    const recentHasVideo = Boolean(rs.hasVideo) || String(rs.videoUrl ?? '').trim().length > 0;

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

            <OverlayHeader title="LOCATION DETAILS" headline={headline} sub={sub} />

            <div className="map-overlay-stats map-overlay-stats--detail">
              <StatTile color="#64748b" label="Total Reports" value={counts.total} />
              <StatTile color="#9ca3af" label="Pending" value={counts.pending} />
              <StatTile color="#eab308" label="In Progress" value={counts.review} />
              <StatTile color="#22c55e" label="Resolved" value={counts.resolved} />
            </div>

            <section className="map-overlay-about">
              <h4 className="map-overlay-about__title">
                <InformationCircleIcon aria-hidden />
                ABOUT THIS LOCATION
              </h4>
              <p>{locationDescription}</p>
            </section>

            <section className="map-overlay-reports">
              <h4 className="map-overlay-section-label map-overlay-reports__head">
                <ClipboardDocumentListIcon aria-hidden />
                RECENT REPORTS ({counts.total})
              </h4>
              <ul className="map-overlay-reports__list">
                {detailRows.map((inc) => {
                  const summary = inc.reportSummary || {};
                  const status = inc.markerStatus ?? inc.status ?? 'pending';
                  const rowImg = (summary.imageUrl || '').trim();
                  const rowHasVideo =
                    Boolean(summary.hasVideo) ||
                    String(summary.videoUrl ?? '').trim().length > 0;
                  return (
                    <li key={inc.id}>
                      <button
                        type="button"
                        className="map-overlay-report-row"
                        onClick={() => {
                          onClose();
                          setShowClusterDetails(false);
                          navigate(`/reports/${encodeURIComponent(String(summary.docId ?? inc.id))}`);
                        }}
                      >
                        <MapThumb
                          imageUrl={rowImg}
                          hasVideo={rowHasVideo}
                          alt={summary.activity ?? inc.title ?? 'Report media'}
                          size="sm"
                        />
                        <span className="map-overlay-report-row__main">
                          <strong>{summary.activity ?? inc.title}</strong>
                          <small>{summary.displayId ?? inc.id}</small>
                          <small>{summary.date ?? '—'}</small>
                        </span>
                        <MapStatusPill status={status} />
                      </button>
                    </li>
                  );
                })}
              </ul>
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

          <OverlayHeader title="LOCATION SUMMARY" headline={headline} sub={sub} />

          <div className="map-overlay-stats">
            <StatTile color="#64748b" label="Reports" value={counts.total} />
            <StatTile color="#9ca3af" label="Pending" value={counts.pending} />
            <StatTile color="#eab308" label="In Progress" value={counts.review} />
            <StatTile color="#22c55e" label="Resolved" value={counts.resolved} />
            <StatTile color="#ef4444" label="Rejected" value={counts.rejected} />
          </div>

          {recent ? (
            <section className="map-overlay-recent">
              <h4 className="map-overlay-section-label">MOST RECENT REPORT</h4>
              <div className="map-overlay-recent__row">
                <MapThumb
                  imageUrl={recentImage}
                  hasVideo={recentHasVideo}
                  alt={rs.activity ?? recent.title ?? 'Recent report'}
                />
                <div className="map-overlay-recent__body">
                  <div className="map-overlay-recent__id-line">
                    <strong>{rs.displayId ?? recent.id}</strong>
                    <MapStatusPill status={recentStatus} />
                  </div>
                  <p className="map-overlay-recent__cat">{rs.activity ?? recent.title}</p>
                  <p className="map-overlay-recent__date">
                    <CalendarDaysIcon aria-hidden />
                    {rs.date ?? '—'}
                  </p>
                </div>
              </div>
            </section>
          ) : null}

          <button
            type="button"
            className="map-overlay-cta map-overlay-cta--cluster"
            onClick={() => setShowClusterDetails(true)}
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
