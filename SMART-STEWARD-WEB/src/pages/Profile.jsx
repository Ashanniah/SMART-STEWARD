import { useCallback, useEffect, useMemo, useState } from 'react';
import { doc, getDoc, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';
import { PROFILE_CONFIG } from '../config/profileConfig';
import { USERS_COLLECTION } from '../constants/agencyAuth';
import { getFirebaseAuth, getFirestoreDb } from '../firebase/config';
import { useAgencyUser } from '../context/AgencyUserContext';
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
  const { displayName, roleLabel, email: accountEmail } = useAgencyUser();
  const [activeTab, setActiveTab] = useState('account');
  const [firstName, setFirstName] = useState('');
  const [middleName, setMiddleName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState(PROFILE_CONFIG.email);
  const [notifEmail, setNotifEmail] = useState(PROFILE_CONFIG.notifications.emailPending);
  const [notifPush, setNotifPush] = useState(PROFILE_CONFIG.notifications.pushUrgent);
  const [notifWeekly, setNotifWeekly] = useState(PROFILE_CONFIG.notifications.weeklySummary);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [messageKind, setMessageKind] = useState('info');

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [emailDigest, setEmailDigest] = useState(true);
  const [compactTable, setCompactTable] = useState(false);
  const [initialAccount, setInitialAccount] = useState({
    firstName: '',
    middleName: '',
    lastName: '',
    email: PROFILE_CONFIG.email,
    notifEmail: PROFILE_CONFIG.notifications.emailPending,
    notifPush: PROFILE_CONFIG.notifications.pushUrgent,
    notifWeekly: PROFILE_CONFIG.notifications.weeklySummary,
  });

  const profileDisplayName = useMemo(() => {
    const joined = [firstName, middleName, lastName].filter(Boolean).join(' ').trim();
    return joined || displayName || PROFILE_CONFIG.displayName;
  }, [firstName, middleName, lastName, displayName]);

  useEffect(() => {
    const auth = getFirebaseAuth();
    const db = getFirestoreDb();
    const uid = auth?.currentUser?.uid;
    if (!db || !uid) {
      const fallbackName = String(displayName || PROFILE_CONFIG.fullName || '').trim();
      const parts = fallbackName.split(/\s+/).filter(Boolean);
      const fallbackFirst = parts[0] ?? '';
      const fallbackLast = parts.length > 1 ? parts[parts.length - 1] : '';
      const fallbackMiddle = parts.length > 2 ? parts.slice(1, -1).join(' ') : '';
      const fallback = {
        firstName: fallbackFirst,
        middleName: fallbackMiddle,
        lastName: fallbackLast,
        email: accountEmail || PROFILE_CONFIG.email,
        notifEmail: PROFILE_CONFIG.notifications.emailPending,
        notifPush: PROFILE_CONFIG.notifications.pushUrgent,
        notifWeekly: PROFILE_CONFIG.notifications.weeklySummary,
      };
      setFirstName(fallback.firstName);
      setMiddleName(fallback.middleName);
      setLastName(fallback.lastName);
      setEmail(fallback.email);
      setNotifEmail(fallback.notifEmail);
      setNotifPush(fallback.notifPush);
      setNotifWeekly(fallback.notifWeekly);
      setInitialAccount(fallback);
      return;
    }

    let alive = true;
    (async () => {
      try {
        const snap = await getDoc(doc(db, USERS_COLLECTION, uid));
        const data = snap.exists() ? snap.data() || {} : {};
        const candidateName = String(
          data.displayName ?? data.name ?? data.fullName ?? displayName ?? PROFILE_CONFIG.fullName
        ).trim();
        const parts = candidateName.split(/\s+/).filter(Boolean);
        const derivedFirst = parts[0] ?? '';
        const derivedLast = parts.length > 1 ? parts[parts.length - 1] : '';
        const derivedMiddle = parts.length > 2 ? parts.slice(1, -1).join(' ') : '';
        const next = {
          firstName: String(data.firstName ?? derivedFirst),
          middleName: String(data.middleName ?? derivedMiddle),
          lastName: String(data.lastName ?? derivedLast),
          email: String(data.email ?? accountEmail ?? PROFILE_CONFIG.email),
          notifEmail: Boolean(
            data.notifications?.emailPending ?? PROFILE_CONFIG.notifications.emailPending
          ),
          notifPush: Boolean(
            data.notifications?.pushUrgent ?? PROFILE_CONFIG.notifications.pushUrgent
          ),
          notifWeekly: Boolean(
            data.notifications?.weeklySummary ?? PROFILE_CONFIG.notifications.weeklySummary
          ),
        };
        if (!alive) return;
        setFirstName(next.firstName);
        setMiddleName(next.middleName);
        setLastName(next.lastName);
        setEmail(next.email);
        setNotifEmail(next.notifEmail);
        setNotifPush(next.notifPush);
        setNotifWeekly(next.notifWeekly);
        setInitialAccount(next);
      } catch {
        /* keep current fallback values */
      }
    })();
    return () => {
      alive = false;
    };
  }, [accountEmail, displayName]);

  const resetForm = useCallback(() => {
    setFirstName(initialAccount.firstName);
    setMiddleName(initialAccount.middleName);
    setLastName(initialAccount.lastName);
    setEmail(initialAccount.email);
    setNotifEmail(initialAccount.notifEmail);
    setNotifPush(initialAccount.notifPush);
    setNotifWeekly(initialAccount.notifWeekly);
    setMessage('');
  }, [initialAccount]);

  const handleSave = useCallback(() => {
    const auth = getFirebaseAuth();
    const db = getFirestoreDb();
    const uid = auth?.currentUser?.uid;
    if (!db || !uid) {
      setMessageKind('error');
      setMessage('Unable to save profile right now.');
      return;
    }
    const first = firstName.trim();
    const middle = middleName.trim();
    const last = lastName.trim();
    const mail = email.trim();
    if (!first || !last || !mail) {
      setMessageKind('error');
      setMessage('First name, last name, and email are required.');
      return;
    }

    const fullName = [first, middle, last].filter(Boolean).join(' ');
    const payload = {
      firstName: first,
      middleName: middle,
      lastName: last,
      fullName,
      displayName: fullName,
      email: mail,
      notifications: {
        emailPending: Boolean(notifEmail),
        pushUrgent: Boolean(notifPush),
        weeklySummary: Boolean(notifWeekly),
      },
      updatedAt: serverTimestamp(),
    };

    setSaving(true);
    setMessage('');
    (async () => {
      try {
        const ref = doc(db, USERS_COLLECTION, uid);
        const existing = await getDoc(ref);
        if (existing.exists()) {
          await updateDoc(ref, payload);
        } else {
          await setDoc(ref, payload, { merge: true });
        }
        const next = {
          firstName: first,
          middleName: middle,
          lastName: last,
          email: mail,
          notifEmail: Boolean(notifEmail),
          notifPush: Boolean(notifPush),
          notifWeekly: Boolean(notifWeekly),
        };
        setInitialAccount(next);
        setMessageKind('success');
        setMessage('Profile updated successfully.');
      } catch {
        setMessageKind('error');
        setMessage('Could not save profile. Please try again.');
      } finally {
        setSaving(false);
      }
    })();
  }, [email, firstName, middleName, lastName, notifEmail, notifPush, notifWeekly]);

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
          <div className="profile-summary__name">{profileDisplayName}</div>
          <div className="profile-summary__role">{roleLabel || PROFILE_CONFIG.roleLabel}</div>
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
                    <label className="profile-field" htmlFor="profile-firstname">
                      <span className="profile-field__label">First Name</span>
                      <input
                        id="profile-firstname"
                        className="profile-input"
                        value={firstName}
                        onChange={(e) => setFirstName(e.target.value)}
                        autoComplete="given-name"
                      />
                    </label>
                    <label className="profile-field" htmlFor="profile-middlename">
                      <span className="profile-field__label">Middle Name</span>
                      <input
                        id="profile-middlename"
                        className="profile-input"
                        value={middleName}
                        onChange={(e) => setMiddleName(e.target.value)}
                        autoComplete="additional-name"
                      />
                    </label>
                    <label className="profile-field" htmlFor="profile-lastname">
                      <span className="profile-field__label">Last Name</span>
                      <input
                        id="profile-lastname"
                        className="profile-input"
                        value={lastName}
                        onChange={(e) => setLastName(e.target.value)}
                        autoComplete="family-name"
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
                  <button
                    type="button"
                    className="profile-btn profile-btn--primary"
                    onClick={handleSave}
                    disabled={saving}
                  >
                    {saving ? 'Saving...' : 'Save Changes'}
                  </button>
                  <button type="button" className="profile-btn profile-btn--ghost" onClick={resetForm}>
                    Discard
                  </button>
                </div>
                {message ? (
                  <p
                    className={`profile-save-msg profile-save-msg--${messageKind}`}
                    role={messageKind === 'error' ? 'alert' : 'status'}
                  >
                    {message}
                  </p>
                ) : null}
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
