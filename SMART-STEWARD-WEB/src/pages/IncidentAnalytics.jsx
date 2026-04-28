import StatCard from '../components/StatCard';
import IncidentChart from '../components/IncidentChart';

const stats = [
  { label: 'Active Incidents', value: '14', change: '+2', direction: 'up' },
  { label: 'Pending Reports', value: '08', change: '-15%', direction: 'down' },
  { label: 'Avg. Response Time', value: '12m', change: '-3m', direction: 'down' },
  { label: 'Resolved Today', value: '32', change: '+7', direction: 'up' },
];

export default function IncidentAnalytics() {
  return (
    <div className="fade-in">
      <div className="stat-cards">
        {stats.map((s, i) => (
          <StatCard key={i} {...s} />
        ))}
      </div>

      <div className="chart-card-large">
        <h3>Incident Volume Trend</h3>
        <IncidentChart height={420} />
      </div>
    </div>
  );
}
