import { useState } from 'react';
import { Link } from 'react-router-dom';

export default function SignUp() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    // Sign up logic placeholder
  };

  return (
    <div className="auth-card fade-in">
      <div className="auth-tabs">
        <Link to="/login" className="auth-tab" id="login-tab-link">
          🔑 Login
        </Link>
        <button className="auth-tab active" id="signup-tab">
          👤 Sign Up
        </button>
      </div>

      <h2>Create account</h2>
      <p className="subtitle">Create your account here</p>

      <form onSubmit={handleSubmit}>
        <div className="auth-field">
          <label>Enter username</label>
          <div className="auth-input-wrap">
            <span className="icon">👤</span>
            <input
              type="text"
              placeholder="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              id="signup-username"
            />
          </div>
        </div>

        <div className="auth-field">
          <label>Enter email address</label>
          <div className="auth-input-wrap">
            <span className="icon">📧</span>
            <input
              type="email"
              placeholder="Email address"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              id="signup-email"
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
              id="signup-password"
            />
          </div>
        </div>
      </form>

      <p className="auth-link">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </div>
  );
}
