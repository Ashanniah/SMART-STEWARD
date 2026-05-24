import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { GoogleMap, useJsApiLoader, Marker, InfoWindow } from '@react-google-maps/api';
import { MarkerClusterer, SuperClusterAlgorithm } from '@googlemaps/markerclusterer';
import { GOOGLE_MAPS_API_KEY } from '../config/googleMaps';
import MapReportFloatingPanel from './MapReportFloatingPanel';
import {
  SmartStewardClusterRenderer,
  aggregateReportCounts,
  inferClusterHeadline,
  mostRecentIncident,
} from '../utils/mapClusterHelpers';
import {
  mapIncidentsStatusSignature,
  resolveMapMarkerStatus,
} from '../utils/mapMarkerStatus';

const mapContainerStyle = {
  width: '100%',
  height: '100%',
};

const defaultCenter = {
  lat: 10.2979,
  lng: 123.8965,
};

const defaultMapOptions = {
  disableDefaultUI: false,
  zoomControl: true,
  streetViewControl: false,
  mapTypeControl: false,
  fullscreenControl: true,
};

const MAP_PIN_PATH =
  'm11.54 22.351.07.04.028.016a.76.76 0 0 0 .723 0l.028-.015.071-.041a16.975 16.975 0 0 0 1.144-.742 19.58 19.58 0 0 0 2.683-2.282c1.944-1.99 3.963-4.98 3.963-8.827a8.25 8.25 0 0 0-16.5 0c0 3.846 2.02 6.837 3.963 8.827a19.58 19.58 0 0 0 2.682 2.282 16.975 16.975 0 0 0 1.145.742ZM12 13.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z';

function pinFillColor(markerStatus) {
  switch (markerStatus) {
    case 'resolved':
      return '#22c55e';
    case 'rejected':
      return '#ef4444';
    case 'review':
    case 'in_progress':
      return '#eab308';
    default:
      return '#6b7280';
  }
}

function buildMapPinIcon(markerStatus, maps) {
  const fillColor = pinFillColor(markerStatus);
  return {
    path: MAP_PIN_PATH,
    fillColor,
    fillOpacity: 1,
    strokeColor: '#ffffff',
    strokeWeight: 0.6,
    scale: 1.45,
    anchor: new maps.Point(12, 22.9),
    labelOrigin: new maps.Point(12, 9),
  };
}

function statusLabelForMarker(markerStatus) {
  switch (markerStatus) {
    case 'resolved':
      return 'RESOLVED';
    case 'rejected':
      return 'REJECTED';
    case 'review':
    case 'in_progress':
      return 'IN PROGRESS';
    default:
      return 'PENDING';
  }
}

function statusColorForMarker(markerStatus) {
  switch (markerStatus) {
    case 'resolved':
      return '#22c55e';
    case 'rejected':
      return '#ef4444';
    case 'review':
    case 'in_progress':
      return '#eab308';
    default:
      return '#6b7280';
  }
}

function markerKey(incident) {
  return String(incident.id);
}

const defaultIncidents = [];

