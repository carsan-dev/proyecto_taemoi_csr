import { AfterViewInit, Component, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser, CommonModule } from '@angular/common';

@Component({
  selector: 'app-mapa',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mapa.component.html',
  styleUrls: ['./mapa.component.scss'],
})
export class MapaComponent implements AfterViewInit {
  localizacionConcreta: [number, number] = [37.368258873076506, -6.160836430640318];
  map: any;
  routingControl?: any;
  pulsatingIcon: any;
  L: any;

  constructor(@Inject(PLATFORM_ID) private readonly platformId: Object) {}

  ngAfterViewInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.initializeMap();
    }
  }

  private async initializeMap(): Promise<void> {
    try {
      // Import Leaflet
      this.L = await import('leaflet');

      // Import plugins
      await Promise.all([
        import('leaflet-routing-machine'),
        import('leaflet-control-geocoder'),
        import('leaflet-minimap'),
      ]);

      // Initialize map
      this.map = this.L.map('map').setView(this.localizacionConcreta, 13);

      // Add base layers
      const mapaLayer = this.L.tileLayer(
        'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        {
          maxZoom: 19,
          attribution: '© OpenStreetMap contributors'
        }
      );

      const sateliteLayer = this.L.tileLayer(
        'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png',
        {
          maxZoom: 17,
          attribution: '© OpenTopoMap contributors'
        }
      );

      const baseMaps = {
        'Mapa': mapaLayer,
        'Satélite': sateliteLayer
      };

      // Add default layer
      mapaLayer.addTo(this.map);

      // Add layer control
      this.L.control.layers(baseMaps).addTo(this.map);

      // Add geocoder with safe error handling
      try {
        const geocoder = (this.L.Control as any).geocoder({
          defaultMarkGeocode: false,
        });

        geocoder.on('markgeocode', (e: any) => {
          try {
            if (e && e.geocode && e.geocode.bbox) {
              const bbox = e.geocode.bbox;
              const poly = this.L.polygon([
                bbox.getSouthEast(),
                bbox.getNorthEast(),
                bbox.getNorthWest(),
                bbox.getSouthWest(),
              ]);
              this.map.fitBounds(poly.getBounds());
            }
          } catch (err) {
            console.error('Error handling geocode result:', err);
          }
        });

        geocoder.addTo(this.map);
      } catch (err) {
        console.warn('Geocoder plugin not available:', err);
      }

      // Add minimap on larger screens
      const screenWidth = window.innerWidth || document.documentElement.clientWidth || document.body.clientWidth;

      if (screenWidth > 576) {
        try {
          const miniMapLayer = this.L.tileLayer(
            'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
          );
          const miniMap = new (this.L.Control as any).MiniMap(miniMapLayer, {
            toggleDisplay: true,
            position: 'topleft',
          });
          miniMap.addTo(this.map);
        } catch (err) {
          console.warn('Minimap plugin not available:', err);
        }
      }

      // Create custom pulsating icon
      this.pulsatingIcon = this.L.divIcon({
        className: 'pulsating-icon',
        html: '<i class="bi-geo-alt-fill text-danger fs-1"></i>',
        iconSize: [30, 30],
      });

      // Add marker
      const marker = this.L.marker(this.localizacionConcreta, {
        icon: this.pulsatingIcon,
      }).addTo(this.map);

      // Add popup
      const lat = this.localizacionConcreta[0];
      const lng = this.localizacionConcreta[1];

      marker.bindPopup(`
        <b>Ubicación:</b><br>
        C. Parada de la Cigüeña 36, 41806 Umbrete, Sevilla<br>
        <a href="https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}" target="_blank">Abrir en Google Maps</a>
      `).openPopup();

    } catch (error) {
      console.error('Error initializing map:', error);
    }
  }

  calculateRoute(): void {
    if (!isPlatformBrowser(this.platformId) || !this.map || !this.L) {
      return;
    }

    if (!navigator.geolocation) {
      alert('La geolocalización no está soportada por este navegador.');
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        try {
          const userLocation = [position.coords.latitude, position.coords.longitude];

          // Remove existing routing control
          if (this.routingControl) {
            this.map.removeControl(this.routingControl);
          }

          // Create new routing control
          this.routingControl = (this.L as any).Routing.control({
            waypoints: [
              this.L.latLng(userLocation[0], userLocation[1]),
              this.L.latLng(this.localizacionConcreta[0], this.localizacionConcreta[1]),
            ],
            routeWhileDragging: true,
            createMarker: (i: number, waypoint: any, n: number) => {
              return this.L.marker(waypoint.latLng, { icon: this.pulsatingIcon });
            },
          }).addTo(this.map);
        } catch (error) {
          console.error('Error creating route:', error);
          alert('Error al calcular la ruta. Por favor, inténtalo de nuevo.');
        }
      },
      (error) => {
        console.error('Geolocation error:', error);
        alert('Error al obtener tu ubicación: ' + error.message);
      }
    );
  }

  shareOnFacebook(): void {
    if (isPlatformBrowser(this.platformId)) {
      const lat = this.localizacionConcreta[0];
      const lng = this.localizacionConcreta[1];
      const url = encodeURIComponent(`https://www.google.com/maps?q=${lat},${lng}`);
      window.open(`https://www.facebook.com/sharer/sharer.php?u=${url}`, '_blank');
    }
  }

  shareOnTwitter(): void {
    if (isPlatformBrowser(this.platformId)) {
      const lat = this.localizacionConcreta[0];
      const lng = this.localizacionConcreta[1];
      const url = encodeURIComponent(`https://www.google.com/maps?q=${lat},${lng}`);
      const text = encodeURIComponent('¡Mira esta ubicación interesante!');
      window.open(`https://twitter.com/intent/tweet?url=${url}&text=${text}`, '_blank');
    }
  }

  downloadLocation(): void {
    if (isPlatformBrowser(this.platformId)) {
      const data = `BEGIN:VCARD
VERSION:3.0
N:;Mi Ubicación;;;
FN:Mi Ubicación
ADR;TYPE=work:;;C. Parada de la Cigüeña 36;Umbrete;Sevilla;41806;España
END:VCARD`;

      const blob = new Blob([data], { type: 'text/vcard' });
      const url = globalThis.URL.createObjectURL(blob);

      const a = document.createElement('a');
      a.href = url;
      a.download = 'ubicacion.vcf';
      a.click();
      globalThis.URL.revokeObjectURL(url);
    }
  }
}
