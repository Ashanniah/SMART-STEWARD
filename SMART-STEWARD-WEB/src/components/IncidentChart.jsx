import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';

const data = [
  { day: 'M', value: 12 },
  { day: 'T', value: 28 },
  { day: 'W', value: 18 },
  { day: 'T', value: 32 },
  { day: 'F', value: 26 },
  { day: 'S', value: 38 },
  { day: 'S', value: 35 },
];

export default function IncidentChart({ height = 200 }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
        <defs>
          <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#9cdf5c" stopOpacity={0.35} />
            <stop offset="100%" stopColor="#7bc142" stopOpacity={0.05} />
          </linearGradient>
        </defs>
        <XAxis
          dataKey="day"
          axisLine={false}
          tickLine={false}
          tick={{ fill: '#a3c48a', fontSize: 14, fontWeight: 600 }}
        />
        <YAxis hide />
        <Tooltip
          contentStyle={{
            background: '#253d16',
            border: '1px solid #3a5c22',
            borderRadius: '8px',
            color: '#eef4e8',
            fontSize: '0.94rem',
          }}
        />
        <Area
          type="monotone"
          dataKey="value"
          stroke="#9cdf5c"
          strokeWidth={2.5}
          fill="url(#chartGradient)"
          dot={{ fill: '#9cdf5c', r: 5, strokeWidth: 2, stroke: '#253d16' }}
          activeDot={{ r: 7, strokeWidth: 2, stroke: '#9cdf5c' }}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
