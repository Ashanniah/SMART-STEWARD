import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';

export default function IncidentTypeDonutChart({ segments, height = 280 }) {
  const data = segments.filter((s) => s.value > 0);
  const hasAny = segments.some((s) => s.value > 0);

  if (!hasAny) {
    return (
      <div className="incident-analytics-chart-empty" style={{ minHeight: height }}>
        No reports submitted this month yet.
      </div>
    );
  }

  return (
    <div className="incident-analytics-donut-wrap">
      <ResponsiveContainer width="100%" height={height}>
        <PieChart margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            cx="50%"
            cy="48%"
            innerRadius="52%"
            outerRadius="78%"
            paddingAngle={2}
            stroke="#fff"
            strokeWidth={2}
          >
            {data.map((entry) => (
              <Cell key={entry.key} fill={entry.color} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value, name) => [`${value} reports`, name]}
            contentStyle={{
              background: '#fff',
              border: '1px solid #e2e8f0',
              borderRadius: '10px',
              fontSize: '0.875rem',
            }}
          />
        </PieChart>
      </ResponsiveContainer>
      <div className="incident-analytics-donut-legend">
        {segments.map((s) => (
          <span key={s.key} className="incident-analytics-donut-legend__item">
            <i className="incident-analytics-donut-legend__swatch" style={{ background: s.color }} />
            <span className="incident-analytics-donut-legend__text">
              {s.name} <strong>{s.percent}%</strong>
            </span>
          </span>
        ))}
      </div>
    </div>
  );
}
