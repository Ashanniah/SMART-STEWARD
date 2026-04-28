import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AuthLayout from './layouts/AuthLayout';
import DashboardLayout from './layouts/DashboardLayout';
import Login from './pages/Login';
import SignUp from './pages/SignUp';
import Dashboard from './pages/Dashboard';
import IncidentAnalytics from './pages/IncidentAnalytics';
import SectorMapping from './pages/SectorMapping';
import SystemSettings from './pages/SystemSettings';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Auth Routes */}
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<SignUp />} />
        </Route>

        {/* Dashboard Routes */}
        <Route element={<DashboardLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/incident-analytics" element={<IncidentAnalytics />} />
          <Route path="/sector-mapping" element={<SectorMapping />} />
          <Route path="/system-settings" element={<SystemSettings />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
