import { Component, inject } from '@angular/core';

import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { menosMovimiento } from '../capas/capa-modal';
import { Aviso, AvisosService, DURACION_AVISO } from './avisos.service';

/**
 * Región de avisos del panel nuevo (8b-D1): abajo a la derecha y de 360 px; en el móvil, a lo ancho y encima de la barra
 * inferior. Cada aviso lleva su icono, su texto, la acción opcional y la X; los que se cierran solos, la barra de tiempo
 * (quieta con `prefers-reduced-motion`). Los lectores de pantalla los oyen por dos regiones vivas ocultas que existen
 * desde que se crea la región, vacías: `role=status` (éxito e información) y `role=alert` (error). La crea
 * `AvisosService`.
 */
@Component({
  selector: 'pn-avisos',
  standalone: true,
  templateUrl: './avisos.component.html',
  styleUrls: ['./avisos.component.scss'],
  host: { class: 'pn pn-capa' },
  hostDirectives: [AcentoEscuelaDirective],
})
export class AvisosComponent {
  readonly avisos = inject(AvisosService);
  readonly duracion = `${DURACION_AVISO}ms`;
  readonly quieta = menosMovimiento();

  esError(aviso: Aviso): boolean {
    return aviso.tipo === 'error';
  }
}
