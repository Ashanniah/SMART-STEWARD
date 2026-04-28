import { Outlet } from 'react-router-dom';
import Logo from '../components/Logo';

export default function AuthLayout() {
  return (
    <div className="auth-layout">
      <div className="auth-logo">
        <Logo size={140} />
      </div>
      <Outlet />
    </div>
  );
}
