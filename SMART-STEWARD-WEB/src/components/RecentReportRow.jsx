import { Link } from 'react-router-dom';
import {
  BuildingOffice2Icon,
  CalendarDaysIcon,
  CheckBadgeIcon,
  ClockIcon,
  DocumentTextIcon,
  MapPinIcon,
} from '@heroicons/react/24/outline';
import { PlayIcon as PlaySolidIcon } from '@heroicons/react/24/solid';
import { viewerScopedAgencyLabel } from '../utils/agencyScope';

const STATUS_CLASS = {
  pending: 'recent-report-card__status--pending',
  review: 'recent-report-card__status--review',
  in_progress: 'recent-report-card__status--review',
  resolved: 'recent-report-card__status--resolved',
  rejected: 'recent-report-card__status--rejected',
};

function FieldLabel({ Icon, children }) {
  return (
    <span className="recent-report-card__label">
      <Icon className="recent-report-card__label-icon" aria-hidden />
      {children}
    </span>
  );
}

export default function RecentReportRow({
  docId,
  imageUrl,
  hasVideo = false,
  onMediaClick,
  reportType,
  dateSubmitted,
  timeOfReport,
  location,
  assignedAgency = '',
  viewerAgencyKey = '',
  statusLabel = 'Pending',
  statusKey = 'pending',
}) {
  const statusClass = STATUS_CLASS[statusKey] ?? STATUS_CLASS.pending;
  const agencyLabel =
    viewerScopedAgencyLabel(assignedAgency, viewerAgencyKey) ||
    String(assignedAgency ?? '').trim();

  return (
    <article className="recent-report-card">
      <div className="recent-report-card__main">
        <div className="recent-report-card__media">
          <button
            type="button"
            className="recent-report-card__thumb"
            onClick={onMediaClick}
            aria-label={hasVideo ? 'Open report video' : 'Open report image'}
            style={imageUrl ? { backgroundImage: `url(${imageUrl})` } : undefined}
          >
            <span className="recent-report-card__thumb-hit" aria-hidden />
          </button>
          {hasVideo ? (
            <span className="recent-report-card__play" title="Includes video" aria-hidden>
              <PlaySolidIcon />
            </span>
          ) : null}
        </div>

        <div className="recent-report-card__body">
          <div className="recent-report-card__status-row">
            <FieldLabel Icon={CheckBadgeIcon}>Current Status</FieldLabel>
            <span className={`recent-report-card__status ${statusClass}`}>{statusLabel}</span>
          </div>

          <dl className="recent-report-card__fields recent-report-card__details">
            <div className="recent-report-card__field">
              <dt>
                <FieldLabel Icon={DocumentTextIcon}>Report Type</FieldLabel>
              </dt>
              <dd>{reportType}</dd>
            </div>
            <div className="recent-report-card__field">
              <dt>
                <FieldLabel Icon={CalendarDaysIcon}>Date Submitted</FieldLabel>
              </dt>
              <dd>{dateSubmitted}</dd>
            </div>
            <div className="recent-report-card__field">
              <dt>
                <FieldLabel Icon={ClockIcon}>Time of Report</FieldLabel>
              </dt>
              <dd>{timeOfReport}</dd>
            </div>
            <div className="recent-report-card__field recent-report-card__field--location">
              <dt>
                <FieldLabel Icon={MapPinIcon}>Location</FieldLabel>
              </dt>
              <dd>{location}</dd>
            </div>
            {agencyLabel ? (
              <div className="recent-report-card__field recent-report-card__field--agencies">
                <dt>
                  <FieldLabel Icon={BuildingOffice2Icon}>Assigned Agency</FieldLabel>
                </dt>
                <dd>{agencyLabel}</dd>
              </div>
            ) : null}
          </dl>
        </div>
      </div>

      {docId ? (
        <Link to={`/reports/${docId}`} className="recent-report-card__detail-link">
          View report details
        </Link>
      ) : null}
    </article>
  );
}
