import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { EyeIcon, EyeSlashIcon } from '@heroicons/react/24/solid';
import { LOGIN_MSG, performAgencyLogin } from '../services/agencyLogin';
import { validateLoginForm } from '../utils/loginValidation';

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [outlineBothFields, setOutlineBothFields] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');

  const clearErrors = () => {
    setEmailError('');
    setPasswordError('');
    setOutlineBothFields(false);
  };

  const applyAuthFailureMessage = (message) => {
    if (message === LOGIN_MSG.userNotFound) {
      setEmailError(message);
      return;
    }
    if (message === LOGIN_MSG.wrongPassword) {
      setPasswordError(message);
      return;
    }
    setOutlineBothFields(true);
    setPasswordError(message);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    clearErrors();
    setSuccessMsg('');

    const v = validateLoginForm(email, password);
    if (!v.ok) {
      if (v.kind === 'form') {
        setOutlineBothFields(true);
        setPasswordError(v.message);
      } else if (v.kind === 'email') setEmailError(v.message);
      else setPasswordError(v.message);
      return;
    }

    setLoading(true);
    try {
      const result = await performAgencyLogin({
        email: v.email,
        password,
        rememberMe: remember,
      });
      if (!result.ok) {
        applyAuthFailureMessage(result.message);
        return;
      }
      setSuccessMsg(LOGIN_MSG.success);
      window.setTimeout(() => navigate('/dashboard'), 900);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-form-panel fade-in">
      <div className="auth-form-body">
        <div className="auth-form-inner">
          <header className="auth-form-header">
            <h1 className="auth-form-title">Welcome Back!</h1>
            <p className="auth-form-subtitle">
              Sign in to access your agency account.
            </p>
          </header>

          <form className="auth-form" onSubmit={handleSubmit} noValidate>
            <div
              className={`auth-form-field${
                emailError || outlineBothFields ? ' auth-form-field--error' : ''
              }`}
            >
              <label htmlFor="login-email">Email</label>
              <input
                id="login-email"
                type="email"
                autoComplete="email"
                placeholder="Enter your email"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (emailError) setEmailError('');
                  if (outlineBothFields) {
                    setOutlineBothFields(false);
                    setPasswordError('');
                  }
                }}
                aria-invalid={!!(emailError || outlineBothFields)}
                aria-describedby={emailError ? 'login-email-error' : undefined}
              />
              {emailError ? (
                <span id="login-email-error" className="auth-form-field-error" role="alert">
                  {emailError}
                </span>
              ) : null}
            </div>

            <div
              className={`auth-form-field${
                passwordError || outlineBothFields ? ' auth-form-field--error' : ''
              }`}
            >
              <label htmlFor="login-password">Password</label>
              <div className="auth-form-password-wrap">
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (passwordError) setPasswordError('');
                    if (outlineBothFields) {
                      setOutlineBothFields(false);
                      setEmailError('');
                    }
                  }}
                  aria-invalid={!!(passwordError || outlineBothFields)}
                  aria-describedby={
                    passwordError ? 'login-password-error' : undefined
                  }
                />
                <button
                  type="button"
                  className="auth-form-password-toggle"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? (
                    <EyeSlashIcon className="auth-form-icon" aria-hidden />
                  ) : (
                    <EyeIcon className="auth-form-icon" aria-hidden />
                  )}
                </button>
              </div>
              {passwordError ? (
                <span id="login-password-error" className="auth-form-field-error" role="alert">
                  {passwordError}
                </span>
              ) : null}
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

            <button
              type="submit"
              className={`auth-form-submit${loading ? ' auth-form-submit--loading' : ''}`}
              disabled={loading || !!successMsg}
              aria-busy={loading}
            >
              {successMsg ? (
                'Redirecting…'
              ) : loading ? (
                <span className="auth-form-submit-inner">
                  <span className="auth-form-submit-spinner" aria-hidden />
                  Logging in…
                </span>
              ) : (
                'LOGIN'
              )}
            </button>

            {successMsg ? (
              <p className="auth-form-success-foot" role="status">
                {successMsg}
              </p>
            ) : null}
          </form>

          <p className="auth-form-footer-link">
            Need an account? <Link to="/signup">Sign up</Link>
          </p>
        </div>
      </div>

      <p className="auth-form-copyright">
        © 2026 Smart steward, All right reserved
      </p>
    </div>
  );
}
