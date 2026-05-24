import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import { Bars3Icon } from '@heroicons/react/24/outline';
import { getDashboardConfig } from '../config/dashboardConfig';
import { useAgencyUser } from '../context/AgencyUserContext';
import avatarDefault from '../assets/avatar_icon.png';
import NotificationsDropdown from './NotificationsDropdown';

export default function TopBar({ onToggleSidebar, sidebarExpanded = true }) {
  const { pathname } = useLocation();
  const { viewerAgencyKey, displayName, email } = useAgencyUser();
  const cfg = useMemo(() => getDashboardConfig(viewerAgencyKey), [viewerAgencyKey]);
  const avatarTitle = displayName || email || cfg.userDisplayName;

  const pageMeta = useMemo(() => {
    if (pathname === '/reports') {
      return {
        title: 'REPORTS',
        subtitle: `Live report list assigned to ${cfg.userDisplayName}.`,
      };
    }
    if (pathname.startsWith('/reports/') && pathname.endsWith('/update')) {
      return {
        title: 'REPORT STATUS',
        subtitle: 'Update current status and add remarks for this report.',
      };
    }
    if (pathname.startsWith('/reports/') && !pathname.endsWith('/update')) {
      return {
        title: 'REPORT DETAILS',
        subtitle: '',
      };
    }
    if (pathname === '/incident-analytics') {
      return {
        title: 'INCIDENT ANALYTICS',
        subtitle: '',
      };
    }
    if (pathname === '/profile') {
      return {
        title: 'PROFILE',
        subtitle: 'Manage your account information and preferences.',
      };
    }
    if (pathname === '/report-history') {
      return {
        title: 'HISTORY OF REPORTS',
        subtitle: `Resolved reports assigned to ${cfg.userDisplayName}.`,
      };
    }
    return {
      title: cfg.pageTitle,
      subtitle: cfg.pageSubtitle,
    };
  }, [pathname, cfg.pageTitle, cfg.pageSubtitle, cfg.userDisplayName]);

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
          <h1 className="topbar-title">{pageMeta.title}</h1>
          {pathname !== '/dashboard' &&
          pathname !== '/reports' &&
          !(
            pathname.startsWith('/reports/') && !pathname.endsWith('/update')
          ) &&
          pathname !== '/report-history' &&
          pathname !== '/profile' &&
          pathname !== '/incident-analytics' &&
          pageMeta.subtitle ? (
            <p className="topbar-subtitle">{pageMeta.subtitle}</p>
          ) : null}
        </div>
      </div>
      <div className="topbar-right">
        <NotificationsDropdown />
        <div
          className="topbar-user topbar-user--avatar-only"
          title={avatarTitle}
          aria-label={avatarTitle ? `Signed in as ${avatarTitle}` : 'Account'}
        >
          <div className="topbar-user-avatar">
            <img src={avatarDefault} alt="" />
          </div>
        </div>
      </div>
    </header>
  );
}
