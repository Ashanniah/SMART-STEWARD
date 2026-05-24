import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  BellIcon,
  CalendarIcon,
  ClockIcon,
  ExclamationTriangleIcon as ExclamationTriangleOutlineIcon,
  IdentificationIcon,
  MapPinIcon,
} from '@heroicons/react/24/outline';
import {
  DocumentPlusIcon,
  ExclamationTriangleIcon,
  ClipboardDocumentListIcon,
  ClockIcon as ClockIconSolid,
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
import { AGENCY_NOTIFICATION_KINDS } from '../constants/agencyNotificationKinds';
import {
  buildNotificationDetailLines,
  findReportForNotification,
  mergeAndSortAgencyNotifications,
  resolveNotificationDisplayCopy,
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

const DETAIL_LABEL_ICONS = {
  'Report Type': ExclamationTriangleOutlineIcon,
  'Report ID': IdentificationIcon,
  'Date Submitted': CalendarIcon,
  'Time of Report': ClockIcon,
  Location: MapPinIcon,
};

function NotificationGlyph({ visual, compact = false }) {
  const boxClass = compact
    ? 'notification-card__icon-box notification-card__icon-box--compact'
    : 'notification-card__icon-box';
  const glyphClass = compact
    ? 'notification-card__glyph notification-card__glyph--compact'
    : 'notification-card__glyph';

  const wrap = (tone, icon, withClock = false) => (
    <div
      className={`${boxClass} notification-card__icon-box--${tone}${withClock ? ' notification-card__icon-box--with-clock' : ''}`}
    >
      {icon}
      {withClock ? (
        <span className="notification-card__clock-badge" aria-hidden>
          <ClockIconSolid />
        </span>
      ) : null}
    </div>
  );

  const toneGlyph = (tone) => `${glyphClass} notification-card__glyph--tone-${tone}`;
  const light = `${glyphClass} notification-card__glyph--light`;
  const blue = `${glyphClass} notification-card__glyph--blue`;
  const g = (tone, fallback) => (compact ? toneGlyph(tone) : fallback);

  switch (visual) {
    case 'new_report':
      return wrap('muted', <DocumentPlusIcon aria-hidden className={g('muted', blue)} />);
    case 'citizen_urgent':
      return wrap('warn', <ExclamationTriangleIcon aria-hidden className={g('warn', light)} />);
    case 'critical_incident':
      return wrap('danger', <ExclamationTriangleIcon aria-hidden className={g('danger', light)} />);
    case 'status_changed':
      return wrap('muted', <ArrowPathIcon aria-hidden className={g('muted', blue)} />);
    case 'reassigned':
      return wrap('muted', <ArrowsRightLeftIcon aria-hidden className={g('muted', blue)} />);
    case 'ai_classified':
      return wrap('muted', <CpuChipIcon aria-hidden className={g('muted', blue)} />);
    case 'ai_low_confidence':
      return wrap('warn', <SignalSlashIcon aria-hidden className={g('warn', light)} />);
    case 'ai_override':
      return wrap('muted', <CheckBadgeIcon aria-hidden className={g('muted', blue)} />);
    case 'sla_warning':
      return wrap('muted', <ClockIconSolid aria-hidden className={g('muted', blue)} />, true);
    case 'sla_escalation':
      return wrap('warn', <ShieldExclamationIcon aria-hidden className={g('warn', light)} />);
    case 'system_access':
      return wrap('muted', <WifiIcon aria-hidden className={g('muted', blue)} />);
    default:
      return wrap('muted', <ClipboardDocumentListIcon aria-hidden className={g('muted', blue)} />);
  }
}

function DetailFieldLabel({ label }) {
  const Icon = DETAIL_LABEL_ICONS[label];
  return (
    <dt className="notification-card__detail-label">
      {Icon ? <Icon aria-hidden className="notification-card__detail-label-icon" /> : null}
      <span>{label}:</span>
    </dt>
  );
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
      const dot = resolveNotificationDot();
      const unread = !readIds.has(n.id);
      const report = findReportForNotification(n.reportDocId, reports);
      const details = buildNotificationDetailLines(report);
      const copy = resolveNotificationDisplayCopy(n);
      return {
        id: n.id,
        kind: n.kind,
        visual,
        dot,
        unread,
        pinned: Boolean(n.pinned),
        title: copy.title,
        body: copy.body,
        timeLabel: formatRelativeTime(n.createdAt),
        reportDocId: report?.docId || n.reportDocId || '',
        details,
        synthetic: Boolean(n.synthetic),
      };
    });
  }, [mergedList, readIds, reports]);

  const hasUnread = useMemo(() => items.some((n) => n.unread), [items]);
  const allRead = useMemo(
    () => items.length > 0 && items.every((n) => !n.unread),
    [items]
  );
  const unreadCount = useMemo(() => items.filter((n) => n.unread).length, [items]);

  const markAllRead = useCallback(() => {
    setReadIds((prev) => {
      const next = new Set(prev);
      items.forEach((n) => next.add(n.id));
      saveReadIds(next);
      return next;
    });
  }, [items]);

  const markAllUnread = useCallback(() => {
    setReadIds(() => {
      saveReadIds(new Set());
      return new Set();
    });
  }, []);

  const toggleMarkAll = useCallback(() => {
    if (hasUnread) markAllRead();
    else markAllUnread();
  }, [hasUnread, markAllRead, markAllUnread]);

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
              className="notifications-mark-all-pill"
              onClick={toggleMarkAll}
              disabled={items.length === 0}
              aria-pressed={allRead}
            >
              {hasUnread ? 'Mark all as read' : 'Mark all unread'}
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
                    className={`notification-card ${n.unread ? 'notification-card--unread' : ''} ${n.pinned ? 'notification-card--pinned' : ''} ${n.kind === AGENCY_NOTIFICATION_KINDS.NEW_REPORT ? 'notification-card--new-report' : ''} ${n.kind === AGENCY_NOTIFICATION_KINDS.CITIZEN_NOTIFY ? 'notification-card--citizen-notify' : ''}`.trim()}
                  >
                    <div className="notification-card__grow">
                      <div className="notification-card__main">
                        <div className="notification-card__text">
                          {n.pinned ? (
                            <p className="notification-card__badge-urgent" role="status">
                              Urgent
                            </p>
                          ) : null}
                          <div className="notification-card__title-row">
                            <NotificationGlyph visual={n.visual} compact />
                            <h3 className="notification-card__title">{n.title}</h3>
                          </div>
                          {n.details.length > 0 ? (
                            <dl className="notification-card__details">
                              {n.details.map((row) => (
                                <div key={row.label} className="notification-card__detail-row">
                                  <DetailFieldLabel label={row.label} />
                                  <dd>{row.value}</dd>
                                </div>
                              ))}
                            </dl>
                          ) : (
                            <p className="notification-card__body">{n.body}</p>
                          )}
                          <div className="notification-card__footer">
                            {n.reportDocId ? (
                              <button
                                type="button"
                                className="notification-card__view-link"
                                onClick={() => {
                                  markOneRead(n.id);
                                  goView(n.reportDocId);
                                }}
                              >
                                View Full Details
                              </button>
                            ) : null}
                            <time className="notification-card__time">{n.timeLabel}</time>
                          </div>
                        </div>
                      </div>
                    </div>
                    {n.unread ? (
                      <span
                        className={`notification-card__dot notification-card__dot--${n.dot}`}
                        aria-hidden
                      />
                    ) : null}
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
