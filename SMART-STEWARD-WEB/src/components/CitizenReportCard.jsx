export default function CitizenReportCard({ title, reporter, time, description, status }) {
  const statusClass = status === 'IN-PROGRESS' ? 'in-progress' : 'pending';

  return (
    <div className="report-card fade-in">
      <div className="report-card-header">
        <div className="report-card-title">
          <span className="warning-icon">⚠️</span>
          <div>
            <h4>{title}</h4>
            <div className="report-card-meta">
              Reported by {reporter} • {time}
            </div>
          </div>
        </div>
        <span className={`report-status ${statusClass}`}>{status}</span>
      </div>
      <p className="report-description">{description}</p>
      <div className="report-actions">
        <button className="btn-sm">Inspect</button>
        <button className="btn-sm">Details</button>
      </div>
    </div>
  );
}
