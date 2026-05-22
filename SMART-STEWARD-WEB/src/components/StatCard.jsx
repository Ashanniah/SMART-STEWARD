export default function StatCard({ label, value, change, direction = 'up' }) {
  const showChange = change != null && String(change).trim() !== '';

  return (
    <div className="stat-card fade-in">
      <div>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
      </div>
      {showChange ? (
        <div className={`stat-change ${direction}`}>
          <span>{direction === 'up' ? '↑' : '↓'}</span>
          {change}
        </div>
      ) : null}
    </div>
  );
}
