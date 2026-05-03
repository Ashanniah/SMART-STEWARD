import { useCallback, useMemo, useState } from 'react';
import { CheckIcon } from '@heroicons/react/24/outline';
import {
  DocumentPlusIcon,
  ExclamationTriangleIcon,
  ClipboardDocumentListIcon,
  ClockIcon,
  ArrowPathIcon,
} from '@heroicons/react/24/solid';
import { NOTIFICATIONS_SEED } from '../data/notificationsMock';

function NotificationIcon({ kind }) {
  switch (kind) {
    case 'new_report':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--danger">
          <DocumentPlusIcon aria-hidden className="notification-card__glyph notification-card__glyph--light" />
        </div>
      );
    case 'urgent':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--warn">
          <ExclamationTriangleIcon aria-hidden className="notification-card__glyph notification-card__glyph--light" />
        </div>
      );
    case 'new_report_blue':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--muted notification-card__icon-box--with-clock">
          <ClipboardDocumentListIcon aria-hidden className="notification-card__glyph notification-card__glyph--blue" />
          <span className="notification-card__clock-badge" aria-hidden>
            <ClockIcon />
          </span>
        </div>
      );
    case 'status_update':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--spin-wrap">
          <ArrowPathIcon aria-hidden className="notification-card__glyph notification-card__glyph--spin" />
        </div>
      );
    default:
      return (
        <div className="notification-card__icon-box notification-card__icon-box--muted">
          <ClipboardDocumentListIcon aria-hidden className="notification-card__glyph notification-card__glyph--blue" />
        </div>
      );
  }
}

export default function Notifications() {
  const [items, setItems] = useState(() =>
    NOTIFICATIONS_SEED.map((n) => ({ ...n }))
  );

  const hasUnread = useMemo(() => items.some((n) => n.unread), [items]);

  const markAllRead = useCallback(() => {
    setItems((prev) => prev.map((n) => ({ ...n, unread: false })));
  }, []);

  const markOneRead = useCallback((id) => {
    setItems((prev) => prev.map((n) => (n.id === id ? { ...n, unread: false } : n)));
  }, []);

  return (
    <div className="notifications-page fade-in">
      <header className="notifications-page__header">
        <h1 className="notifications-page__title">Notification</h1>
        <button
          type="button"
          className="notifications-mark-all"
          onClick={markAllRead}
          disabled={!hasUnread}
        >
          <CheckIcon aria-hidden className="notifications-mark-all__icon" />
          Mark all as read
        </button>
      </header>

      <ul className="notifications-list">
        {items.map((n) => (
          <li key={n.id}>
            <article
              className={`notification-card ${n.unread ? 'notification-card--unread' : ''}`}
            >
              <button
                type="button"
                className="notification-card__main"
                onClick={() => markOneRead(n.id)}
                aria-label={`${n.title}. ${n.body}`}
              >
                <NotificationIcon kind={n.kind} />
                <div className="notification-card__text">
                  <h2 className="notification-card__title">{n.title}</h2>
                  <p className="notification-card__body">{n.body}</p>
                  <time className="notification-card__time">{n.timeLabel}</time>
                </div>
              </button>
              <span
                className={`notification-card__dot notification-card__dot--${n.dot}`}
                aria-hidden
              />
            </article>
          </li>
        ))}
      </ul>
    </div>
  );
}
