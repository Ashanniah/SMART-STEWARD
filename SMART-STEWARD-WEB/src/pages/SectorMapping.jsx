import GoogleMapComponent from '../components/GoogleMap';

const hazardReports = [
  { id: 1, type: 'Garbage', location: 'Mabolo, Cebu City', lat: 10.3029, lng: 123.8995, reportedAt: '05/26, 10:00 AM', status: 'pending' },
  { id: 2, type: 'Garbage', location: 'Zapatera', lat: 10.2979, lng: 123.8935, reportedAt: '05/26, 09:45 AM', status: 'resolved' },
  { id: 3, type: 'Chemical Spill', location: 'South Road', lat: 10.2929, lng: 123.8975, reportedAt: '05/25, 02:20 PM', status: 'active' },
];

export default function SectorMapping() {
  const incidents = hazardReports.map(r => ({
    id: r.id,
    lat: r.lat,
    lng: r.lng,
    title: r.type,
    type: r.type,
    status: r.status,
  }));

  return (
    <div className="fade-in">
      <h2 className="page-title">Sector Surveillance Map</h2>

      <GoogleMapComponent height="calc(100vh - 200px)" incidents={incidents} showAllControls zoom={15} />

      <div className="hazard-table-card">
        <h3>Top Hazard Reports</h3>
        <table className="hazard-table">
          <thead>
            <tr>
              <th>Type</th>
              <th>Location</th>
              <th>Reported At</th>
            </tr>
          </thead>
          <tbody>
            {hazardReports.map((r, i) => (
              <tr key={i}>
                <td className="type-link">{r.type}</td>
                <td>{r.location}</td>
                <td>{r.reportedAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
