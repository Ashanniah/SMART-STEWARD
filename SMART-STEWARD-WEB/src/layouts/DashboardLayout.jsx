import { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import TopBar from '../components/TopBar';
import RequireAuth from '../components/RequireAuth';
import { AgencyNotificationsProvider } from '../context/AgencyNotificationsContext';
import { AgencyUserProvider } from '../context/AgencyUserContext';
import { ReportsDataProvider } from '../context/ReportsDataContext';

export default function DashboardLayout() {
  const [sidebarExpanded, setSidebarExpanded] = useState(true);
  const { pathname } = useLocation();
  const profileFill = pathname === '/profile';

  return (
    <RequireAuth>
    <AgencyUserProvider>
    <AgencyNotificationsProvider>
    <ReportsDataProvider>
    <div
      className={`dashboard-layout ${sidebarExpanded ? '' : 'dashboard-layout--sidebar-mini'}`.trim()}
    >
      <Sidebar expanded={sidebarExpanded} />
      <div className="main-content">
        <TopBar
          onToggleSidebar={() => setSidebarExpanded((open) => !open)}
          sidebarExpanded={sidebarExpanded}
        />
        <div
          className={['page-content', profileFill ? 'page-content--profile-fill' : '']
            .filter(Boolean)
            .join(' ')}
        >
          <Outlet />
        </div>
      </div>
    </div>
    </ReportsDataProvider>
    </AgencyNotificationsProvider>
    </AgencyUserProvider>
    </RequireAuth>
  );
}
