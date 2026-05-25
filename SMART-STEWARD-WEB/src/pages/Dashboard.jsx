import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  ClipboardDocumentListIcon,
  ClockIcon,
  ArrowPathIcon,
  CheckCircleIcon,
  FunnelIcon,
  InboxArrowDownIcon,
  MapIcon,
} from '@heroicons/react/24/outline';
import { useAgencyUser } from '../context/AgencyUserContext';
import SummaryStatCard from '../components/SummaryStatCard';
import RecentReportRow from '../components/RecentReportRow';
import GoogleMapComponent from '../components/GoogleMap';
import MediaLightbox from '../components/MediaLightbox';
import { useReportsData } from '../context/ReportsDataContext';
import {
  formatReportDateOnly,
  formatReportTimeOnly,
  reportsToMapIncidents,
  statusToLabel,
} from '../utils/normalizeReportDoc';
import { nextMarkerExpiryMs } from '../utils/mapMarkerStatus';
import {
  buildAgencyFilterOptions,
  buildTypeFilterOptions,
  dashboardCountsFromReports,
  DEFAULT_DASHBOARD_FILTERS,
  filterDashboardReports,
  FILTER_ALL_AGENCIES,
  FILTER_ALL_STATUS,
  FILTER_ALL_TYPES,
} from '../utils/dashboardFilters';

const MAP_DEFAULT_CENTER = { lat: 10.2979, lng: 123.8965 };

const SUMMARY_CONFIG = [
  { key: 'total', Icon: ClipboardDocumentListIcon, label: 'Total Reports', accent: 'green' },
  { key: 'pending', Icon: ClockIcon, label: 'Pending Reports', accent: 'slate' },
  { key: 'review', Icon: ArrowPathIcon, label: 'In Progress', accent: 'amber' },
  { key: 'resolved', Icon: CheckCircleIcon, label: 'Resolved Reports', accent: 'teal' },
];

const STATUS_FILTER_OPTIONS = [
  FILTER_ALL_STATUS,
  'Pending',
  'In Progress',
  'Resolved',
  'Rejected',
];

