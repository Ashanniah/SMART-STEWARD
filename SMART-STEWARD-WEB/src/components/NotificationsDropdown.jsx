import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BellIcon, CheckIcon } from '@heroicons/react/24/outline';
import {
  DocumentPlusIcon,
  ExclamationTriangleIcon,
  ClipboardDocumentListIcon,
  ClockIcon,
  ArrowPathIcon,
  ArrowsRightLeftIcon,
  CpuChipIcon,
  SignalSlashIcon,
  CheckBadgeIcon,
  ShieldExclamationIcon,
  WifiIcon,
} from '@heroicons/react/24/solid';
import { useNavigate } from 'react-router-dom';
import { useAgencyNotifications } from '../context/AgencyNotificationsContext';
import { useReportsData } from '../context/ReportsDataContext';
import { formatRelativeTime } from '../utils/normalizeReportDoc';
import { buildSyntheticAgencyNotifications } from '../utils/syntheticAgencyNotifications';
import {
  mergeAndSortAgencyNotifications,
  resolveNotificationDot,
  resolveNotificationVisualKind,
} from '../utils/agencyNotificationPresentation';

const READ_IDS_KEY = 'ss-agency-notif-read-ids';

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

function resolveNotificationReportDocId(rawId, reports) {
  const id = String(rawId ?? '').trim();
  if (!id) return '';
  const exact = reports.find((r) => String(r.docId) === id);
  if (exact) return exact.docId;
  const byDisplayId = reports.find((r) => String(r.id) === id);
  if (byDisplayId) return byDisplayId.docId;
  const byDeptId = reports.find((r) => String(r.deptReportId ?? '') === id);
  if (byDeptId) return byDeptId.docId;
  return id;
}

function NotificationGlyph({ visual }) {
  switch (visual) {
    case 'new_report':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--danger">
          <DocumentPlusIcon aria-hidden className="notification-card__glyph notification-card__glyph--light" />
        </div>
      );
    case 'citizen_urgent':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--warn">
          <ExclamationTriangleIcon aria-hidden className="notification-card__glyph notification-card__glyph--light" />
        </div>
      );
    case 'critical_incident':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--danger">
          <ExclamationTriangleIcon aria-hidden className="notification-card__glyph notification-card__glyph--light" />
        </div>
      );
    case 'status_changed':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--muted">
          <ArrowPathIcon aria-hidden className="notification-card__glyph notification-card__glyph--blue" />
        </div>
      );
    case 'reassigned':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--muted">
          <ArrowsRightLeftIcon aria-hidden className="notification-card__glyph notification-card__glyph--blue" />
        </div>
      );
    case 'ai_classified':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--muted">
          <CpuChipIcon aria-hidden className="notification-card__glyph notification-card__glyph--blue" />
        </div>
      );
    case 'ai_low_confidence':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--warn">
          <SignalSlashIcon aria-hidden className="notification-card__glyph notification-card__glyph--light" />
        </div>
      );
    case 'ai_override':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--muted">
          <CheckBadgeIcon aria-hidden className="notification-card__glyph notification-card__glyph--blue" />
        </div>
      );
    case 'sla_warning':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--muted notification-card__icon-box--with-clock">
          <ClockIcon aria-hidden className="notification-card__glyph notification-card__glyph--blue" />
          <span className="notification-card__clock-badge" aria-hidden>
            <ClockIcon />
          </span>
        </div>
      );
    case 'sla_escalation':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--warn">
          <ShieldExclamationIcon aria-hidden className="notification-card__glyph notification-card__glyph--light" />
        </div>
      );
    case 'system_access':
      return (
        <div className="notification-card__icon-box notification-card__icon-box--muted">
          <WifiIcon aria-hidden className="notification-card__glyph notification-card__glyph--blue" />
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

export default function NotificationsDropdown() {
  const navigate = useNavigate();
  const { notifications } = useAgencyNotifications();
  const { reports } = useReportsData();
  const [readIds, setReadIds] = useState(() => loadReadIds());
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  const mergedList = useMemo(() => {
    const synthetic = buildSyntheticAgencyNotifications(reports);
    return mergeAndSortAgencyNotifications(notifications, synthetic);
  }, [notifications, reports]);

  const items = useMemo(() => {
    return mergedList.map((n) => {
      const visual = resolveNotificationVisualKind(n.kind);
      const dot = resolveNotificationDot(n.kind, n.severity);
      const unread = !readIds.has(n.id);
      return {
        id: n.id,
        kind: n.kind,
        visual,
        dot,
        unread,
        pinned: Boolean(n.pinned),
        title: n.title,
        body: n.body,
        timeLabel: formatRelativeTime(n.createdAt),
        reportDocId: n.reportDocId || '',
        synthetic: Boolean(n.synthetic),
      };
    });
  }, [mergedList, readIds]);

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

  const goView = useCallback(
    (reportDocId) => {
      const resolvedDocId = resolveNotificationReportDocId(reportDocId, reports);
      if (!resolvedDocId) return;
      navigate(`/reports/${encodeURIComponent(resolvedDocId)}`);
      setOpen(false);
    },
    [navigate, reports]
  );

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
                    className={`notification-card ${n.unread ? 'notification-card--unread' : ''} ${n.pinned ? 'notification-card--pinned' : ''}`.trim()}
                  >
                    <div className="notification-card__grow">
                      <button
                        type="button"
                        className="notification-card__main"
                        onClick={() => {
                          markOneRead(n.id);
                          if (n.reportDocId) goView(n.reportDocId);
                        }}
                        aria-label={`${n.title}. ${n.body}`}
                      >
                        <NotificationGlyph visual={n.visual} />
                        <div className="notification-card__text">
                          {n.pinned ? (
                            <p className="notification-card__badge-urgent" role="status">
                              Urgent
                            </p>
                          ) : null}
                          <h3 className="notification-card__title">{n.title}</h3>
                          <p className="notification-card__body">{n.body}</p>
                          <time className="notification-card__time">{n.timeLabel}</time>
                        </div>
                      </button>
                    </div>
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
