import { useMemo, useState } from 'react';
import { ChevronDownIcon } from '@heroicons/react/24/outline';
import StatCard from '../components/StatCard';
import IncidentVolumeTrendChart from '../components/IncidentVolumeTrendChart';
import IncidentTypeDonutChart from '../components/IncidentTypeDonutChart';
import { useReportsData } from '../context/ReportsDataContext';
import { toJsDate } from '../utils/normalizeReportDoc';
import {
  buildVolumeTrendSeries,
  buildMonthlyTypeBreakdown,
  TREND_RANGE_OPTIONS,
} from '../utils/incidentAnalytics';

export default function IncidentAnalytics() {
  const { reports, loading, error, counts } = useReportsData();
  const [rangeIndex, setRangeIndex] = useState(0);
  const range = TREND_RANGE_OPTIONS[rangeIndex] ?? TREND_RANGE_OPTIONS[0];

  const trendData = useMemo(
    () => buildVolumeTrendSeries(reports, range.days),
    [reports, range.days]
  );

  const donutSegments = useMemo(() => buildMonthlyTypeBreakdown(reports), [reports]);

  const summaryStats = useMemo(() => {
    const now = new Date();
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    let active = 0;
    let resolvedMonth = 0;
    let newThisMonth = 0;

    for (const r of reports) {
      if (r.status === 'pending' || r.status === 'review' || r.status === 'in_progress') {
        active += 1;
      }
      const created = r.createdAt instanceof Date ? r.createdAt : null;
      if (created && created >= monthStart) {
        newThisMonth += 1;
      }
      if (r.status === 'resolved') {
        const updated = toJsDate(r.raw?.statusUpdatedAt) ?? created;
        if (updated instanceof Date && updated >= monthStart) {
          resolvedMonth += 1;
        }
      }
    }

    return [
      { label: 'Active incidents', value: String(active) },
      { label: 'Pending reports', value: String(counts.pending) },
      { label: 'Resolved this month', value: String(resolvedMonth) },
      { label: 'New reports (month)', value: String(newThisMonth) },
    ];
  }, [reports, counts]);

  const monthTitle = useMemo(
    () =>
      new Date().toLocaleDateString('en-US', { month: 'long', year: 'numeric' }),
    []
  );

  return (
    <div className="incident-analytics-page fade-in">
      {error ? (
        <p className="incident-analytics-page__alert" role="alert">
          {error}
        </p>
      ) : null}

      <div className="incident-analytics-page__stats stat-cards stat-cards--analytics">
        {loading && reports.length === 0 ? (
          <p className="incident-analytics-page__loading">Loading analytics…</p>
        ) : (
          summaryStats.map((s) => <StatCard key={s.label} label={s.label} value={s.value} />)
        )}
      </div>

      <div className="incident-analytics-page__charts">
        <section className="incident-analytics-card">
          <div className="incident-analytics-card__head">
            <div>
              <h2 className="incident-analytics-card__title">Incident volume trend</h2>
              <p className="incident-analytics-card__subtitle">{range.subtitle}</p>
            </div>
            <div className="incident-analytics-select-wrap">
              <select
                className="incident-analytics-select"
                value={rangeIndex}
                onChange={(e) => setRangeIndex(Number(e.target.value))}
                aria-label="Date range for volume trend"
              >
                {TREND_RANGE_OPTIONS.map((opt, i) => (
                  <option key={opt.days} value={i}>
                    {opt.label}
                  </option>
                ))}
              </select>
              <ChevronDownIcon className="incident-analytics-select__icon" aria-hidden />
            </div>
          </div>
          <div className="incident-analytics-card__chart">
            <IncidentVolumeTrendChart data={trendData} height={340} />
          </div>
        </section>

        <section className="incident-analytics-card">
          <div className="incident-analytics-card__head">
            <div>
              <h2 className="incident-analytics-card__title">Incident type breakdown</h2>
              <p className="incident-analytics-card__subtitle">This month · {monthTitle}</p>
            </div>
          </div>
          <div className="incident-analytics-card__chart incident-analytics-card__chart--donut">
            <IncidentTypeDonutChart segments={donutSegments} height={260} />
          </div>
        </section>
      </div>
    </div>
  );
}
