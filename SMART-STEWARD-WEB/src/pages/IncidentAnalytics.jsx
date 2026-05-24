import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ChartBarIcon,
  ChevronDownIcon,
  ChartPieIcon,
  MapPinIcon,
  CalendarDaysIcon,
  ClipboardDocumentListIcon,
  FireIcon,
  ClockIcon,
} from '@heroicons/react/24/outline';
import IncidentVolumeTrendChart from '../components/IncidentVolumeTrendChart';
import IncidentTypeDonutChart from '../components/IncidentTypeDonutChart';
import IncidentAnalyticsLocationList from '../components/IncidentAnalyticsLocationList';
import { useReportsData } from '../context/ReportsDataContext';
import {
  buildAnalyticsInsights,
  buildIncidentTypeSegments,
  buildStatusBreakdown,
  buildTopLocations,
  buildVolumeTrendSeries,
  countReportsInRange,
  formatInsightLocation,
  getAnalyticsRangeMeta,
  TREND_RANGE_OPTIONS,
} from '../utils/incidentAnalytics';

export default function IncidentAnalytics() {
  const { reports, loading, error } = useReportsData();
  const [rangeIndex, setRangeIndex] = useState(0);
  const range = TREND_RANGE_OPTIONS[rangeIndex] ?? TREND_RANGE_OPTIONS[0];
  const rangeMeta = useMemo(() => getAnalyticsRangeMeta(range.days), [range.days]);

  const trend = useMemo(
    () => buildVolumeTrendSeries(reports, range.days),
    [reports, range.days]
  );

  const statusSegments = useMemo(
    () => buildStatusBreakdown(reports, range.days),
    [reports, range.days]
  );
  const typeSegments = useMemo(
    () => buildIncidentTypeSegments(reports, range.days),
    [reports, range.days]
  );
  const topLocations = useMemo(
    () => buildTopLocations(reports, range.days, 8),
    [reports, range.days]
  );
  const insights = useMemo(
    () => buildAnalyticsInsights(reports, range.days),
    [reports, range.days]
  );

  const reportsInRange = useMemo(
    () => countReportsInRange(reports, range.days),
    [reports, range.days]
  );

  const showEmptyBanner = !loading && reports.length === 0;
  const emptyInRange = rangeMeta.emptyInRange;

  return (
    <div className="denr-dashboard incident-analytics-page fade-in">
      {error ? (
        <p className="denr-dashboard__firestore-msg" role="alert">
          {error}
        </p>
      ) : null}

      {showEmptyBanner ? (
        <div className="denr-dashboard__muted incident-analytics-banner" role="status">
          <strong>No analytics data yet.</strong> Charts will populate when citizens submit reports.
          For individual reports, use the <Link to="/dashboard">Dashboard</Link> or{' '}
          <Link to="/reports">Reports</Link> page.
        </div>
      ) : null}

      {loading && reports.length === 0 ? (
        <p className="denr-dashboard__muted">Loading analytics…</p>
      ) : null}

      {!showEmptyBanner ? (
        <section className="denr-panel incident-analytics-toolbar">
          <div className="incident-analytics-toolbar__text">
            <h3 className="denr-panel__title denr-panel__title--branded">
              <CalendarDaysIcon aria-hidden />
              Analysis period
            </h3>
            <p className="denr-panel__subtitle">
              All charts below use the same date range to identify activities, locations, and
              patterns.
            </p>
          </div>
          <div className="incident-analytics-select-wrap">
            <select
              className="incident-analytics-select"
              value={rangeIndex}
              onChange={(e) => setRangeIndex(Number(e.target.value))}
              aria-label="Analysis date range"
            >
              {TREND_RANGE_OPTIONS.map((opt, i) => (
                <option key={opt.days} value={i}>
                  {opt.label}
                </option>
              ))}
            </select>
            <ChevronDownIcon className="incident-analytics-select__icon" aria-hidden />
          </div>
        </section>
      ) : null}

      {insights ? (
        <section className="incident-analytics-insights" aria-label="Key findings">
          <h4 className="incident-analytics-insights__heading">Key findings</h4>
          <ul className="incident-analytics-insights__grid">
            <li className="incident-analytics-insights__item">
              <span className="incident-analytics-insights__icon-wrap" aria-hidden>
                <ClipboardDocumentListIcon />
              </span>
              <div className="incident-analytics-insights__body">
                <span className="incident-analytics-insights__label">Reports in period</span>
                <span className="incident-analytics-insights__value">
                  {insights.total}
                  <span className="incident-analytics-insights__hint">
                    {rangeMeta.label.toLowerCase()}
                  </span>
                </span>
              </div>
            </li>
            {insights.topActivity !== '—' ? (
              <li className="incident-analytics-insights__item">
                <span className="incident-analytics-insights__icon-wrap" aria-hidden>
                  <FireIcon />
                </span>
                <div className="incident-analytics-insights__body">
                  <span className="incident-analytics-insights__label">Most common activity</span>
                  <span className="incident-analytics-insights__value">
                    {insights.topActivity}
                    <span className="incident-analytics-insights__hint">
                      {insights.topActivityShare}% of reports
                    </span>
                  </span>
                </div>
              </li>
            ) : null}
            {insights.topLocation !== '—' ? (
              <li className="incident-analytics-insights__item">
                <span className="incident-analytics-insights__icon-wrap" aria-hidden>
                  <MapPinIcon />
                </span>
                <div className="incident-analytics-insights__body">
                  <span className="incident-analytics-insights__label">Top location</span>
                  <span className="incident-analytics-insights__value incident-analytics-insights__value--location">
                    {formatInsightLocation(insights.topLocation)}
                    <span className="incident-analytics-insights__hint">
                      {insights.topLocationCount} report
                      {insights.topLocationCount === 1 ? '' : 's'}
                    </span>
                  </span>
                </div>
              </li>
            ) : null}
            {insights.pending > 0 ? (
              <li className="incident-analytics-insights__item">
                <span className="incident-analytics-insights__icon-wrap" aria-hidden>
                  <ClockIcon />
                </span>
                <div className="incident-analytics-insights__body">
                  <span className="incident-analytics-insights__label">Awaiting review</span>
                  <span className="incident-analytics-insights__value">
                    {insights.pending}
                    <span className="incident-analytics-insights__hint">pending</span>
                  </span>
                </div>
              </li>
            ) : null}
          </ul>
        </section>
      ) : null}

      <section className="denr-panel denr-panel--analytics-trend">
        <div className="denr-panel__head">
          <h3 className="denr-panel__title denr-panel__title--branded">
            <ChartBarIcon aria-hidden />
            Incident volume trend
          </h3>
        </div>
        <p className="denr-panel__subtitle">{rangeMeta.trendSubtitle}</p>
        <div className="incident-analytics-card__chart">
          <IncidentVolumeTrendChart
            data={trend.data}
            series={trend.series}
            height={340}
            emptyMessage={
              reportsInRange === 0 ? emptyInRange : 'No data in this date range.'
            }
          />
        </div>
      </section>

      <div className="denr-analytics-charts">
        <section className="denr-panel">
          <div className="denr-panel__head">
            <h3 className="denr-panel__title denr-panel__title--branded">
              <ChartPieIcon aria-hidden />
              Status breakdown
            </h3>
          </div>
          <p className="denr-panel__subtitle">{rangeMeta.periodSubtitle}</p>
          <div className="incident-analytics-card__chart incident-analytics-card__chart--donut">
            <IncidentTypeDonutChart
              segments={statusSegments}
              height={260}
              emptyMessage={emptyInRange}
            />
          </div>
        </section>

        <section className="denr-panel">
          <div className="denr-panel__head">
            <h3 className="denr-panel__title denr-panel__title--branded">
              <ChartPieIcon aria-hidden />
              Common illegal activities
            </h3>
          </div>
          <p className="denr-panel__subtitle">{rangeMeta.periodSubtitle}</p>
          <div className="incident-analytics-card__chart incident-analytics-card__chart--donut">
            <IncidentTypeDonutChart
              segments={typeSegments}
              height={260}
              emptyMessage={emptyInRange}
            />
          </div>
        </section>
      </div>

      <section className="denr-panel denr-panel--analytics-locations">
        <div className="denr-panel__head">
          <h3 className="denr-panel__title denr-panel__title--branded">
            <MapPinIcon aria-hidden />
            Frequent locations
          </h3>
        </div>
        <p className="denr-panel__subtitle">
          {rangeMeta.periodSubtitle} · where reports are concentrated
        </p>
        <IncidentAnalyticsLocationList locations={topLocations} emptyMessage={emptyInRange} />
      </section>
    </div>
  );
}
