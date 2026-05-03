import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { EyeIcon, EyeSlashIcon } from '@heroicons/react/24/solid';

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    navigate('/dashboard');
  };

  return (
    <div className="auth-form-panel fade-in">
      <div className="auth-form-inner">
        <header className="auth-form-header">
          <h1 className="auth-form-title">Welcome Back!</h1>
          <p className="auth-form-subtitle">
            Sign in to access your agency account.
          </p>
          <p className="auth-form-agency-hint">
            One login for Organization, DENR, BFP, PNP, and Barangay users.
          </p>
        </header>

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="auth-form-field">
            <label htmlFor="login-email">Email</label>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              placeholder="Enter your email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="auth-form-field">
            <label htmlFor="login-password">Password</label>
            <div className="auth-form-password-wrap">
              <input
                id="login-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <button
                type="button"
                className="auth-form-password-toggle"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? (
                  <EyeSlashIcon className="auth-form-icon" aria-hidden />
                ) : (
                  <EyeIcon className="auth-form-icon" aria-hidden />
                )}
              </button>
            </div>
          </div>

          <div className="auth-form-row">
            <label className="auth-form-checkbox">
              <input
                type="checkbox"
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
              />
              Remember me
            </label>
            <button type="button" className="auth-form-link-btn">
              Forgot Password?
            </button>
          </div>

          <button type="submit" className="auth-form-submit">
            LOGIN
          </button>
        </form>

        <p className="auth-form-footer-link">
          Need an account? <Link to="/signup">Sign up</Link>
        </p>
      </div>

      <p className="auth-form-copyright">
        © 2026 Smart steward, All right reserved
      </p>
    </div>
  );
}
