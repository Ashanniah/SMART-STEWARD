import { useState } from 'react';

export default function SystemSettings() {
  const [form, setForm] = useState({
    fullName: 'Barangay Liloan - Unit 7',
    email: 'barangay.liloan@local.gov',
    authorityUser: 'Kagawad Ricardo Cruz',
    department: 'Peace & Order Committee',
  });

  const handleChange = (field) => (e) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // Save logic placeholder
  };

  return (
    <div className="fade-in">
      <h2 className="page-title">System Settings</h2>

      <div className="settings-page">
        <div className="settings-header">
          ⚙️ Settings · Manage Your Account And Application Settings
        </div>

        <div className="settings-section-title">🔒 Profile Information</div>
        <p className="settings-section-desc">Update your personal or agency details.</p>

        <form onSubmit={handleSubmit}>
          <div className="settings-field">
            <label>Full Name / Agency Name</label>
            <input
              type="text"
              value={form.fullName}
              onChange={handleChange('fullName')}
              id="settings-fullname"
            />
          </div>

          <div className="settings-field">
            <label>Email Address</label>
            <input
              type="email"
              value={form.email}
              onChange={handleChange('email')}
              id="settings-email"
            />
          </div>

          <div className="settings-field">
            <label>Authority User</label>
            <input
              type="text"
              value={form.authorityUser}
              onChange={handleChange('authorityUser')}
              id="settings-authority"
            />
          </div>

          <div className="settings-field">
            <label>Department / Unit</label>
            <input
              type="text"
              value={form.department}
              onChange={handleChange('department')}
              id="settings-department"
            />
          </div>

          <div className="settings-actions">
            <button type="submit" className="btn-primary" id="save-profile-btn">Save Profile</button>
            <button type="button" className="btn-secondary" id="cancel-btn">Cancel</button>
          </div>
        </form>
      </div>
    </div>
  );
}
