import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BellIcon, CheckIcon } from '@heroicons/react/24/outline';
import {
  DocumentPlusIcon,
  ExclamationTriangleIcon,
  ClipboardDocumentListIcon,
  ClockIcon,
  ArrowPathIcon,
} from '@heroicons/react/24/solid';
import { useReportsData } from '../context/ReportsDataContext';
import { formatRelativeTime, statusToLabel } from '../utils/normalizeReportDoc';

const READ_IDS_KEY = 'ss-notif-read-ids';

function loadReadIds() {
  try {
    const raw = sessionStorage.getItem(READ_IDS_KEY);
    const arr = JSON.parse(raw || '[]');
    return new Set(Array.isArray(arr) ? arr.map(String) : []);
  } catch {
    return new Set();
  }
}

function saveReadIds(set) {
  try {
    sessionStorage.setItem(READ_IDS_KEY, JSON.stringify([...set]));
  } catch {
    /* ignore */
  }
}

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

function kindForReport(status) {
  if (status === 'rejected') return 'urgent';
  if (status === 'pending') return 'new_report';
  if (status === 'review') return 'new_report_blue';
  return 'status_update';
}

function dotForReport(status) {
  if (status === 'resolved') return 'green';
  if (status === 'rejected') return 'red';
  if (status === 'review') return 'blue';
  if (status === 'pending') return 'yellow';
  return 'blue';
}

export default function NotificationsDropdown() {
  const { reports } = useReportsData();
  const [readIds, setReadIds] = useState(() => loadReadIds());
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  const items = useMemo(() => {
    return reports.slice(0, 25).map((r) => {
      const kind = kindForReport(r.status);
      const dot = dotForReport(r.status);
      const unread = !readIds.has(r.docId);
      return {
        id: r.docId,
        kind,
        dot,
        unread,
        title: `Report: ${r.activity}`,
        body: `${r.location} · ${statusToLabel(r.status)}`,
        timeLabel: formatRelativeTime(r.createdAt),
      };
    });
  }, [reports, readIds]);

  const hasUnread = useMemo(() => items.some((n) => n.unread), [items]);
  const unreadCount = useMemo(() => items.filter((n) => n.unread).length, [items]);

  const markAllRead = useCallback(() => {
    setReadIds((prev) => {
      const next = new Set(prev);
      items.forEach((n) => next.add(n.id));
      saveReadIds(next);
      return next;
    });
  }, [items]);

  const markOneRead = useCallback((id) => {
    setReadIds((prev) => {
      const next = new Set(prev);
      next.add(id);
      saveReadIds(next);
      return next;
    });
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKey = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  return (
    <div className="topbar-notif" ref={wrapRef}>
      <button
        type="button"
        className="topbar-notif__trigger"
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        onClick={() => setOpen((v) => !v)}
      >
        <BellIcon className="topbar-notif__bell" aria-hidden />
        {unreadCount > 0 ? (
          <span className="topbar-notif__badge" aria-hidden>
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        ) : null}
      </button>

      {open ? (
        <div className="topbar-notif__panel" role="dialog" aria-label="Notifications">
          <header className="notifications-dropdown__header">
            <h2 className="notifications-dropdown__title">Notifications</h2>
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

          <ul className="notifications-list notifications-dropdown__list">
            {items.length === 0 ? (
              <li>
                <p className="denr-dashboard__muted" style={{ padding: '1rem 1.25rem', margin: 0 }}>
                  No notifications right now.
                </p>
              </li>
            ) : (
              items.map((n) => (
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
                        <h3 className="notification-card__title">{n.title}</h3>
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
              ))
            )}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
