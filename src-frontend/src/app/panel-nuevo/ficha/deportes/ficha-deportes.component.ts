import { CommonModule } from '@angular/common';
import { Component, Input, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { MascotaComponent } from '../../mascota/mascota.component';
import { SearchableSelectDirective } from '../../../directives/searchable-select.directive';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { fondoCinturon, tiempoConGrado } from '../../alumnos/alumno-vista';
import { AcentoEscuelaActual } from '../../acento-escuela.directive';
import { fecha, numero, plural } from '../../formato/formato-es';
import { variablesDelTema } from '../../tema-escuela';
import type { FichaAlumnoComponent } from '../ficha-alumno.component';

/**
 * Pestaña «Deportes» de la ficha nueva (8b-3b): un bloque por disciplina con su marca y las cajas Grado, Tarifa, Alta y
 * antigüedad, Licencia federativa, Competidor y Grupos y turnos. Los campos, los «Aplicar cambios» y los botones son los
 * de la ficha de hoy y llaman a sus mismos métodos (`ficha`), así que se envía y se confirma lo mismo.
 */
@Component({
  selector: 'app-ficha-deportes',
  standalone: true,
  imports: [MascotaComponent, CommonModule, FormsModule, SearchableSelectDirective],
  templateUrl: './ficha-deportes.component.html',
  styleUrls: ['./ficha-deportes.component.scss'],
})
export class FichaDeportesComponent {
  @Input({ required: true }) ficha!: FichaAlumnoComponent;

  readonly fecha = fecha;
  readonly numero = numero;
  readonly plural = plural;

  private readonly temaActual = inject(AcentoEscuelaActual);
  /** 8b-C: las listas flotantes van en el body, fuera del `.pn` de la ficha: llevan copiado el tema de la escuela. */
  readonly variablesDesplegable = computed(() => variablesDelTema(this.temaActual.aplicado()));

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
