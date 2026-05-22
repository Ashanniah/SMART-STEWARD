import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AuthLayout from './layouts/AuthLayout';
import DashboardLayout from './layouts/DashboardLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Reports from './pages/Reports';
import ReportDetail from './pages/ReportDetail';
import ReportStatusUpdate from './pages/ReportStatusUpdate';
import IncidentAnalytics from './pages/IncidentAnalytics';
import SystemSettings from './pages/SystemSettings';
import ReportHistory from './pages/ReportHistory';
import Profile from './pages/Profile';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />

        {/* Auth Routes */}
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Navigate to="/login" replace />} />
        </Route>

        {/* Dashboard Routes */}
        <Route element={<DashboardLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/reports/:reportId/update" element={<ReportStatusUpdate />} />
          <Route path="/reports/:reportId" element={<ReportDetail />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/incident-analytics" element={<IncidentAnalytics />} />
          <Route path="/sector-mapping" element={<Navigate to="/dashboard" replace />} />
          <Route path="/system-settings" element={<SystemSettings />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/notifications" element={<Navigate to="/dashboard" replace />} />
          <Route path="/report-history" element={<ReportHistory />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
