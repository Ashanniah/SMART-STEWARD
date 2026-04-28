import { useState } from 'react';
import { Link } from 'react-router-dom';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    // Login logic placeholder
  };

  return (
    <div className="auth-card fade-in">
      <div className="auth-tabs">
        <button className="auth-tab active" id="login-tab">
          🔑 Login
        </button>
        <Link to="/signup" className="auth-tab" id="signup-tab-link">
          👤 Sign Up
        </Link>
      </div>

      <h2>Welcome back</h2>
      <p className="subtitle">Sign in to your account</p>

      <form onSubmit={handleSubmit}>
        <div className="auth-field">
          <label>Enter email address</label>
          <div className="auth-input-wrap">
            <span className="icon">📧</span>
            <input
              type="email"
              placeholder="Email address"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              id="login-email"
            />
          </div>
        </div>

        <div className="auth-field">
          <label>Enter password</label>
          <div className="auth-input-wrap">
            <span className="icon">🔒</span>
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              id="login-password"
            />
          </div>
        </div>
      </form>

      <p className="auth-link">
        Don't have an Account? <Link to="/signup">Sign up</Link>
      </p>
    </div>
  );
}
