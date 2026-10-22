import { Component, inject } from '@angular/core';

import { Aviso, AvisosService, DURACION_AVISO } from './avisos.service';

/**
 * Región de avisos del panel nuevo (8b-D1): abajo a la derecha y de 360 px; en el móvil, a lo ancho y encima de la barra
 * inferior. Cada aviso lleva `role=status` (el error, `role=alert`), su icono, su texto, la acción opcional y la X; los
 * que se cierran solos, la barra de tiempo. La crea `AvisosService`.
 */
@Component({
  selector: 'pn-avisos',
  standalone: true,
  templateUrl: './avisos.component.html',
  styleUrls: ['./avisos.component.scss'],
  host: { class: 'pn pn-capa' },
})
export class AvisosComponent {
  readonly avisos = inject(AvisosService);
  readonly duracion = `${DURACION_AVISO}ms`;

  rol(aviso: Aviso): 'status' | 'alert' {
    return aviso.tipo === 'error' ? 'alert' : 'status';
  }
}
