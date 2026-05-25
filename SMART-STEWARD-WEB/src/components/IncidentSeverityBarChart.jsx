import {
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  LabelList,
  ResponsiveContainer,
} from 'recharts';

/**
 * Vertical bar chart for severity breakdown.
 *
 * Severity is an *ordinal* scale (Critical → High → Medium → Low), so a bar
 * chart communicates ranking and magnitude better than a donut would. Bars
 * are rendered left-to-right in priority order — Critical first — and the
 * `Pending classification` bar sits last with a muted color so it never
 * visually competes with real severity buckets.
 *
 * All categories are rendered — even those with `value === 0` — so the
 * horizontal alignment of bars is stable as data changes day-to-day.
 * Zero-value bars show only a tick on the axis and a `0` label.
 */
export default function IncidentSeverityBarChart({
  segments,
  height = 280,
  emptyMessage = 'No reports in this date range.',
}) {
  const hasAny = segments.some((s) => s.value > 0);
  if (!hasAny) {
    return (
      <div className="incident-analytics-chart-empty" style={{ minHeight: height }}>
        {emptyMessage}
      </div>
    );
  }

  const maxValue = Math.max(...segments.map((s) => s.value), 1);
  const yMax = Math.max(maxValue + 1, Math.ceil(maxValue * 1.25));

  return (
    <div className="severity-bar-chart">
      <ResponsiveContainer width="100%" height={height}>
        <BarChart
          data={segments}
          margin={{ top: 24, right: 16, bottom: 16, left: -8 }}
          barCategoryGap={24}
        >
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
          <XAxis
            type="category"
            dataKey="name"
            interval={0}
            axisLine={false}
            tickLine={false}
            tick={{ fill: '#334155', fontSize: 12, fontWeight: 600 }}
            tickMargin={8}
          />
          <YAxis
            type="number"
            domain={[0, yMax]}
            allowDecimals={false}
            axisLine={false}
            tickLine={false}
            tick={{ fill: '#94a3b8', fontSize: 11 }}
            width={36}
          />
          <Tooltip
            cursor={{ fill: 'rgba(148, 163, 184, 0.12)' }}
            contentStyle={{
              background: '#fff',
              border: '1px solid #e2e8f0',
              borderRadius: '10px',
              fontSize: '0.875rem',
            }}
            formatter={(value, _name, ctx) => {
              const pct = ctx?.payload?.percent ?? 0;
              const noun = value === 1 ? 'report' : 'reports';
              return [`${value} ${noun} (${pct}%)`, ctx?.payload?.name ?? ''];
            }}
            labelFormatter={() => ''}
          />
          <Bar dataKey="value" radius={[6, 6, 0, 0]} maxBarSize={56}>
            {segments.map((s) => (
              <Cell key={s.key} fill={s.color} />
            ))}
            <LabelList
              dataKey="value"
              position="top"
              content={(props) => {
                // Read the value and payload directly from the props recharts
                // hands us instead of looking back into the `segments` array.
                // Recharts' `index` is the position in the *rendered* list,
                // which doesn't line up with our source array when some bars
                // have value === 0 (it was previously labelling the High bar
                // with the Critical bar's "0" because the index lookup
                // pointed at the wrong segment).
                const { x, y, width, value, payload } = props;
                const segValue = Number(value) || 0;
                const percent = payload?.percent ?? 0;
                const text =
                  segValue === 0 ? '0' : `${segValue} (${percent}%)`;
                return (
                  <text
                    x={Number(x) + Number(width) / 2}
                    y={Number(y) - 6}
                    textAnchor="middle"
                    fill="#475569"
                    fontSize={12}
                    fontWeight={600}
                  >
                    {text}
                  </text>
                );
              }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