export default function Dashboard() {
  const [searchParams] = useSearchParams();
  const { viewerAgencyKey } = useAgencyUser();
  const { reports, loading, error } = useReportsData();
  const [mediaPreview, setMediaPreview] = useState({ open: false, type: 'image', src: '' });

  const [draftFilters, setDraftFilters] = useState({ ...DEFAULT_DASHBOARD_FILTERS });
  const [appliedFilters, setAppliedFilters] = useState({ ...DEFAULT_DASHBOARD_FILTERS });

  const reportMapPanelRef = useRef(null);
  const agencyFilterInitialized = useRef(false);
  const [reportMapFullscreen, setReportMapFullscreen] = useState(false);

  const typeOptions = useMemo(() => buildTypeFilterOptions(reports), [reports]);
  const agencyOptions = useMemo(
    () => buildAgencyFilterOptions(reports, viewerAgencyKey),
    [reports, viewerAgencyKey]
  );

  const defaultAgencyFilter = useMemo(() => {
    if (agencyOptions.length === 0) return FILTER_ALL_AGENCIES;
    if (agencyOptions.length === 1) return agencyOptions[0];
    if (viewerAgencyKey && agencyOptions.includes(viewerAgencyKey)) return viewerAgencyKey;
    return agencyOptions[0];
  }, [agencyOptions, viewerAgencyKey]);

  useEffect(() => {
    if (!agencyFilterInitialized.current && viewerAgencyKey && defaultAgencyFilter !== FILTER_ALL_AGENCIES) {
      agencyFilterInitialized.current = true;
      setDraftFilters((f) => ({ ...f, agency: defaultAgencyFilter }));
      setAppliedFilters((f) => ({ ...f, agency: defaultAgencyFilter }));
    }
  }, [viewerAgencyKey, defaultAgencyFilter]);

  useEffect(() => {
    const fixAgency = (prev) => {
      if (prev.agency !== FILTER_ALL_AGENCIES && !agencyOptions.includes(prev.agency)) {
        return { ...prev, agency: defaultAgencyFilter };
      }
      return prev;
    };
    setDraftFilters(fixAgency);
    setAppliedFilters(fixAgency);
  }, [agencyOptions, defaultAgencyFilter]);

  const filteredReports = useMemo(
    () => filterDashboardReports(reports, appliedFilters),
    [reports, appliedFilters]
  );

  const counts = useMemo(
    () => dashboardCountsFromReports(filteredReports),
    [filteredReports]
  );

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

  // "Recent Receive Reports" intentionally hides reports in terminal states
  // (Resolved / Rejected) so the panel surfaces only items that still need
  // the agency's attention. Closed cases stay accessible via the Reports list
  // and History of Reports pages.
  const recentSlice = useMemo(
    () =>
      filteredReports
        .filter((r) => r.status !== 'resolved' && r.status !== 'rejected')
        .slice(0, 6),
    [filteredReports]
  );

  const [nowTick, setNowTick] = useState(() => Date.now());

  /**
   * Closed (resolved / rejected) reports linger on the map for one minute.
   * We schedule a single timeout for the next pending expiry — when it
   * fires we bump `nowTick` which both re-runs this effect (chaining to
   * the next expiry if any) and re-evaluates the map filter. The loop
   * self-terminates as soon as no terminal marker is still within TTL.
   */
  useEffect(() => {
    const nextExpiry = nextMarkerExpiryMs(filteredReports, nowTick);
    if (!Number.isFinite(nextExpiry)) return undefined;
    const delay = Math.max(250, nextExpiry - Date.now());
    const id = window.setTimeout(() => setNowTick(Date.now()), delay);
    return () => window.clearTimeout(id);
  }, [filteredReports, nowTick]);

  const mapIncidents = useMemo(
    () => reportsToMapIncidents(filteredReports, nowTick),
    [filteredReports, nowTick]
  );
  const focusedReportId = searchParams.get('focusReport') || '';

  const mapCenter = useMemo(() => {
    const first = mapIncidents[0];
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

  const applyFilters = () => setAppliedFilters({ ...draftFilters });

  const resetFilters = () => {
    const reset = {
      type: FILTER_ALL_TYPES,
      status: FILTER_ALL_STATUS,
      agency: defaultAgencyFilter,
      date: '',
    };
    setDraftFilters(reset);
    setAppliedFilters(reset);
  };

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
            <h3 className="denr-panel__title denr-panel__title--branded">
              <InboxArrowDownIcon aria-hidden />
              Recent Receive Reports
            </h3>
            <Link to="/reports" className="denr-link-all">
              View all reports
            </Link>
          </div>
          <div className="recent-report-list">
            {loading && reports.length === 0 ? (
              <p className="denr-dashboard__muted">Loading reports…</p>
            ) : recentSlice.length === 0 ? (
              <p className="denr-dashboard__muted">
                {reports.length === 0
                  ? 'No reports yet. Submissions from the mobile app will appear here.'
                  : filteredReports.length > 0
                    ? 'No open reports right now. Closed cases are available in History of Reports.'
                    : 'No reports match the current filters.'}
              </p>
            ) : (
              recentSlice.map((r) => (
                <RecentReportRow
                  key={r.docId}
                  docId={r.docId}
                  reportType={r.activity}
                  location={r.location}
                  dateSubmitted={formatReportDateOnly(r.createdAt)}
                  timeOfReport={formatReportTimeOnly(r.createdAt)}
                  assignedAgency={r.assignedAgency}
                  viewerAgencyKey={viewerAgencyKey}
                  imageUrl={r.imageUrl || undefined}
                  hasVideo={Boolean(r.hasVideo)}
                  onMediaClick={() =>
                    setMediaPreview({
                      open: true,
                      type: r.hasVideo && r.videoUrl ? 'video' : 'image',
                      src:
                        r.hasVideo && r.videoUrl
                          ? r.videoUrl
                          : r.imageUrl || r.mediaUrl || '',
                    })
                  }
                  statusLabel={statusToLabel(r.status)}
                  statusKey={r.status}
                />
              ))
            )}
          </div>
        </section>

        <section ref={reportMapPanelRef} className="denr-panel denr-panel--map">
          <h3 className="denr-panel__title denr-panel__title--map denr-panel__title--branded">
            <MapIcon aria-hidden />
            Report Map Location
          </h3>
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
            <span>
              <i className="denr-dot denr-dot--pending" /> Pending
            </span>
            <span>
              <i className="denr-dot denr-dot--review" /> In Progress
            </span>
            <span>
              <i className="denr-dot denr-dot--resolved" /> Resolved
            </span>
            <span>
              <i className="denr-dot denr-dot--rejected" /> Rejected
            </span>
          </div>
        </section>
      </div>

      <section className="denr-filters">
        <div className="denr-filters__head">
          <h3 className="denr-filters__title">
            <FunnelIcon aria-hidden />
            Quick filters
          </h3>
          <p className="denr-filters__hint">Narrow reports by type, status, agency, or date</p>
        </div>
        <div className="denr-filters__row">
          <label className="denr-filter-field">
            <span>By Type</span>
            <select
              value={draftFilters.type}
              onChange={(e) => setDraftFilters((f) => ({ ...f, type: e.target.value }))}
            >
              {typeOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Status</span>
            <select
              value={draftFilters.status}
              onChange={(e) => setDraftFilters((f) => ({ ...f, status: e.target.value }))}
            >
              {STATUS_FILTER_OPTIONS.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Agency</span>
            <select
              value={draftFilters.agency}
              onChange={(e) => setDraftFilters((f) => ({ ...f, agency: e.target.value }))}
              disabled={agencyOptions.length === 0}
            >
              {agencyOptions.length === 0 ? (
                <option value="">No agencies</option>
              ) : (
                agencyOptions.map((opt) => (
                  <option key={opt} value={opt}>
                    {opt}
                  </option>
                ))
              )}
            </select>
          </label>
          <label className="denr-filter-field">
            <span>By Date</span>
            <input
              type="date"
              value={draftFilters.date}
              onChange={(e) => setDraftFilters((f) => ({ ...f, date: e.target.value }))}
            />
          </label>
          <div className="denr-filters__actions">
            <button type="button" className="denr-btn-apply" onClick={applyFilters}>
              <FunnelIcon aria-hidden />
              Apply Filters
            </button>
            <button type="button" className="denr-btn-reset" onClick={resetFilters}>
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
