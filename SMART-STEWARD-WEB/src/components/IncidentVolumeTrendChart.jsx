import {
  CartesianGrid,
  ComposedChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { TREND_SERIES_COLORS } from '../utils/incidentAnalytics';

export default function IncidentVolumeTrendChart({ data, height = 320 }) {
  const maxSingle = data.reduce(
    (m, row) => Math.max(m, row.burning, row.dumping, row.otherViolations),
    0
  );
  const yMax = Math.max(10, Math.ceil(maxSingle * 1.2));
  const step = yMax <= 10 ? 2 : yMax <= 24 ? 4 : 5;
  const yTicks = [];
  for (let v = 0; v <= yMax; v += step) {
    yTicks.push(v);
  }
  if (yTicks[yTicks.length - 1] < yMax) yTicks.push(yMax);

  if (!data.length) {
    return (
      <div className="incident-analytics-chart-empty" style={{ height }}>
        No data in this date range.
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <ComposedChart data={data} margin={{ top: 12, right: 8, left: -12, bottom: 4 }}>
        <defs>
          <linearGradient id="volBurning" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={TREND_SERIES_COLORS.burning} stopOpacity={0.35} />
            <stop offset="100%" stopColor={TREND_SERIES_COLORS.burning} stopOpacity={0.05} />
          </linearGradient>
          <linearGradient id="volDumping" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={TREND_SERIES_COLORS.dumping} stopOpacity={0.35} />
            <stop offset="100%" stopColor={TREND_SERIES_COLORS.dumping} stopOpacity={0.05} />
          </linearGradient>
          <linearGradient id="volOther" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={TREND_SERIES_COLORS.otherViolations} stopOpacity={0.35} />
            <stop offset="100%" stopColor={TREND_SERIES_COLORS.otherViolations} stopOpacity={0.05} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
        <XAxis
          dataKey="label"
          axisLine={false}
          tickLine={false}
          tick={{ fill: '#64748b', fontSize: 12 }}
          dy={4}
        />
        <YAxis
          domain={[0, yMax]}
          ticks={yTicks}
          axisLine={false}
          tickLine={false}
          tick={{ fill: '#94a3b8', fontSize: 11 }}
          width={36}
          allowDecimals={false}
        />
        <Tooltip
          contentStyle={{
            background: '#fff',
            border: '1px solid #e2e8f0',
            borderRadius: '10px',
            fontSize: '0.875rem',
          }}
          labelFormatter={(_, payload) =>
            payload?.[0]?.payload?.dateLabel ? String(payload[0].payload.dateLabel) : ''
          }
        />
        <Legend
          wrapperStyle={{ paddingTop: '0.5rem' }}
          formatter={(value) => <span style={{ color: '#475569', fontSize: '0.82rem' }}>{value}</span>}
        />
        <Area
          name="Open burning"
          type="monotone"
          dataKey="burning"
          stackId="a"
          stroke={TREND_SERIES_COLORS.burning}
          strokeWidth={2}
          fill="url(#volBurning)"
          dot={{ r: 3, strokeWidth: 1, stroke: '#fff', fill: TREND_SERIES_COLORS.burning }}
          activeDot={{ r: 5 }}
        />
        <Area
          name="Illegal dumping"
          type="monotone"
          dataKey="dumping"
          stackId="b"
          stroke={TREND_SERIES_COLORS.dumping}
          strokeWidth={2}
          fill="url(#volDumping)"
          dot={{ r: 3, strokeWidth: 1, stroke: '#fff', fill: TREND_SERIES_COLORS.dumping }}
          activeDot={{ r: 5 }}
        />
        <Area
          name="Other violations"
          type="monotone"
          dataKey="otherViolations"
          stackId="c"
          stroke={TREND_SERIES_COLORS.otherViolations}
          strokeWidth={2}
          fill="url(#volOther)"
          dot={{ r: 3, strokeWidth: 1, stroke: '#fff', fill: TREND_SERIES_COLORS.otherViolations }}
          activeDot={{ r: 5 }}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
