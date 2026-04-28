export default function StatCard({ label, value, change, direction }) {
  return (
    <div className="stat-card fade-in">
      <div>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
      </div>
      <div className={`stat-change ${direction}`}>
        <span>{direction === 'up' ? '↑' : '↓'}</span>
        {change}
      </div>
    </div>
  );
}
