import { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import TopBar from '../components/TopBar';
import { ReportsDataProvider } from '../context/ReportsDataContext';

export default function DashboardLayout() {
  const [sidebarExpanded, setSidebarExpanded] = useState(true);
  const { pathname } = useLocation();
  const useDashboardBg =
    pathname === '/dashboard' ||
    pathname === '/reports' ||
    pathname.startsWith('/reports/') ||
    pathname === '/report-history' ||
    pathname === '/profile';

  return (
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
          className={[
            'page-content',
            useDashboardBg ? 'page-content--dashboard' : '',
            pathname === '/profile' ? 'page-content--profile' : '',
          ]
            .filter(Boolean)
            .join(' ')}
        >
          <Outlet />
        </div>
      </div>
    </div>
    </ReportsDataProvider>
  );
}
