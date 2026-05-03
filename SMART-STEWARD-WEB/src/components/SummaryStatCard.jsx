export default function SummaryStatCard({ Icon, label, value, accent }) {
  const accentClass = accent ? `summary-stat-card--${accent}` : '';

  return (
    <div className={`summary-stat-card ${accentClass}`.trim()}>
      <div className="summary-stat-card__icon" aria-hidden>
        {Icon ? <Icon /> : null}
      </div>
      <div className="summary-stat-card__body">
        <div className="summary-stat-card__label">{label}</div>
        <div className="summary-stat-card__value">{value}</div>
      </div>
    </div>
  );
}
