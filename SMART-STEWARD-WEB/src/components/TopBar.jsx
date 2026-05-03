import { useMemo } from 'react';
import { Bars3Icon, ChevronDownIcon } from '@heroicons/react/24/outline';
import { getDashboardConfig } from '../config/dashboardConfig';
import avatarDefault from '../assets/avatar_icon.png';

export default function TopBar({ onToggleSidebar, sidebarExpanded = true }) {
  const cfg = useMemo(() => getDashboardConfig('default'), []);

  return (
    <header className="topbar topbar--unified">
      <div className="topbar-left">
        <button
          type="button"
          className="topbar-menu-btn"
          aria-expanded={sidebarExpanded}
          aria-controls="app-sidebar"
          aria-label={
            sidebarExpanded ? 'Collapse sidebar (show icons only)' : 'Expand sidebar'
          }
          onClick={onToggleSidebar}
        >
          <Bars3Icon aria-hidden />
        </button>
        <div className="topbar-headings">
          <h1 className="topbar-title">{cfg.pageTitle}</h1>
          <p className="topbar-subtitle">{cfg.pageSubtitle}</p>
        </div>
      </div>
      <div className="topbar-user">
        <div className="topbar-user-avatar" aria-hidden>
          <img src={avatarDefault} alt="" />
        </div>
        <div className="topbar-user-text">
          <span className="topbar-user-name">{cfg.userDisplayName}</span>
          <span className="topbar-user-role">{cfg.userRole}</span>
        </div>
        <button type="button" className="topbar-chevron" aria-label="Account menu">
          <ChevronDownIcon aria-hidden />
        </button>
      </div>
    </header>
  );
}
