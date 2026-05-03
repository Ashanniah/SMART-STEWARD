import { UserIcon } from '@heroicons/react/24/outline';

export default function RecentReportRow({
  imageUrl,
  title,
  location,
  dateTime,
  statusLabel = 'Pending',
  statusKey = 'pending',
}) {
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
        <span
          className={`recent-report-row__status recent-report-row__status--${statusKey}`}
        >
          {statusLabel}
        </span>
      </div>
    </div>
  );
}
