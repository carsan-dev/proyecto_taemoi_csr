import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { SearchableSelectDirective } from '../../../../directives/searchable-select.directive';
import type { EditarAlumnoComponent } from '../editar-alumno.component';

/**
 * Diálogos de la ficha del alumno: convocatorias, quitar de una convocatoria, WhatsApp, agregar deporte y actualizar
 * grado. Salen tal cual de la ficha de hoy (8b-3b) para que la ficha nueva los use sin copiarlos: el estado y los
 * métodos son los de la ficha que los pinta (`[ficha]="this"`), así que lo que se envía y se confirma no cambia.
 */
@Component({
  selector: 'app-dialogos-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectDirective],
  templateUrl: './dialogos-alumno.component.html',
  styleUrls: ['./dialogos-alumno.component.scss'],
})
export class DialogosAlumnoComponent {
  @Input({ required: true }) ficha!: EditarAlumnoComponent;
}
