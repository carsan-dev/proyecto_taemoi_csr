import { Component, Input } from '@angular/core';

import type { EditarAlumnoComponent } from '../editar-alumno.component';

/**
 * Foto del alumno ampliada (el `<dialog id="imageModal">` de la ficha de hoy). Sale tal cual (8b-3b) para que la ficha
 * nueva la use sin copiarla: `abrirModal` y `cerrarModal` siguen en la ficha que la pinta (`[ficha]="this"`).
 */
@Component({
  selector: 'app-foto-ampliada',
  standalone: true,
  templateUrl: './foto-ampliada.component.html',
  styleUrls: ['./foto-ampliada.component.scss'],
})
export class FotoAmpliadaComponent {
  @Input({ required: true }) ficha!: EditarAlumnoComponent;
}
