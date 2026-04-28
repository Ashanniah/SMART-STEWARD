import StatCard from '../components/StatCard';
import CitizenReportCard from '../components/CitizenReportCard';
import IncidentChart from '../components/IncidentChart';
import GoogleMapComponent from '../components/GoogleMap';

const stats = [
  { label: 'Active Incidents', value: '14', change: '+2', direction: 'up' },
  { label: 'Pending Reports', value: '08', change: '-15%', direction: 'down' },
  { label: 'Avg. Response Time', value: '12m', change: '-3m', direction: 'down' },
  { label: 'Resolved Today', value: '32', change: '+7', direction: 'up' },
];

const reports = [
  {
    title: 'Grass fire near Phase 3',
    reporter: 'Maria S.',
    time: '12 mins ago',
    description: 'Ongoing fire hazard near residential area, smoke spreading rapidly towards the highway.',
    status: 'IN-PROGRESS',
  },
  {
    title: 'Faulty Electrical zPost',
    reporter: 'Luzviminda C.',
    time: '45 mins ago',
    description: 'Sparks from electric post near public market, risk of structural fire.',
    status: 'PENDING',
  },
  {
    title: 'Forest fire threat',
    reporter: 'Ranger Joe',
    time: '1h ago',
    description: 'Heavy smoke detected from mountain area. Immediate BFP response requested.',
    status: 'PENDING',
  },
];

export default function Dashboard() {
  return (
    <div className="fade-in">
      <div className="stat-cards">
        {stats.map((s, i) => (
          <StatCard key={i} {...s} />
        ))}
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-left">
          <div className="reports-section">
            <div className="reports-header">
              <div>
                <h3>Citizen Reports</h3>
                <p>Real-time incoming incident data</p>
              </div>
              <div className="reports-actions">
                <button className="btn-outline" id="filter-btn">🔘 Filter</button>
                <button className="btn-outline" id="export-btn">↑ Export</button>
              </div>
            </div>

            {reports.map((r, i) => (
              <CitizenReportCard key={i} {...r} />
            ))}

            <div className="view-all">View All Reports</div>
          </div>
        </div>

        <div className="dashboard-right">
          <div className="map-card">
            <div className="map-card-header">
              <span className="dot"></span>
              <h3>Sector Surveillance Map</h3>
            </div>
            <GoogleMapComponent height="220px" />
          </div>

          <div className="chart-card">
            <h3>Incident Volume Trend</h3>
            <IncidentChart height={180} />
          </div>
        </div>
      </div>
    </div>
  );
}
