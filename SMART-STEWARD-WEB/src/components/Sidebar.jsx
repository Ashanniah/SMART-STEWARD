import { NavLink, useLocation } from 'react-router-dom';
import Logo from './Logo';

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: '📊' },
  { path: '/incident-analytics', label: 'Incident Analytics', icon: '📈' },
  { path: '/sector-mapping', label: 'Sector Mapping', icon: '🗺️' },
  { path: '/system-settings', label: 'System Settings', icon: '⚙️' },
];

export default function Sidebar() {
  const location = useLocation();

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <Logo size={100} />
      </div>

      <nav className="sidebar-nav">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `sidebar-item ${isActive || (item.path === '/dashboard' && location.pathname === '/') ? 'active' : ''}`
            }
          >
            <span className="icon">{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-user">
        <div className="sidebar-avatar">AD</div>
        <div className="sidebar-user-info">
          <div className="sidebar-user-name">Admin User</div>
          <div className="sidebar-user-role">Barangay Talamban</div>
        </div>
      </div>
    </aside>
  );
}