export default function GoogleMapComponent({
  height = '220px',
  incidents = defaultIncidents,
  showAllControls = false,
  zoom = 14,
  center = defaultCenter,
  enableFullscreenControl = true,
  focusIncidentId = '',
  /** When true, markers cluster when zoomed out; single-marker clicks open the rich panel. */
  clustering = true,
}) {
  const { isLoaded, loadError } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: GOOGLE_MAPS_API_KEY,
    libraries: ['places'],
  });

  const [selectedIncident, setSelectedIncident] = useState(null);
  const [floating, setFloating] = useState(null);
  const [geocodedById, setGeocodedById] = useState({});
  const [mapInstance, setMapInstance] = useState(null);
  const mapRef = useRef(null);
  const geocodeRunRef = useRef(0);
  const clustererRef = useRef(null);
  const incidentsRef = useRef([]);

  const mergedIncidents = useMemo(() => {
    return incidents.map((inc) => {
      if (Number.isFinite(inc.lat) && Number.isFinite(inc.lng)) return inc;
      const g = geocodedById[inc.id];
      if (g && Number.isFinite(g.lat) && Number.isFinite(g.lng)) {
        return { ...inc, lat: g.lat, lng: g.lng };
      }
      return inc;
    });
  }, [incidents, geocodedById]);

  const placedIncidents = useMemo(
    () => mergedIncidents.filter((i) => Number.isFinite(i.lat) && Number.isFinite(i.lng)),
    [mergedIncidents]
  );

  /**
   * Spread incidents that share the same coordinates so status-specific pins remain visible
   * after cluster breakup at high zoom.
   */
  const adjustedIncidents = useMemo(() => {
    const groups = new Map();
    placedIncidents.forEach((inc) => {
      const key = `${inc.lat.toFixed(6)}:${inc.lng.toFixed(6)}`;
      const list = groups.get(key) ?? [];
      list.push(inc);
      groups.set(key, list);
    });

    const out = [];
    groups.forEach((list) => {
      if (list.length === 1) {
        out.push(list[0]);
        return;
      }
      const step = 0.000055; // ~6m
      list.forEach((inc, idx) => {
        const angle = (2 * Math.PI * idx) / list.length;
        out.push({
          ...inc,
          lat: inc.lat + Math.sin(angle) * step,
          lng: inc.lng + Math.cos(angle) * step,
        });
      });
    });
    return out;
  }, [placedIncidents]);

  const incidentsStatusSignature = useMemo(
    () => mapIncidentsStatusSignature(placedIncidents),
    [placedIncidents]
  );

  useEffect(() => {
    incidentsRef.current = adjustedIncidents;
  }, [adjustedIncidents]);

  useEffect(() => {
    setFloating((prev) => {
      if (!prev) return prev;

      if (prev.variant === 'single' && prev.incident) {
        const latest =
          adjustedIncidents.find((i) => String(i.id) === String(prev.incident.id)) ??
          prev.incident;
        if (resolveMapMarkerStatus(prev.incident) === resolveMapMarkerStatus(latest)) {
          return prev;
        }
        return { variant: 'single', incident: latest };
      }

      if (prev.variant === 'cluster' && prev.clusterPayload?.incidents?.length) {
        const idSet = new Set(prev.clusterPayload.incidents.map((i) => String(i.id)));
        const refreshed = adjustedIncidents.filter((i) => idSet.has(String(i.id)));
        if (refreshed.length < 2) return prev;
        const prevSig = mapIncidentsStatusSignature(prev.clusterPayload.incidents);
        const nextSig = mapIncidentsStatusSignature(refreshed);
        if (prevSig === nextSig) return prev;
        const counts = aggregateReportCounts(refreshed);
        const { headline, sub } = inferClusterHeadline(refreshed);
        const recent = mostRecentIncident(refreshed);
        return {
          variant: 'cluster',
          clusterPayload: { counts, headline, sub, recent, incidents: refreshed },
        };
      }

      return prev;
    });
  }, [incidentsStatusSignature, adjustedIncidents]);

  useEffect(() => {
    const ids = new Set(incidents.map((i) => i.id));
    setGeocodedById((prev) => {
      let changed = false;
      const next = { ...prev };
      for (const k of Object.keys(next)) {
        if (!ids.has(k)) {
          delete next[k];
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [incidents]);

  useEffect(() => {
    if (!isLoaded || !window.google?.maps?.Geocoder) return;

    const runId = ++geocodeRunRef.current;
    const geocoder = new window.google.maps.Geocoder();
    const timeouts = [];

    const needGeocode = incidents.filter(
      (i) =>
        (!Number.isFinite(i.lat) || !Number.isFinite(i.lng)) &&
        typeof i.geocodeAddress === 'string' &&
        i.geocodeAddress.trim().length > 2
    );

    needGeocode.forEach((inc, index) => {
      const address = inc.geocodeAddress.trim();
      const tid = window.setTimeout(() => {
        if (geocodeRunRef.current !== runId) return;
        geocoder.geocode({ address }, (results, status) => {
          if (geocodeRunRef.current !== runId) return;
          if (status === 'OK' && results?.[0]?.geometry?.location) {
            const loc = results[0].geometry.location;
            setGeocodedById((prev) => {
              if (prev[inc.id]) return prev;
              return { ...prev, [inc.id]: { lat: loc.lat(), lng: loc.lng() } };
            });
          }
        });
      }, index * 220);
      timeouts.push(tid);
    });

    return () => {
      timeouts.forEach((id) => window.clearTimeout(id));
      geocodeRunRef.current += 1;
    };
  }, [isLoaded, incidents]);

  const onMapLoad = useCallback((map) => {
    mapRef.current = map;
    setMapInstance(map);
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !isLoaded || !window.google?.maps) return;
    if (adjustedIncidents.length >= 2) {
      const bounds = new window.google.maps.LatLngBounds();
      adjustedIncidents.forEach((i) => bounds.extend({ lat: i.lat, lng: i.lng }));
      map.fitBounds(bounds, 56);
    } else if (adjustedIncidents.length === 1) {
      map.panTo({ lat: adjustedIncidents[0].lat, lng: adjustedIncidents[0].lng });
      map.setZoom(Math.max(zoom, 13));
    }
  }, [isLoaded, adjustedIncidents, zoom]);

  useEffect(() => {
    if (!focusIncidentId || !isLoaded || !mapRef.current) return;
    const target = adjustedIncidents.find((inc) => String(inc.id) === String(focusIncidentId));
    if (!target) return;
    mapRef.current.panTo({ lat: target.lat, lng: target.lng });
    mapRef.current.setZoom(Math.max(mapRef.current.getZoom() ?? zoom, 17));
    setFloating({ variant: 'single', incident: target });
  }, [focusIncidentId, isLoaded, adjustedIncidents, zoom]);

  useEffect(() => {
    if (!clustering) {
      setFloating(null);
      return;
    }
    if (adjustedIncidents.length === 0) setFloating(null);
  }, [clustering, adjustedIncidents.length]);

  useEffect(() => {
    if (!clustering || !isLoaded || !mapInstance) {
      return () => {};
    }

    const map = mapRef.current;
    const g = window.google?.maps;
    if (!map || !g?.Marker) {
      return () => {};
    }

    if (clustererRef.current) {
      clustererRef.current.clearMarkers();
      clustererRef.current.setMap(null);
      clustererRef.current = null;
    }

    const list = adjustedIncidents;
    if (list.length === 0) {
      return () => {};
    }

    const markers = list.map((inc) => {
      const markerStatus = resolveMapMarkerStatus(inc);
      const m = new g.Marker({
        position: { lat: inc.lat, lng: inc.lng },
        icon: buildMapPinIcon(markerStatus, g),
      });
      m.set('incident', inc);
      m.addListener('click', () => {
        const latest =
          incidentsRef.current.find((i) => String(i.id) === String(inc.id)) ?? inc;
        setFloating({ variant: 'single', incident: latest });
      });
      return m;
    });

    const clusterer = new MarkerClusterer({
      map,
      markers,
      algorithm: new SuperClusterAlgorithm({ radius: 76, maxZoom: 17 }),
      renderer: new SmartStewardClusterRenderer(),
      onClusterClick: (_evt, cluster) => {
        const group = cluster.markers || [];
        if (group.length < 2) return;
        const incidentsInCluster = group
          .map((mk) => {
            const stored = typeof mk.get === 'function' ? mk.get('incident') : null;
            if (!stored) return null;
            return (
              incidentsRef.current.find((i) => String(i.id) === String(stored.id)) ?? stored
            );
          })
          .filter(Boolean);
        if (incidentsInCluster.length < 2) return;
        const counts = aggregateReportCounts(incidentsInCluster);
        const { headline, sub } = inferClusterHeadline(incidentsInCluster);
        const recent = mostRecentIncident(incidentsInCluster);
        setFloating({
          variant: 'cluster',
          clusterPayload: { counts, headline, sub, recent, incidents: incidentsInCluster },
        });
      },
    });

    clustererRef.current = clusterer;

    return () => {
      markers.forEach((m) => {
        g.event.clearInstanceListeners(m);
        m.setMap(null);
      });
      if (clustererRef.current) {
        clustererRef.current.clearMarkers();
        clustererRef.current.setMap(null);
        clustererRef.current = null;
      }
    };
  }, [clustering, isLoaded, mapInstance, adjustedIncidents, incidentsStatusSignature]);

  useEffect(() => {
    if (!selectedIncident) return;
    if (!adjustedIncidents.some((i) => i.id === selectedIncident.id)) {
      setSelectedIncident(null);
    }
  }, [adjustedIncidents, selectedIncident]);

  const onMarkerClick = useCallback((incident) => {
    setSelectedIncident(incident);
  }, []);

  const onInfoWindowClose = useCallback(() => {
    setSelectedIncident(null);
  }, []);

  if (loadError) {
    return (
      <div className="map-placeholder" style={{ height }}>
        <span>Error loading maps</span>
      </div>
    );
  }

  if (!isLoaded) {
    return (
      <div className="map-placeholder" style={{ height }}>
        <span>Loading map...</span>
      </div>
    );
  }

  const mapOptions = showAllControls
    ? {
        ...defaultMapOptions,
        zoomControl: true,
        mapTypeControl: true,
        fullscreenControl: enableFullscreenControl,
      }
    : { ...defaultMapOptions, fullscreenControl: enableFullscreenControl };

  if (clustering) {
    return (
      <div className="google-map-stack" style={{ position: 'relative', width: '100%', height }}>
        <GoogleMap
          mapContainerStyle={mapContainerStyle}
          zoom={zoom}
          center={center}
          options={mapOptions}
          onLoad={onMapLoad}
        />
        <MapReportFloatingPanel
          open={!!floating}
          variant={floating?.variant}
          onClose={() => setFloating(null)}
          incident={floating?.variant === 'single' ? floating.incident : null}
          clusterPayload={floating?.variant === 'cluster' ? floating.clusterPayload : null}
        />
      </div>
    );
  }

  return (
    <GoogleMap
      mapContainerStyle={{ ...mapContainerStyle, height }}
      zoom={zoom}
      center={center}
      options={mapOptions}
      onLoad={onMapLoad}
    >
      {adjustedIncidents.map((incident) => (
        <Marker
          key={markerKey(incident)}
          position={{ lat: incident.lat, lng: incident.lng }}
          icon={buildMapPinIcon(resolveMapMarkerStatus(incident), window.google.maps)}
          onClick={() => onMarkerClick(incident)}
        />
      ))}

      {selectedIncident &&
        Number.isFinite(selectedIncident.lat) &&
        Number.isFinite(selectedIncident.lng) && (
          <InfoWindow
            position={{ lat: selectedIncident.lat, lng: selectedIncident.lng }}
            onCloseClick={onInfoWindowClose}
          >
            <div style={{ color: '#1a1a1a', padding: '4px 8px', minWidth: '120px' }}>
              <strong style={{ fontSize: '0.94rem' }}>{selectedIncident.title}</strong>
              <p style={{ margin: '4px 0 0', fontSize: '0.82rem', color: '#666' }}>
                Type: {selectedIncident.type}
              </p>
              <p
                style={{
                  margin: '2px 0 0',
                  fontSize: '0.77rem',
                  color: statusColorForMarker(resolveMapMarkerStatus(selectedIncident)),
                }}
              >
                Status: {statusLabelForMarker(resolveMapMarkerStatus(selectedIncident))}
              </p>
            </div>
          </InfoWindow>
        )}
    </GoogleMap>
  );
}
