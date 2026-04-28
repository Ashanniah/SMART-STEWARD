import { useCallback, useState } from 'react';
import { GoogleMap, useJsApiLoader, Marker, InfoWindow } from '@react-google-maps/api';

const mapContainerStyle = {
  width: '100%',
  height: '100%',
};

const defaultCenter = {
  lat: 10.2979,
  lng: 123.8965,
};

const darkMapStyles = [
  { elementType: 'geometry', stylers: [{ color: '#1a2e10' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#1a2e10' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#6e9050' }] },
  { featureType: 'administrative.locality', elementType: 'labels.text.fill', stylers: [{ color: '#a3c48a' }] },
  { featureType: 'poi', elementType: 'labels.text.fill', stylers: [{ color: '#6e9050' }] },
  { featureType: 'poi.park', elementType: 'geometry', stylers: [{ color: '#263d3e' }] },
  { featureType: 'poi.park', elementType: 'labels.text.fill', stylers: [{ color: '#6e9050' }] },
  { featureType: 'road', elementType: 'geometry', stylers: [{ color: '#3a5c22' }] },
  { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#2a4217' }] },
  { featureType: 'road', elementType: 'labels.text.fill', stylers: [{ color: '#a3c48a' }] },
  { featureType: 'road.highway', elementType: 'geometry', stylers: [{ color: '#4a7c10' }] },
  { featureType: 'road.highway', elementType: 'geometry.stroke', stylers: [{ color: '#1e3512' }] },
  { featureType: 'road.highway', elementType: 'labels.text.fill', stylers: [{ color: '#eef4e8' }] },
  { featureType: 'transit', elementType: 'geometry', stylers: [{ color: '#2a4217' }] },
  { featureType: 'transit.station', elementType: 'labels.text.fill', stylers: [{ color: '#a3c48a' }] },
  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#0e1926' }] },
  { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#515e6b' }] },
];

const defaultMapOptions = {
  disableDefaultUI: false,
  zoomControl: true,
  streetViewControl: false,
  mapTypeControl: false,
  fullscreenControl: true,
  styles: darkMapStyles,
};

const incidentIcon = {
  path: google.maps.SymbolPath.CIRCLE,
  scale: 10,
  fillColor: '#e67e22',
  fillOpacity: 1,
  strokeColor: '#eef4e8',
  strokeWeight: 2,
};

const resolvedIcon = {
  path: google.maps.SymbolPath.CIRCLE,
  scale: 8,
  fillColor: '#7bc142',
  fillOpacity: 1,
  strokeColor: '#eef4e8',
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
}) {
  const { isLoaded, loadError } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: 'AIzaSyCDzyU7r_W1JUVQi8JT3PG9gteHQm6inRI',
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
    ? { ...defaultMapOptions, zoomControl: true, mapTypeControl: true }
    : defaultMapOptions;

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