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

export default function IncidentVolumeTrendChart({
  data,
  series = [],
  height = 320,
  emptyMessage = 'No reports in this date range.',
}) {
  const activeSeries = series.length > 0 ? series : [{ key: 'total', name: 'Reports', color: '#21734b' }];

  const maxSingle = data.reduce((m, row) => {
    let max = m;
    for (const s of activeSeries) {
      max = Math.max(max, Number(row[s.key] ?? 0));
    }
    return max;
  }, 0);

  const totalInRange = data.reduce((sum, row) => {
    let day = 0;
    for (const s of activeSeries) {
      day += Number(row[s.key] ?? 0);
    }
    return sum + day;
  }, 0);

  const yMax = Math.max(4, Math.ceil(Math.max(maxSingle, 1) * 1.25));
  const step = yMax <= 8 ? 2 : yMax <= 20 ? 4 : 5;
  const yTicks = [];
  for (let v = 0; v <= yMax; v += step) {
    yTicks.push(v);
  }
  if (yTicks[yTicks.length - 1] < yMax) yTicks.push(yMax);

  if (!data.length || totalInRange === 0) {
    return (
      <div className="incident-analytics-chart-empty" style={{ height }}>
        {emptyMessage}
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={height}>
      <ComposedChart data={data} margin={{ top: 12, right: 8, left: -12, bottom: 4 }}>
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
          formatter={(value) => (
            <span style={{ color: '#475569', fontSize: '0.82rem' }}>{value}</span>
          )}
        />
        {activeSeries.map((s) => (
          <Area
            key={s.key}
            name={s.name}
            type="monotone"
            dataKey={s.key}
            stackId="volume"
            stroke={s.color}
            strokeWidth={2}
            fill={s.color}
            fillOpacity={0.22}
            dot={{ r: 3, strokeWidth: 1, stroke: '#fff', fill: s.color }}
            activeDot={{ r: 5 }}
          />
        ))}
      </ComposedChart>
    </ResponsiveContainer>
  );
}
