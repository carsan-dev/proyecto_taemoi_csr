import Swal from 'sweetalert2';
import { concat } from 'rxjs';

import { getDeporteLabel } from '../../../enums/deporte';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { attachSwalSelectSearch } from '../../../utils/swal-search.util';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';

/**
 * «Dar de baja» y «Dar de alta» del listado de alumnos, con sus confirmaciones de siempre. Salen tal cual del listado de
 * hoy (8b-3a) para que el listado nuevo haga exactamente lo mismo. `recargar` vuelve a pedir la página tras el cambio.
 */
export class BajasAltasAlumno {
  constructor(
    private readonly endpoints: EndpointsService,
    private readonly alumnoService: AlumnoService,
    private readonly recargar: () => void
  ) {}

  darDeAlta(alumnoId: number): void {
    // Primero obtener los deportes del alumno para ver cuáles están inactivos
    this.alumnoService.obtenerDeportesDelAlumno(alumnoId).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => {
        const deportesInactivos = deportes.filter(d => d.activo === false);

        if (deportesInactivos.length === 0) {
          Swal.fire({
            title: 'Información',
            text: 'El alumno ya tiene todos sus deportes activos.',
            icon: 'info',
          });
          return;
        }

        // Si solo hay un deporte inactivo, activarlo directamente
        if (deportesInactivos.length === 1) {
          this.activarDeporteDeAlumno(alumnoId, deportesInactivos[0].deporte);
          return;
        }

        // Si hay múltiples deportes inactivos, mostrar selector con opción "Todos"
        const deportesOptions = deportesInactivos
          .map(d => `<option value="${d.deporte}">${getDeporteLabel(d.deporte)}</option>`)
          .join('');

        Swal.fire({
          title: 'Selecciona el deporte a activar',
          html: `
            <select id="deporte-select" class="swal2-input">
              <option value="">Selecciona un deporte...</option>
              <option value="TODOS">✓ Todos los deportes</option>
              ${deportesOptions}
            </select>
          `,
          showCancelButton: true,
          confirmButtonText: 'Dar de Alta',
          cancelButtonText: 'Cancelar',
          didOpen: () => {
            attachSwalSelectSearch({ selectId: 'deporte-select', placeholder: 'Buscar deporte...' });
          },
          preConfirm: () => {
            const select = document.getElementById('deporte-select') as HTMLSelectElement;
            if (!select.value) {
              Swal.showValidationMessage('Por favor selecciona un deporte');
              return false;
            }
            return select.value;
          },
        }).then((result) => {
          if (result.isConfirmed && result.value) {
            if (result.value === 'TODOS') {
              this.activarTodosLosDeportes(alumnoId, deportesInactivos);
            } else {
              this.activarDeporteDeAlumno(alumnoId, result.value);
            }
          }
        });
      },
      error: () => {
        showErrorToast('Error al obtener los deportes del alumno');
      },
    });
  }

  private activarDeporteDeAlumno(alumnoId: number, deporte: string): void {
    this.alumnoService.activarDeporteDeAlumno(alumnoId, deporte).subscribe({
      next: () => {
        showSuccessToast(`Alumno dado de alta en ${getDeporteLabel(deporte)}`);
        this.recargar();
      },
      error: (error) => {
        showErrorToast(error.error || 'Error al dar de alta al alumno');
      },
    });
  }

  private activarTodosLosDeportes(alumnoId: number, deportesInactivos: AlumnoDeporteDTO[]): void {
    const activarDeportesObservables = deportesInactivos.map(deporte =>
      this.alumnoService.activarDeporteDeAlumno(alumnoId, deporte.deporte)
    );

    concat(...activarDeportesObservables).subscribe({
      complete: () => {
        const deportesLabels = deportesInactivos.map(d => getDeporteLabel(d.deporte)).join(', ');
        showSuccessToast(`Alumno dado de alta en todos sus deportes: ${deportesLabels}`);
        this.recargar();
      },
      error: (error) => {
        showErrorToast(error.error || 'Error al activar algunos deportes');
      },
    });
  }

  darDeBaja(alumnoId: number): void {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno será dado de baja.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, dar de baja',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpoints.darDeBajaAlumno(alumnoId).subscribe({
          next: () => {
            showSuccessToast('Alumno dado de baja correctamente');
            this.recargar();
          },
          error: () => {
            showErrorToast('Error al dar de baja al alumno');
          },
        });
      }
    });
  }
}
