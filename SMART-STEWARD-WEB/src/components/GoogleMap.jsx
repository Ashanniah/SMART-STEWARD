import { useCallback, useState } from 'react';
import { GoogleMap, useJsApiLoader, Marker, InfoWindow } from '@react-google-maps/api';
import { GOOGLE_MAPS_API_KEY } from '../config/googleMaps';

const mapContainerStyle = {
  width: '100%',
  height: '100%',
};

const defaultCenter = {
  lat: 10.2979,
  lng: 123.8965,
};

/** Default Google roadmap (light) — no custom `styles` so the base map stays white/neutral. */
const defaultMapOptions = {
  disableDefaultUI: false,
  zoomControl: true,
  streetViewControl: false,
  mapTypeControl: false,
  fullscreenControl: true,
};

// google.maps.SymbolPath.CIRCLE — use numeric path so we never touch `google` at module load
// (the Maps script is async; referencing google here crashed the whole app → blank green screen).
const SYMBOL_PATH_CIRCLE = 0;

const incidentIcon = {
  path: SYMBOL_PATH_CIRCLE,
  scale: 10,
  fillColor: '#e67e22',
  fillOpacity: 1,
  strokeColor: '#fff',
  strokeWeight: 2,
};

const resolvedIcon = {
  path: SYMBOL_PATH_CIRCLE,
  scale: 8,
  fillColor: '#7bc142',
  fillOpacity: 1,
  strokeColor: '#fff',
  strokeWeight: 2,
};

const defaultIncidents = [
  { id: 1, lat: 10.3029, lng: 123.8995, title: 'Grass Fire', type: 'Fire', status: 'active' },
  { id: 2, lat: 10.2979, lng: 123.8935, title: 'Chemical Spill', type: 'Hazard', status: 'pending' },
  { id: 3, lat: 10.2929, lng: 123.8975, title: 'Faulty Post', type: 'Electrical', status: 'resolved' },
];

export default function GoogleMapComponent({
  height = '220px',
  incidents = defaultIncidents,
  showAllControls = false,
  zoom = 14,
  center = defaultCenter,
  /** Set false on embedded dashboard map — fullscreen is handled on the panel wrapper. */
  enableFullscreenControl = true,
}) {
  const { isLoaded, loadError } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: GOOGLE_MAPS_API_KEY,
    libraries: ['places'],
  });

  const [selectedIncident, setSelectedIncident] = useState(null);

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

  return (
    <GoogleMap
      mapContainerStyle={{ ...mapContainerStyle, height }}
      zoom={zoom}
      center={center}
      options={mapOptions}
    >
      {incidents.map((incident) => (
        <Marker
          key={incident.id}
          position={{ lat: incident.lat, lng: incident.lng }}
          icon={incident.status === 'resolved' ? resolvedIcon : incidentIcon}
          onClick={() => onMarkerClick(incident)}
        />
      ))}

      {selectedIncident && (
        <InfoWindow
          position={{ lat: selectedIncident.lat, lng: selectedIncident.lng }}
          onCloseClick={onInfoWindowClose}
        >
          <div style={{ color: '#1a1a1a', padding: '4px 8px', minWidth: '120px' }}>
            <strong style={{ fontSize: '0.94rem' }}>{selectedIncident.title}</strong>
            <p style={{ margin: '4px 0 0', fontSize: '0.82rem', color: '#666' }}>
              Type: {selectedIncident.type}
            </p>
            <p style={{ margin: '2px 0 0', fontSize: '0.77rem', color: selectedIncident.status === 'resolved' ? '#7bc142' : '#e67e22' }}>
              Status: {selectedIncident.status.toUpperCase()}
            </p>
          </div>
        </InfoWindow>
      )}
    </GoogleMap>
  );
}