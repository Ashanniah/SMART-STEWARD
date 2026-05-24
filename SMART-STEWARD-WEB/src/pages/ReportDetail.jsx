import { useMemo, useState } from 'react';
import { useNavigate, useParams, Navigate } from 'react-router-dom';
import {
  ClipboardDocumentListIcon,
  CheckCircleIcon,
  IdentificationIcon,
  FireIcon,
  MapPinIcon,
  DocumentTextIcon,
  UserIcon,
  BuildingOffice2Icon,
  PhotoIcon,
  ExclamationTriangleIcon,
} from '@heroicons/react/24/outline';
import { PlayIcon } from '@heroicons/react/24/solid';
import GoogleMapComponent from '../components/GoogleMap';
import MediaLightbox from '../components/MediaLightbox';
import { useReportsData } from '../context/ReportsDataContext';
import { normalizedToDetailView, reportsToMapIncidents } from '../utils/normalizeReportDoc';

function DetailSeverityBadge({ severityKey, label }) {
  const key = severityKey || 'unknown';
  const text = label || 'Not assessed';
  return (
    <span className={`report-detail-severity report-detail-severity--${key}`}>{text}</span>
  );
}

function DetailStatusBadge({ status }) {
  const labels = {
    pending: 'Pending',
    review: 'In Progress',
    in_progress: 'In Progress',
    resolved: 'Resolved',
    rejected: 'Rejected',
  };
  const pillStatus =
    status === 'review' ? 'in_progress' : status === 'rejected' ? 'rejected' : status;
  return (
    <span className={`reports-status reports-status--${pillStatus}`}>
      {labels[status] ?? status}
    </span>
  );
}

export default function ReportDetail() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const id = reportId ? decodeURIComponent(reportId) : '';

  const { loading, error, reportByDocId } = useReportsData();
  const row = id ? reportByDocId(id) : null;

  const detail = useMemo(() => (row ? normalizedToDetailView(row) : null), [row]);
  const [mediaPreview, setMediaPreview] = useState({ open: false, type: 'image', src: '' });

  const mapIncidents = useMemo(() => {
    if (!row || row.lat == null || row.lng == null) return [];
    return reportsToMapIncidents([row]);
  }, [row]);

  if (!id) {
    return <Navigate to="/reports" replace />;
  }

  if (loading && !row) {
    return (
      <div className="reports-page report-detail-page fade-in">
        <p className="reports-table__loading">Loading report…</p>
      </div>
    );
  }

  if (!loading && !row) {
    return <Navigate to="/reports" replace />;
  }

  if (!detail) {
    return <Navigate to="/reports" replace />;
  }

  return (
    <div className="reports-page report-detail-page fade-in">
      {error ? (
        <p className="reports-page__banner-msg" role="alert">
          {error}
        </p>
      ) : null}

      <div className="report-detail__grid">
        <section className="report-detail-card report-detail-card--info reports-table-card">
          <h2 className="report-detail-card__heading report-detail-card__heading--primary">
            <ClipboardDocumentListIcon aria-hidden />
            REPORT INFORMATION
          </h2>
          <dl className="report-detail-info">
            <div className="report-detail-info__row">
              <dt>
                <IdentificationIcon aria-hidden />
                Report ID
              </dt>
              <dd>{detail.id}</dd>
            </div>
            <div className="report-detail-info__row report-detail-info__row--status">
              <dt>
                <CheckCircleIcon aria-hidden />
                Current Status
              </dt>
              <dd>
                <DetailStatusBadge status={detail.status} />
              </dd>
            </div>
            <div className="report-detail-info__row">
              <dt>
                <FireIcon aria-hidden />
                Report Type
              </dt>
              <dd>{detail.reportTypeLabel}</dd>
            </div>
            <div className="report-detail-info__row">
              <dt>
                <MapPinIcon aria-hidden /> Location
              </dt>
              <dd>{detail.locationDisplay}</dd>
            </div>
            <div className="report-detail-info__row">
              <dt>
                <DocumentTextIcon aria-hidden /> Description
              </dt>
              <dd>{detail.description}</dd>
            </div>
            <div className="report-detail-info__row">
              <dt>
                <UserIcon aria-hidden /> Reported By
              </dt>
              <dd>{detail.reportedBy}</dd>
            </div>
            <div className="report-detail-info__row">
              <dt>
                <BuildingOffice2Icon aria-hidden />
                Agency
              </dt>
              <dd>{detail.assignedAgency}</dd>
            </div>
            <div className="report-detail-info__row report-detail-info__row--severity">
              <dt>
                <ExclamationTriangleIcon aria-hidden />
                Incident severity
              </dt>
              <dd>
                <DetailSeverityBadge
                  severityKey={detail.incidentSeverityKey}
                  label={detail.incidentSeverityLabel}
                />
                {detail.needsAiReview ? (
                  <p className="report-detail-ai-review-hint" role="status">
                    AI classification should be verified before relying on the report type.
                  </p>
                ) : null}
              </dd>
            </div>
          </dl>
        </section>

        <div className="report-detail__col-right">
          <section className="report-detail-card reports-table-card">
            <h2 className="report-detail-card__heading">
              <PhotoIcon aria-hidden /> MEDIA EVIDENCE
            </h2>
            <div className="report-detail-media">
              <button
                type="button"
                className="report-detail-media__btn"
                onClick={() =>
                  setMediaPreview({
                    open: true,
                    type: detail.hasVideo && detail.videoUrl ? 'video' : 'image',
                    src:
                      detail.hasVideo && detail.videoUrl
                        ? detail.videoUrl
                        : detail.mediaUrl,
                  })
                }
                aria-label={detail.hasVideo ? 'Open report video' : 'Open report image'}
              >
                <img src={detail.mediaUrl} alt="" />
                {detail.hasVideo ? (
                  <span className="report-detail-media__play" aria-hidden>
                    <PlayIcon />
                  </span>
                ) : null}
              </button>
            </div>
          </section>

          <section className="report-detail-card report-detail-card--map reports-table-card">
            <h2 className="report-detail-card__heading">
              <MapPinIcon aria-hidden className="report-detail-card__heading-pin" />
              LOCATION ON MAP
            </h2>
            <div className="report-detail-map-wrap">
              <GoogleMapComponent
                height="280px"
                zoom={detail.mapZoom}
                center={detail.mapCenter}
                incidents={mapIncidents}
              />
            </div>
          </section>
        </div>
      </div>

      <footer className="report-detail__footer">
        <button
          type="button"
          className="reports-btn reports-btn--export"
          onClick={() => navigate(`/reports/${encodeURIComponent(id)}/update`)}
        >
          Update status
        </button>
      </footer>
      <MediaLightbox
        open={mediaPreview.open}
        type={mediaPreview.type}
        src={mediaPreview.src}
        onClose={() => setMediaPreview({ open: false, type: 'image', src: '' })}
      />
    </div>
  );
}
