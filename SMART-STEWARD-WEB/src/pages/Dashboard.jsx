import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  ClipboardDocumentListIcon,
  ClockIcon,
  ArrowPathIcon,
  CheckCircleIcon,
  FunnelIcon,
} from '@heroicons/react/24/outline';
import { getDashboardConfig } from '../config/dashboardConfig';
import { useAgencyUser } from '../context/AgencyUserContext';
import SummaryStatCard from '../components/SummaryStatCard';
import RecentReportRow from '../components/RecentReportRow';
import GoogleMapComponent from '../components/GoogleMap';
import MediaLightbox from '../components/MediaLightbox';
import { useReportsData } from '../context/ReportsDataContext';
import { reportsToMapIncidents, statusToLabel } from '../utils/normalizeReportDoc';

const MAP_DEFAULT_CENTER = { lat: 10.2979, lng: 123.8965 };

const SUMMARY_CONFIG = [
  { key: 'total', Icon: ClipboardDocumentListIcon, label: 'Total Reports', accent: 'green' },
  { key: 'pending', Icon: ClockIcon, label: 'Pending Reports', accent: 'slate' },
  { key: 'review', Icon: ArrowPathIcon, label: 'In Progress', accent: 'amber' },
  { key: 'resolved', Icon: CheckCircleIcon, label: 'Resolved Reports', accent: 'teal' },
];

