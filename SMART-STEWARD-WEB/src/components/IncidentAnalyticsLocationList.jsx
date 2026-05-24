import { MapPinIcon } from '@heroicons/react/24/outline';
import { formatInsightLocation } from '../utils/incidentAnalytics';

export default function IncidentAnalyticsLocationList({
  locations,
  emptyMessage = 'No location data in this period.',
}) {
  if (!locations.length) {
    return (
      <p className="incident-analytics-chart-empty incident-analytics-chart-empty--compact">
        {emptyMessage}
      </p>
    );
  }

  const total = locations.reduce((sum, item) => sum + item.count, 0);
  const showBars = locations.length > 1;

  return (
    <ul className="incident-analytics-locations">
      {locations.map((item, index) => {
        const share = total > 0 ? Math.round((item.count / total) * 100) : 0;
        const displayName = formatInsightLocation(item.location);
        const fullAddress = item.location;
        const reportLabel = item.count === 1 ? 'report' : 'reports';

        return (
          <li
            key={`${fullAddress}-${index}`}
            className="incident-analytics-locations__card"
          >
            <div className="incident-analytics-locations__card-main">
              <span className="incident-analytics-locations__rank" aria-hidden>
                {index + 1}
              </span>
              <span className="incident-analytics-locations__icon-wrap" aria-hidden>
                <MapPinIcon />
              </span>
              <div className="incident-analytics-locations__body">
                <span
                  className="incident-analytics-locations__name"
                  title={fullAddress !== displayName ? fullAddress : undefined}
                >
                  {displayName}
                </span>
                {fullAddress !== displayName ? (
                  <span className="incident-analytics-locations__full" title={fullAddress}>
                    {fullAddress}
                  </span>
                ) : null}
                <div className="incident-analytics-locations__meta">
                  <span className="incident-analytics-locations__count">
                    {item.count} {reportLabel}
                  </span>
                  <span className="incident-analytics-locations__meta-sep" aria-hidden>
                    ·
                  </span>
                  <span className="incident-analytics-locations__share">
                    {share}% of reports in period
                  </span>
                </div>
              </div>
            </div>
            {showBars ? (
              <div
                className="incident-analytics-locations__bar"
                role="presentation"
                aria-hidden
              >
                <span
                  className="incident-analytics-locations__bar-fill"
                  style={{ width: `${Math.max(share, 4)}%` }}
                />
              </div>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
