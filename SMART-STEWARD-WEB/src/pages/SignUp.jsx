import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { EyeIcon, EyeSlashIcon } from '@heroicons/react/24/solid';

export default function SignUp() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    navigate('/dashboard');
  };

  return (
    <div className="auth-form-panel fade-in">
      <div className="auth-form-inner">
        <header className="auth-form-header">
          <h1 className="auth-form-title">Create account</h1>
          <p className="auth-form-subtitle">
            Register for Organization, DENR, BFP, PNP, or Barangay access.
          </p>
        </header>

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="auth-form-field">
            <label htmlFor="signup-username">Username</label>
            <input
              id="signup-username"
              type="text"
              autoComplete="username"
              placeholder="Enter your username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>

          <div className="auth-form-field">
            <label htmlFor="signup-email">Email</label>
            <input
              id="signup-email"
              type="email"
              autoComplete="email"
              placeholder="Enter your email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="auth-form-field">
            <label htmlFor="signup-password">Password</label>
            <div className="auth-form-password-wrap">
              <input
                id="signup-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
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

          <button type="submit" className="auth-form-submit">
            Sign up
          </button>
        </form>

        <p className="auth-form-footer-link">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>

      <p className="auth-form-copyright">
        © 2026 Smart steward, All right reserved
      </p>
    </div>
  );
}
