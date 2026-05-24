import { Link } from 'react-router-dom';
import { formatReportDateOnly, statusToLabel } from '../utils/normalizeReportDoc';

const STATUS_CLASS = {
  pending: 'recent-report-card__status--pending',
  review: 'recent-report-card__status--review',
  in_progress: 'recent-report-card__status--review',
  resolved: 'recent-report-card__status--resolved',
  rejected: 'recent-report-card__status--rejected',
};

export default function IncidentAnalyticsRecentList({ reports }) {
  if (!reports.length) {
    return (
      <p className="incident-analytics-chart-empty incident-analytics-chart-empty--compact">
        No reports yet. Submissions from the mobile app will appear here.
      </p>
    );
  }

  return (
    <ul className="incident-analytics-recent">
      {reports.map((r) => {
        const statusKey = r.status ?? 'pending';
        const statusClass = STATUS_CLASS[statusKey] ?? STATUS_CLASS.pending;
        const dateLabel = formatReportDateOnly(r.createdAt);

        return (
          <li key={r.docId}>
            <Link to={`/reports/${encodeURIComponent(r.docId)}`} className="incident-analytics-recent__row">
              <span className="incident-analytics-recent__main">
                <strong>{r.activity}</strong>
                <span className="incident-analytics-recent__meta">
                  {r.id} · {dateLabel}
                </span>
                <span className="incident-analytics-recent__loc">{r.location}</span>
              </span>
              <span className={`recent-report-card__status ${statusClass}`}>
                {statusToLabel(statusKey)}
              </span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
