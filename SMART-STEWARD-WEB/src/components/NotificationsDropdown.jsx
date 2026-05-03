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
import { formatRelativeTime } from '../utils/normalizeReportDoc';

const READ_IDS_KEY = 'smartsteward-notif-read-doc-ids';

function loadReadSet() {
  try {
    const raw = sessionStorage.getItem(READ_IDS_KEY);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return new Set(Array.isArray(arr) ? arr : []);
  } catch {
    return new Set();
  }
}

function saveReadSet(set) {
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
  const [open, setOpen] = useState(false);
  const [readIds, setReadIds] = useState(() => loadReadSet());
  const wrapRef = useRef(null);

  useEffect(() => {
    saveReadSet(readIds);
  }, [readIds]);

  const items = useMemo(() => {
    const slice = reports.slice(0, 25);
    return slice.map((r) => {
      const kind =
        r.status === 'resolved'
          ? 'status_update'
          : r.status === 'review'
            ? 'new_report_blue'
            : 'new_report';
      const dot =
        r.status === 'resolved' ? 'green' : r.status === 'review' ? 'yellow' : 'red';
      return {
        id: r.docId,
        kind,
        title: 'New report submitted',
        body: `${r.activity} — ${r.location}`,
        timeLabel: formatRelativeTime(r.createdAt),
        dot,
        unread: !readIds.has(r.docId),
      };
    });
  }, [reports, readIds]);

  const hasUnread = useMemo(() => items.some((n) => n.unread), [items]);
  const unreadCount = useMemo(() => items.filter((n) => n.unread).length, [items]);

  const markAllRead = useCallback(() => {
    const ids = reports.slice(0, 25).map((r) => r.docId);
    setReadIds((prev) => new Set([...prev, ...ids]));
  }, [reports]);

  const markOneRead = useCallback((id) => {
    setReadIds((prev) => new Set([...prev, id]));
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
              <li className="notifications-dropdown__empty">No reports yet.</li>
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