export default function Dashboard() {
  const [searchParams] = useSearchParams();
  const { viewerAgencyKey } = useAgencyUser();
  const cfg = useMemo(() => getDashboardConfig(viewerAgencyKey), [viewerAgencyKey]);
  const { reports, loading, error, counts } = useReportsData();
  const [mediaPreview, setMediaPreview] = useState({ open: false, type: 'image', src: '' });
  const [filterType, setFilterType] = useState('All Types');
  const [filterStatus, setFilterStatus] = useState('All Status');
  const [filterAgency] = useState(cfg.filterAgencyDefault);
  const [filterDate] = useState('May 23, 2025');

  const reportMapPanelRef = useRef(null);
  const [reportMapFullscreen, setReportMapFullscreen] = useState(false);

  const summaryStats = useMemo(
    () =>
      SUMMARY_CONFIG.map(({ key, Icon, label, accent }) => ({
        key,
        Icon,
        label,
        accent,
        value: String(counts[key] ?? 0),
      })),
    [counts]
  );

  const recentSlice = useMemo(() => reports.slice(0, 6), [reports]);

  const mapIncidents = useMemo(() => reportsToMapIncidents(reports), [reports]);
  const focusedReportId = searchParams.get('focusReport') || '';

  const mapCenter = useMemo(() => {
    const first = mapIncidents.find(
      (m) =>
        m.lat != null &&
        m.lng != null &&
        Number.isFinite(m.lat) &&
        Number.isFinite(m.lng)
    );
    if (first) return { lat: first.lat, lng: first.lng };
    return MAP_DEFAULT_CENTER;
  }, [mapIncidents]);

  useEffect(() => {
    const sync = () => {
      const el = reportMapPanelRef.current;
      setReportMapFullscreen(!!el && document.fullscreenElement === el);
    };
    document.addEventListener('fullscreenchange', sync);
    return () => document.removeEventListener('fullscreenchange', sync);
  }, []);

  const toggleReportMapFullscreen = useCallback(() => {
    const el = reportMapPanelRef.current;
    if (!el) return;
    if (document.fullscreenElement === el) {
      void document.exitFullscreen();
    } else {
      void el.requestFullscreen?.();
    }
  }, []);

  return (
    <div className="denr-dashboard fade-in">
      {error ? (
        <p className="denr-dashboard__firestore-msg" role="alert">
          {error}
        </p>
      ) : null}

      <div className="denr-dashboard__stats">
        {summaryStats.map(({ key, Icon, ...rest }) => (
          <SummaryStatCard key={key} Icon={Icon} {...rest} />
        ))}
      </div>

      <div className="denr-dashboard__grid">
        <section className="denr-panel denr-panel--reports">
          <div className="denr-panel__head">
            <h3 className="denr-panel__title">Recent Receive Reports</h3>
            <Link to="/reports" className="denr-link-all">
              View all reports
            </Link>
          </div>
          <div className="recent-report-list">
            {loading && reports.length === 0 ? (
              <p className="denr-dashboard__muted">Loading reports…</p>
            ) : recentSlice.length === 0 ? (
              <p className="denr-dashboard__muted">
                No reports to display. New reports will appear here when they are received.
              </p>
            ) : (
              recentSlice.map((r) => (
                <RecentReportRow
                  key={r.docId}
                  title={r.activity}
                  location={r.location}
                  dateTime={r.date}
                  imageUrl={r.imageUrl || undefined}
                  hasVideo={Boolean(r.hasVideo)}
                  onMediaClick={() =>
                    setMediaPreview({
                      open: true,
                      type: r.hasVideo && r.videoUrl ? 'video' : 'image',
                      src: (r.hasVideo && r.videoUrl) ? r.videoUrl : (r.imageUrl || r.mediaUrl || ''),
                    })
                  }
                  statusLabel={statusToLabel(r.status)}
                  statusKey={r.status}
                />
              ))
            )}
          </div>
          <Link to="/reports" className="denr-view-all-bottom">
            View all reports
          </Link>
        </section>

        <section ref={reportMapPanelRef} className="denr-panel denr-panel--map">
          <h3 className="denr-panel__title denr-panel__title--map">Report Location</h3>
          <div className="denr-map-wrap">
            <button
              type="button"
              className="denr-map-fs-btn"
              onClick={toggleReportMapFullscreen}
              title={reportMapFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
              aria-label={reportMapFullscreen ? 'Exit fullscreen' : 'Fullscreen map'}
            >
              <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
                <path
                  fill="currentColor"
                  d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"
                />
              </svg>
            </button>
            <GoogleMapComponent
              height={reportMapFullscreen ? '100%' : '360px'}
              zoom={mapIncidents.length ? 11 : 10}
              center={mapCenter}
              incidents={mapIncidents}
              enableFullscreenControl={false}
              focusIncidentId={focusedReportId}
            />
          </div>
          <div className="denr-map-legend">
            <span><i className="denr-dot denr-dot--pending" /> Pending</span>
            <span><i className="denr-dot denr-dot--review" /> In Progress</span>
            <span><i className="denr-dot denr-dot--resolved" /> Resolved</span>
            <span><i className="denr-dot denr-dot--rejected" /> Rejected</span>
          </div>
        </section>
      </div>

      <section className="denr-filters">
        <h3 className="denr-filters__title">Quick filters</h3>
        <div className="denr-filters__row">
          <label className="denr-filter-field">
            <span>By Type</span>
            <select value={filterType} onChange={(e) => setFilterType(e.target.value)}>
              <option>All Types</option>
              <option>Fire</option>
              <option>Environmental</option>
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Status</span>
            <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}>
              <option>All Status</option>
              <option>Pending</option>
              <option>In Progress</option>
              <option>Resolved</option>
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Agency</span>
            <select defaultValue={filterAgency}>
              <option value="DENR">DENR</option>
              <option value="BFP">BFP</option>
              <option value="PNP">PNP</option>
              <option value="Barangay">Barangay</option>
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Date</span>
            <input type="text" readOnly value={filterDate} />
          </label>
          <div className="denr-filters__actions">
            <button type="button" className="denr-btn-apply">
              <FunnelIcon aria-hidden />
              Apply Filters
            </button>
            <button type="button" className="denr-btn-reset">
              <ArrowPathIcon aria-hidden />
              Reset
            </button>
          </div>
        </div>
      </section>
      <MediaLightbox
        open={mediaPreview.open}
        type={mediaPreview.type}
        src={mediaPreview.src}
        onClose={() => setMediaPreview({ open: false, type: 'image', src: '' })}
      />
    </div>
  );
}
