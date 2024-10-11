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
  localizacionConcreta: [number, number] = [
    37.368258873076506, -6.160836430640318,
  ];
  map!: any;
  routingControl?: any;
  pulsatingIcon: any;

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}

  async ngAfterViewInit() {
    if (isPlatformBrowser(this.platformId)) {
      const L = await import('leaflet');

      // Importar los complementos de manera dinámica
      await Promise.all([
        import('leaflet-routing-machine'),
        import('leaflet-control-geocoder'),
        import('leaflet-minimap'),
      ]);

      // Inicializar el mapa
      this.map = L.map('map').setView(this.localizacionConcreta, 13);

      // Capas base
      const baseMaps = {
        Mapa: L.tileLayer(
          'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
          { maxZoom: 19 }
        ),
        Satélite: L.tileLayer(
          'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png',
          { maxZoom: 17 }
        ),
      };

      baseMaps['Mapa'].addTo(this.map);

      // Control de capas
      L.control.layers(baseMaps).addTo(this.map);

      // Geocodificador
      const geocoder = (L.Control as any)
        .geocoder({
          defaultMarkGeocode: false,
        })
        .on('markgeocode', (e: any) => {
          const bbox = e.geocode.bbox;
          const poly = L.polygon([
            bbox.getSouthEast(),
            bbox.getNorthEast(),
            bbox.getNorthWest(),
            bbox.getSouthWest(),
          ]);
          this.map.fitBounds(poly.getBounds());
        })
        .addTo(this.map);

      const screenWidth =
        window.innerWidth ||
        document.documentElement.clientWidth ||
        document.body.clientWidth;

      // Solo agregar el minimapa si el ancho es mayor a 576px
      if (screenWidth > 576) {
        const miniMapLayer = L.tileLayer(
          'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
        );
        const miniMap = new (L.Control as any).MiniMap(miniMapLayer, {
          toggleDisplay: true,
          position: 'topleft',
        }).addTo(this.map);
      }

      // Icono personalizado con animación
      this.pulsatingIcon = L.divIcon({
        className: 'pulsating-icon',
        html: '<i class="bi-geo-alt-fill text-danger fs-1"></i>',
        iconSize: [30, 30],
      });

      const marker = L.marker(this.localizacionConcreta, {
        icon: this.pulsatingIcon,
      }).addTo(this.map);
      marker
        .bindPopup(
          `
        <b>Ubicación:</b><br>
        C. Parada de la Cigüeña 36, 41806 Umbrete, Sevilla<br>
        <a href="https://www.google.com/maps/dir/?api=1&destination=${this.localizacionConcreta.join(
          ','
        )}" target="_blank">Abrir en Google Maps</a>
      `
        )
        .openPopup();
    }
  }

  calculateRoute() {
    if (isPlatformBrowser(this.platformId)) {
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          async (position) => {
            const L = await import('leaflet');
            await import('leaflet-routing-machine');

            const userLocation = [
              position.coords.latitude,
              position.coords.longitude,
            ];

            if (this.routingControl) {
              this.map.removeControl(this.routingControl);
            }
            this.routingControl = L.Routing.control({
              waypoints: [
                L.latLng(userLocation[0], userLocation[1]),
                L.latLng(
                  this.localizacionConcreta[0],
                  this.localizacionConcreta[1]
                ),
              ],
              routeWhileDragging: true,
              createMarker: (i: number, waypoint: any, n: number) => {
                return L.marker(waypoint.latLng, { icon: this.pulsatingIcon });
              },
            }).addTo(this.map);
          },
          (error) => {
            alert('Error al obtener la ubicación: ' + error.message);
          }
        );
      } else {
        alert('La geolocalización no está soportada por este navegador.');
      }
    }
  }

  shareOnFacebook() {
    if (isPlatformBrowser(this.platformId)) {
      const url = encodeURIComponent(
        'https://www.google.com/maps?q=' + this.localizacionConcreta.join(',')
      );
      window.open(
        `https://www.facebook.com/sharer/sharer.php?u=${url}`,
        '_blank'
      );
    }
  }

  shareOnTwitter() {
    if (isPlatformBrowser(this.platformId)) {
      const url = encodeURIComponent(
        'https://www.google.com/maps?q=' + this.localizacionConcreta.join(',')
      );
      const text = encodeURIComponent('¡Mira esta ubicación interesante!');
      window.open(
        `https://twitter.com/intent/tweet?url=${url}&text=${text}`,
        '_blank'
      );
    }
  }

  downloadLocation() {
    if (isPlatformBrowser(this.platformId)) {
      const data = `BEGIN:VCARD
VERSION:3.0
N:;Mi Ubicación;;;
FN:Mi Ubicación
ADR;TYPE=work:;;C. Parada de la Cigüeña 36;Umbrete;Sevilla;41806;España
END:VCARD`;

      const blob = new Blob([data], { type: 'text/vcard' });
      const url = window.URL.createObjectURL(blob);

      const a = document.createElement('a');
      a.href = url;
      a.download = 'ubicacion.vcf';
      a.click();
      window.URL.revokeObjectURL(url);
    }
  }
}
