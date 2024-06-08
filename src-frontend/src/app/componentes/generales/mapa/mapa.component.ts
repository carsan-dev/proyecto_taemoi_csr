import { AfterViewInit, Component } from '@angular/core';

@Component({
  selector: 'app-mapa',
  standalone: true,
  imports: [],
  templateUrl: './mapa.component.html',
  styleUrl: './mapa.component.scss',
})
export class MapaComponent implements AfterViewInit {
  localizacionConcreta: [number, number] = [37.3683719731871, -6.160806131969828];
  map: any;

  constructor() {}

  ngAfterViewInit() {
    if (typeof window !== 'undefined') {
      import('leaflet').then((L) => {
        const leaflet = L.default;
        this.map = leaflet.map('map').setView(this.localizacionConcreta, 13);

        leaflet.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          attribution:
            '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        }).addTo(this.map);

        const customIcon = leaflet.icon({
          iconUrl: '../../../../assets/media/custom-marker.png',
          iconSize: [38, 38],
          iconAnchor: [22, 94],
          popupAnchor: [-3, -76],
        });

        const marker = leaflet.marker(this.localizacionConcreta, { icon: customIcon }).addTo(this.map);
        marker.bindPopup('<b>Ubicación:</b><br>C. Parada de la Cigüeña, 34a, 41806 Umbrete, Sevilla').openPopup();
      }).catch(error => {
        console.error('Error loading Leaflet:', error);
      });
    }
  }
}
