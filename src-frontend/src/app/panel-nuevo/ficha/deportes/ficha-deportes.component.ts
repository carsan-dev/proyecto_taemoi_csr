import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { SearchableSelectDirective } from '../../../directives/searchable-select.directive';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { fondoCinturon, tiempoConGrado } from '../../alumnos/alumno-vista';
import { fecha, numero, plural } from '../../formato/formato-es';
import type { FichaAlumnoComponent } from '../ficha-alumno.component';

/**
 * Pestaña «Deportes» de la ficha nueva (8b-3b): un bloque por disciplina con su marca y las cajas Grado, Tarifa, Alta y
 * antigüedad, Licencia federativa, Competidor y Grupos y turnos. Los campos, los «Aplicar cambios» y los botones son los
 * de la ficha de hoy y llaman a sus mismos métodos (`ficha`), así que se envía y se confirma lo mismo.
 */
@Component({
  selector: 'app-ficha-deportes',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectDirective],
  templateUrl: './ficha-deportes.component.html',
  styleUrls: ['./ficha-deportes.component.scss'],
})
export class FichaDeportesComponent {
  @Input({ required: true }) ficha!: FichaAlumnoComponent;

  readonly fecha = fecha;
  readonly numero = numero;
  readonly plural = plural;

  /** Fondo del cinturón del grado actual, o nulo sin grado. */
  fondo(d: AlumnoDeporteDTO): string | null {
    return d.grado ? fondoCinturon(d.grado, d.deporte) : null;
  }

  /** «3 años y 6 meses con el grado» o «Desde hoy». Vacío sin fecha. */
  tiempoConGrado(d: AlumnoDeporteDTO): string {
    const tiempo = tiempoConGrado(d.fechaGrado, new Date());
    if (!tiempo) {
      return '';
    }
    return tiempo === 'Desde hoy' ? tiempo : `${tiempo} con el grado`;
  }

  quitarTexto(turno: any): string {
    return `${turno.diaSemana} ${turno.horaInicio} - ${turno.horaFin}`;
  }
}
