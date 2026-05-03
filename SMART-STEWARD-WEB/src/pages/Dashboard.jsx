import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ClipboardDocumentListIcon,
  ClockIcon,
  MagnifyingGlassIcon,
  CheckCircleIcon,
  FunnelIcon,
  ArrowPathIcon,
} from '@heroicons/react/24/outline';
import { getDashboardConfig } from '../config/dashboardConfig';
import SummaryStatCard from '../components/SummaryStatCard';
import RecentReportRow from '../components/RecentReportRow';
import GoogleMapComponent from '../components/GoogleMap';

const summaryStats = [
  { key: 'total', Icon: ClipboardDocumentListIcon, label: 'Total Reports', value: '10', accent: 'green' },
  { key: 'pending', Icon: ClockIcon, label: 'Pending Reports', value: '10', accent: 'orange' },
  { key: 'review', Icon: MagnifyingGlassIcon, label: 'Under Review', value: '5', accent: 'blue' },
  { key: 'resolved', Icon: CheckCircleIcon, label: 'Resolved Reports', value: '15', accent: 'teal' },
];

const recentReports = [
  {
    id: 1,
    title: 'illegal logging',
    location: 'Busay, Cebu City',
    dateTime: 'May 20, 2025 · 2:30 PM',
    imageUrl: 'https://images.unsplash.com/photo-1448375240586-882707db8887?w=200&h=120&fit=crop',
  },
  {
    id: 2,
    title: 'Open Burning',
    location: 'Guadalupe, Cebu City',
    dateTime: 'May 21, 2025 · 9:15 AM',
    imageUrl: 'https://images.unsplash.com/photo-1476234251651-3533a066bd4b?w=200&h=120&fit=crop',
  },
  {
    id: 3,
    title: 'illegal Quarry',
    location: 'Talamban, Cebu City',
    dateTime: 'May 22, 2025 · 11:00 AM',
    imageUrl: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=200&h=120&fit=crop',
  },
  {
    id: 4,
    title: 'Wildlife Violations',
    location: 'Toledo City',
    dateTime: 'May 23, 2025 · 8:45 AM',
    imageUrl: 'https://images.unsplash.com/photo-1436280613368-f164db747295?w=200&h=120&fit=crop',
  },
];

export default function Dashboard() {
  const cfg = useMemo(() => getDashboardConfig('default'), []);
  const [filterType, setFilterType] = useState('All Types');
  const [filterStatus, setFilterStatus] = useState('All Status');
  const [filterAgency] = useState(cfg.filterAgencyDefault);
  const [filterDate] = useState('May 23, 2025');

  const reportMapPanelRef = useRef(null);
  const [reportMapFullscreen, setReportMapFullscreen] = useState(false);

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
      <div className="denr-dashboard__stats">
        {summaryStats.map(({ key, Icon, ...rest }) => (
          <SummaryStatCard key={key} Icon={Icon} {...rest} />
        ))}
      </div>

      <div className="denr-dashboard__grid">
        <section className="denr-panel denr-panel--reports">
          <div className="denr-panel__head">
            <h3 className="denr-panel__title">Recent Receive Reports</h3>
            <button type="button" className="denr-link-all">
              View all reports
            </button>
          </div>
          <div className="recent-report-list">
            {recentReports.map((r) => (
              <RecentReportRow key={r.id} {...r} />
            ))}
          </div>
          <button type="button" className="denr-view-all-bottom">
            View all reports
          </button>
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
              zoom={11}
              enableFullscreenControl={false}
            />
          </div>
          <div className="denr-map-legend">
            <span><i className="denr-dot denr-dot--pending" /> Pending</span>
            <span><i className="denr-dot denr-dot--review" /> Under Review</span>
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
              <option>Under Review</option>
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
    </div>
  );
}
