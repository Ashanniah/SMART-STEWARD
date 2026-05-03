import { Outlet } from 'react-router-dom';
import logoSteward from '../assets/logo_steward.png';

export default function AuthLayout() {
  return (
    <div className="auth-split">
      <aside className="auth-split-sidebar" aria-label="Branding">
        <div className="auth-split-brand">
          <img
            src={logoSteward}
            alt="Smart Steward"
            className="auth-split-logo"
            width={240}
            height={240}
          />
        </div>
        <div className="auth-split-tagline">
          <p>A Smart Way to Report</p>
          <p>A Better Environment</p>
          <p>for All</p>
        </div>
      </aside>
      <main className="auth-split-main">
        <Outlet />
      </main>
    </div>
  );
}
