import { useCallback, useState } from 'react';
import { PROFILE_CONFIG } from '../config/profileConfig';
import avatarDefault from '../assets/avatar_icon.png';

const TABS = [
  { id: 'account', label: 'Account Details' },
  { id: 'security', label: 'Security' },
  { id: 'preferences', label: 'System Preferences' },
];

function Toggle({ id, label, checked, onChange }) {
  return (
    <div className="profile-toggle">
      <span className="profile-toggle__label" id={`${id}-label`}>
        {label}
      </span>
      <button
        type="button"
        id={id}
        className={`profile-toggle__switch ${checked ? 'is-on' : ''}`}
        role="switch"
        aria-checked={checked}
        aria-labelledby={`${id}-label`}
        onClick={() => onChange(!checked)}
      >
        <span className="profile-toggle__knob" />
      </button>
    </div>
  );
}

export default function Profile() {
  const [activeTab, setActiveTab] = useState('account');
  const [fullName, setFullName] = useState(PROFILE_CONFIG.fullName);
  const [email, setEmail] = useState(PROFILE_CONFIG.email);
  const [notifEmail, setNotifEmail] = useState(PROFILE_CONFIG.notifications.emailPending);
  const [notifPush, setNotifPush] = useState(PROFILE_CONFIG.notifications.pushUrgent);
  const [notifWeekly, setNotifWeekly] = useState(PROFILE_CONFIG.notifications.weeklySummary);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [emailDigest, setEmailDigest] = useState(true);
  const [compactTable, setCompactTable] = useState(false);

  const resetForm = useCallback(() => {
    setFullName(PROFILE_CONFIG.fullName);
    setEmail(PROFILE_CONFIG.email);
    setNotifEmail(PROFILE_CONFIG.notifications.emailPending);
    setNotifPush(PROFILE_CONFIG.notifications.pushUrgent);
    setNotifWeekly(PROFILE_CONFIG.notifications.weeklySummary);
  }, []);

  const handleSave = useCallback(() => {
    // Placeholder for API
  }, []);

  return (
    <div className="profile-page profile-page--fill fade-in">
      <header className="profile-page__header">
        <h1 className="profile-page__title">PROFILE &amp; SETTINGS</h1>
        <p className="profile-page__subtitle">
          Manage your account and system preferences for {PROFILE_CONFIG.subtitleJurisdiction}
        </p>
      </header>

      <div className="profile-page__grid">
        <aside className="profile-summary" aria-label="Profile summary">
          <div className="profile-summary__avatar">
            <img src={avatarDefault} alt="" width={72} height={72} />
          </div>
          <div className="profile-summary__name">{PROFILE_CONFIG.displayName}</div>
          <div className="profile-summary__role">{PROFILE_CONFIG.roleLabel}</div>
          <hr className="profile-summary__rule" />
          <dl className="profile-summary__meta">
            <div>
              <dt>Jurisdiction</dt>
              <dd>{PROFILE_CONFIG.jurisdiction}</dd>
            </div>
            <div>
              <dt>Account Status</dt>
              <dd className="profile-summary__status">{PROFILE_CONFIG.accountStatus}</dd>
            </div>
            <div>
              <dt>Last Login</dt>
              <dd>{PROFILE_CONFIG.lastLogin}</dd>
            </div>
          </dl>
        </aside>

        <div className="profile-settings">
          <div className="profile-tabs" role="tablist" aria-label="Settings sections">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                id={`tab-${tab.id}`}
                aria-selected={activeTab === tab.id}
                tabIndex={activeTab === tab.id ? 0 : -1}
                className={`profile-tabs__btn ${activeTab === tab.id ? 'is-active' : ''}`}
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <div
            className="profile-panel"
            role="tabpanel"
            id={`panel-${activeTab}`}
            aria-labelledby={`tab-${activeTab}`}
          >
            {activeTab === 'account' && (
              <>
                <section className="profile-section">
                  <h2 className="profile-section__title">Personal Information</h2>
                  <div className="profile-fields">
                    <label className="profile-field" htmlFor="profile-fullname">
                      <span className="profile-field__label">Full Name</span>
                      <input
                        id="profile-fullname"
                        className="profile-input"
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        autoComplete="name"
                      />
                    </label>
                    <label className="profile-field" htmlFor="profile-email">
                      <span className="profile-field__label">Official Email Address</span>
                      <input
                        id="profile-email"
                        type="email"
                        className="profile-input"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        autoComplete="email"
                      />
                    </label>
                  </div>
                </section>

                <section className="profile-section">
                  <h2 className="profile-section__title">Notification Preferences</h2>
                  <div className="profile-toggles">
                    <Toggle
                      id="notif-pending"
                      label='Email alerts for new "Pending" reports'
                      checked={notifEmail}
                      onChange={setNotifEmail}
                    />
                    <Toggle
                      id="notif-urgent"
                      label="Push notifications for urgent environmental incidents"
                      checked={notifPush}
                      onChange={setNotifPush}
                    />
                    <Toggle
                      id="notif-weekly"
                      label="Weekly summary of resolved cases"
                      checked={notifWeekly}
                      onChange={setNotifWeekly}
                    />
                  </div>
                </section>

                <div className="profile-actions">
                  <button type="button" className="profile-btn profile-btn--primary" onClick={handleSave}>
                    Save Changes
                  </button>
                  <button type="button" className="profile-btn profile-btn--ghost" onClick={resetForm}>
                    Discard
                  </button>
                </div>
              </>
            )}

            {activeTab === 'security' && (
              <>
                <section className="profile-section">
                  <h2 className="profile-section__title">Change Password</h2>
                  <p className="profile-section__hint">
                    Use a strong password you do not reuse on other sites.
                  </p>
                  <div className="profile-fields profile-fields--stack">
                    <label className="profile-field" htmlFor="pw-current">
                      <span className="profile-field__label">Current password</span>
                      <input
                        id="pw-current"
                        type="password"
                        className="profile-input"
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        autoComplete="current-password"
                      />
                    </label>
                    <label className="profile-field" htmlFor="pw-new">
                      <span className="profile-field__label">New password</span>
                      <input
                        id="pw-new"
                        type="password"
                        className="profile-input"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        autoComplete="new-password"
                      />
                    </label>
                    <label className="profile-field" htmlFor="pw-confirm">
                      <span className="profile-field__label">Confirm new password</span>
                      <input
                        id="pw-confirm"
                        type="password"
                        className="profile-input"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        autoComplete="new-password"
                      />
                    </label>
                  </div>
                </section>
                <div className="profile-actions">
                  <button type="button" className="profile-btn profile-btn--primary" onClick={handleSave}>
                    Update password
                  </button>
                </div>
              </>
            )}

            {activeTab === 'preferences' && (
              <>
                <section className="profile-section">
                  <h2 className="profile-section__title">System Preferences</h2>
                  <p className="profile-section__hint">
                    These options apply to your session in Smart Steward.
                  </p>
                  <div className="profile-toggles">
                    <Toggle
                      id="pref-digest"
                      label="Email digest for dashboard activity"
                      checked={emailDigest}
                      onChange={setEmailDigest}
                    />
                    <Toggle
                      id="pref-compact"
                      label="Compact tables in reports and history"
                      checked={compactTable}
                      onChange={setCompactTable}
                    />
                  </div>
                </section>
                <div className="profile-actions">
                  <button type="button" className="profile-btn profile-btn--primary" onClick={handleSave}>
                    Save preferences
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
