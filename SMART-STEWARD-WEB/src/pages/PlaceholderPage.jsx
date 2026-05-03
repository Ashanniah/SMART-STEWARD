export default function PlaceholderPage({ title }) {
  return (
    <div className="fade-in" style={{ color: 'var(--text-primary)' }}>
      <h1 className="page-title">{title}</h1>
      <p style={{ color: 'var(--text-secondary)' }}>This section will be available soon.</p>
    </div>
  );
}
