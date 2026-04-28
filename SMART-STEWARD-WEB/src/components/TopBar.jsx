export default function TopBar() {
  return (
    <header className="topbar">
      <h1 className="topbar-title">BFP Dashboard</h1>
      <div className="topbar-search">
        <input
          type="text"
          placeholder="Search incidents, locations, or reporters..."
          id="search-input"
        />
      </div>
      <div className="topbar-actions">
        <button className="topbar-icon-btn" title="Help" id="help-btn">❓</button>
        <button className="topbar-icon-btn has-badge" title="Notifications" id="notif-btn">🔔</button>
        <button className="topbar-new-alert" id="new-alert-btn">+ New Alert</button>
      </div>
    </header>
  );
}
