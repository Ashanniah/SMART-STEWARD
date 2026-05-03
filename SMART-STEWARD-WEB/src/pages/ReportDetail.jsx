import { useMemo } from 'react';
import { useNavigate, useParams, Navigate } from 'react-router-dom';
import {
  ArrowLeftIcon,
  FireIcon,
  MapPinIcon,
  DocumentTextIcon,
  UserIcon,
  BuildingOffice2Icon,
  PhotoIcon,
} from '@heroicons/react/24/outline';
import GoogleMapComponent from '../components/GoogleMap';
import { getReportDetail } from '../data/reportsMock';

function StatusHeaderBadge({ status }) {
  const map = {
    pending: { label: 'PENDING', className: 'report-detail__pill report-detail__pill--pending' },
    review: { label: 'UNDER REVIEW', className: 'report-detail__pill report-detail__pill--review' },
    resolved: { label: 'RESOLVED', className: 'report-detail__pill report-detail__pill--resolved' },
  };
  const item = map[status] ?? map.pending;
  return <span className={item.className}>{item.label}</span>;
}

export default function ReportDetail() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const id = reportId ? decodeURIComponent(reportId) : '';

  const detail = useMemo(() => (id ? getReportDetail(id) : null), [id]);

  if (!detail) {
    return <Navigate to="/reports" replace />;
  }

  return (
    <div className="report-detail fade-in">
      <button
        type="button"
        className="report-detail__back"
        onClick={() => navigate('/reports')}
      >
        <ArrowLeftIcon aria-hidden />
        Back to reports
      </button>

      <header className="report-detail__header">
        <div className="report-detail__header-main">
          <h1 className="report-detail__title">REPORT DETAILS (VIEW)</h1>
          <StatusHeaderBadge status={detail.status} />
        </div>
        <div className="report-detail__meta">
          <span className="report-detail__meta-id">
            Report ID: <strong>{detail.deptReportId}</strong>
          </span>
          <span className="report-detail__meta-date">
            Submitted on {detail.submittedAt}
          </span>
        </div>
      </header>

      <div className="report-detail__grid">
        <section className="report-detail-card report-detail-card--info">
          <h2 className="report-detail-card__heading">REPORT INFORMATION</h2>
          <dl className="report-detail-info">
            <div className="report-detail-info__row">
              <dt>
                <FireIcon aria-hidden /> Report Type
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
                <BuildingOffice2Icon aria-hidden /> Assigned Agency
              </dt>
              <dd>{detail.assignedAgency}</dd>
            </div>
            <div className="report-detail-info__row report-detail-info__row--confidence">
              <dt>Confidence Level</dt>
              <dd>
                <div className="report-detail-confidence">
                  <span className="report-detail-confidence__value">{detail.confidence}%</span>
                  <div
                    className="report-detail-confidence__track"
                    role="progressbar"
                    aria-valuenow={detail.confidence}
                    aria-valuemin={0}
                    aria-valuemax={100}
                  >
                    <div
                      className="report-detail-confidence__fill"
                      style={{ width: `${detail.confidence}%` }}
                    />
                  </div>
                </div>
              </dd>
            </div>
          </dl>
        </section>

        <div className="report-detail__col-right">
          <section className="report-detail-card">
            <h2 className="report-detail-card__heading">
              <PhotoIcon aria-hidden /> MEDIA EVIDENCE
            </h2>
            <div className="report-detail-media">
              <img src={detail.mediaUrl} alt="" />
            </div>
          </section>

          <section className="report-detail-card report-detail-card--map">
            <h2 className="report-detail-card__heading">
              <MapPinIcon aria-hidden className="report-detail-card__heading-pin" />
              LOCATION ON MAP
            </h2>
            <div className="report-detail-map-wrap">
              <GoogleMapComponent
                height="280px"
                zoom={detail.mapZoom}
                center={detail.mapCenter}
              />
            </div>
          </section>
        </div>
      </div>

      <footer className="report-detail__footer">
        <button
          type="button"
          className="report-detail__update"
          onClick={() => navigate(`/reports/${encodeURIComponent(id)}/update`)}
        >
          Update
        </button>
      </footer>
    </div>
  );
}
