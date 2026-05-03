import { UserIcon } from '@heroicons/react/24/outline';

const STATUS_CLASS = {
  pending: 'recent-report-row__status--pending',
  review: 'recent-report-row__status--review',
  resolved: 'recent-report-row__status--resolved',
  rejected: 'recent-report-row__status--rejected',
};

export default function RecentReportRow({
  imageUrl,
  title,
  location,
  dateTime,
  statusLabel = 'Pending',
  statusKey = 'pending',
}) {
  const statusClass = STATUS_CLASS[statusKey] ?? STATUS_CLASS.pending;

  return (
    <div className="recent-report-row">
      <div
        className="recent-report-row__thumb"
        style={
          imageUrl
            ? { backgroundImage: `url(${imageUrl})` }
            : undefined
        }
      />
      <div className="recent-report-row__body">
        <h4 className="recent-report-row__title">{title}</h4>
        <p className="recent-report-row__meta">{location}</p>
        <p className="recent-report-row__time">{dateTime}</p>
        <div className="recent-report-row__anon">
          <UserIcon aria-hidden />
          Anonymous
        </div>
      </div>
      <div className="recent-report-row__actions">
        <span className={`recent-report-row__status ${statusClass}`}>
          {statusLabel}
        </span>
      </div>
    </div>
  );
}
