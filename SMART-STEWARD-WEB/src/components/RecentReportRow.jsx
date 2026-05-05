import { UserIcon } from '@heroicons/react/24/outline';
import { PlayIcon } from '@heroicons/react/24/solid';

export default function RecentReportRow({
  imageUrl,
  hasVideo = false,
  onMediaClick,
  title,
  location,
  dateTime,
  statusLabel = 'Pending',
  statusKey = 'pending',
}) {
  return (
    <div className="recent-report-row">
      <div className="recent-report-row__thumb-wrap">
        <button
          type="button"
          className="recent-report-row__thumb"
          onClick={onMediaClick}
          aria-label={hasVideo ? 'Open report video' : 'Open report image'}
          style={
            imageUrl
              ? { backgroundImage: `url(${imageUrl})` }
              : undefined
          }
        >
          <span className="recent-report-row__thumb-hit" aria-hidden />
        </button>
        {hasVideo ? (
          <span
            className="recent-report-row__play-badge"
            title="Includes video"
            aria-label="Video attached"
          >
            <PlayIcon className="recent-report-row__play-icon" aria-hidden />
          </span>
        ) : null}
      </div>
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
