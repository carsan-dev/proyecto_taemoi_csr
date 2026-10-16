import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';

/**
 * Aporta el CSS de Leaflet y sus plugins solo cuando se renderiza el mapa
 * (que va en un @defer), en lugar de incluirlo en el CSS global inicial.
 * Sin encapsulación: Leaflet crea el DOM por su cuenta.
 */
@Component({
  selector: 'app-mapa-leaflet-estilos',
  standalone: true,
  template: '',
  styleUrls: ['./mapa-leaflet-estilos.component.scss'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { hidden: '' },
})
export class MapaLeafletEstilosComponent {}
