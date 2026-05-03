import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Squares2X2Icon,
  DocumentTextIcon,
  ChartBarIcon,
  UserIcon,
  ClipboardDocumentListIcon,
  ArrowRightOnRectangleIcon,
} from '@heroicons/react/24/outline';
import Logo from './Logo';
import avatarDefault from '../assets/avatar_icon.png';
import { getDashboardConfig } from '../config/dashboardConfig';
import ConfirmModal from './ConfirmModal';

const navItems = [
  { path: '/dashboard', label: 'Dashboard', Icon: Squares2X2Icon },
  { path: '/reports', label: 'Reports', Icon: DocumentTextIcon },
  { path: '/incident-analytics', label: 'Incident Analytics', Icon: ChartBarIcon },
  { path: '/profile', label: 'User Profile', Icon: UserIcon },
  { path: '/report-history', label: 'History of Reports', Icon: ClipboardDocumentListIcon },
];

export default function Sidebar({ expanded = true }) {
  const navigate = useNavigate();
  const cfg = getDashboardConfig('default');
  const [logoutOpen, setLogoutOpen] = useState(false);

  return (
    <aside className="sidebar" id="app-sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-logo">
          <Logo size={expanded ? 120 : 44} />
        </div>
      </div>

      <nav className="sidebar-nav">
        {navItems.map(({ path, label, Icon, highlight }) => (
          <NavLink
            key={path}
            to={path}
            title={label}
            className={({ isActive }) =>
              [
                'sidebar-item',
                isActive ? 'active' : '',
                highlight ? 'sidebar-item--highlight' : '',
              ]
                .filter(Boolean)
                .join(' ')
            }
          >
            <span className="icon">
              <Icon aria-hidden />
            </span>
            <span className="sidebar-item-label">{label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-footer">
        <button
          type="button"
          className="sidebar-logout"
          title="Logout"
          onClick={() => setLogoutOpen(true)}
        >
          <span className="icon">
            <ArrowRightOnRectangleIcon aria-hidden />
          </span>
          <span className="sidebar-logout-label">Logout</span>
        </button>

        <div className="sidebar-user" title={`${cfg.userDisplayName} · ${cfg.userRole}`}>
          <div className="sidebar-avatar">
            <img src={avatarDefault} alt="" />
          </div>
          <div className="sidebar-user-info">
            <div className="sidebar-user-name">{cfg.userDisplayName}</div>
            <div className="sidebar-user-role">{cfg.userRole}</div>
          </div>
        </div>
      </div>

      <ConfirmModal
        open={logoutOpen}
        title="Log out"
        message="Are you sure you want to logout?"
        cancelLabel="Cancel"
        confirmLabel="Log out"
        onCancel={() => setLogoutOpen(false)}
        onConfirm={() => {
          setLogoutOpen(false);
          navigate('/login');
        }}
      />
    </aside>
  );
}
