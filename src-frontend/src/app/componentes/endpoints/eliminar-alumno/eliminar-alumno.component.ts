import { Component, OnInit, OnDestroy } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { Subject, concat } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs/operators';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { getDeporteLabel } from '../../../enums/deporte';
import { attachSwalSelectSearch } from '../../../utils/swal-search.util';

@Component({
  selector: 'app-eliminar-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent],
  templateUrl: './eliminar-alumno.component.html',
  styleUrl: './eliminar-alumno.component.scss',
})
export class EliminarAlumnoComponent implements OnInit, OnDestroy {
  alumnos: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 10;
  totalPaginas: number = 0;
  nombreFiltro: string = '';
  mostrarInactivos: boolean = false;
  cargando: boolean = true;
  private searchSubject = new Subject<string>();
  private readonly storageKey = 'eliminarAlumnoEstado';

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly alumnoService: AlumnoService
  ) {}

  ngOnInit(): void {
    this.restaurarEstadoPaginacion();
    this.obtenerAlumnos();

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(500), // Wait 500ms after user stops typing
      distinctUntilChanged() // Only trigger if value actually changed
    ).subscribe(() => {
      this.paginaActual = 1;
      this.guardarEstadoPaginacion();
      this.obtenerAlumnos();
    });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  obtenerAlumnos() {
    this.cargando = true;
    this.endpointsService
      .obtenerAlumnos(
        this.paginaActual,
        this.tamanoPagina,
        this.nombreFiltro,
        this.mostrarInactivos
      )
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (response) => {
          this.alumnos = response.content.map((alumno: any) => ({
            ...alumno,
            activo: alumno.activo ?? true,
          }));
          this.totalPaginas = response.totalPages;

          if (this.totalPaginas > 0 && this.paginaActual > this.totalPaginas) {
            this.paginaActual = this.totalPaginas;
            this.guardarEstadoPaginacion();
            this.obtenerAlumnos();
            return;
          }

          this.guardarEstadoPaginacion();
        },
        error: () => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
  }

  cambiarPagina(pageNumber: number): void {
    if (this.cargando || pageNumber === this.paginaActual) {
      return;
    }
    this.paginaActual = pageNumber;
    this.guardarEstadoPaginacion();
    this.obtenerAlumnos();
  }

  darDeBajaAlumno(id: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno será dado de baja',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, dar de baja',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.darDeBajaAlumno(id).subscribe({
          next: () => {
            Swal.fire({
              title: 'Alumno dado de baja',
              text: 'El alumno ha sido dado de baja correctamente.',
              icon: 'success',
              timer: 2000,
            });
            this.obtenerAlumnos();
          },
          error: () => {
            Swal.fire({
              title: 'Error al dar de baja al alumno',
              text: 'Ha ocurrido un error al intentar dar de baja al alumno.',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  eliminarAlumno(id: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno será eliminado permanentemente',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarAlumnos(id).subscribe({
          next: () => {
            Swal.fire({
              title: 'Alumno eliminado',
              text: 'El alumno ha sido eliminado correctamente.',
              icon: 'success',
              timer: 2000,
            });
            this.obtenerAlumnos();
          },
          error: () => {
            Swal.fire({
              title: 'Error al eliminar alumno',
              text: 'Ha ocurrido un error al intentar eliminar al alumno.',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  darDeBajaAlumnosSeleccionados() {
    const alumnosSeleccionados = this.alumnos.filter(
      (alumno) => alumno.selected
    );

    if (alumnosSeleccionados.length > 0) {
      Swal.fire({
        title: '¿Estás seguro?',
        text: 'Los alumnos seleccionados serán dados de baja',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, darlos de baja',
      }).then((result) => {
        if (result.isConfirmed) {
          this.darDeBajaAlumnoSecuencial(alumnosSeleccionados);
        }
      });
    } else {
      Swal.fire({
        title: 'No hay alumnos seleccionados',
        text: 'Por favor, seleccione al menos un alumno para dar de baja.',
        icon: 'info',
      });
    }
  }

  eliminarAlumnosSeleccionados() {
    const alumnosSeleccionados = this.alumnos.filter(
      (alumno) => alumno.selected
    );

    if (alumnosSeleccionados.length > 0) {
      Swal.fire({
        title: '¿Estás seguro?',
        text: 'Los alumnos seleccionados serán eliminados permanentemente',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminarlos',
      }).then((result) => {
        if (result.isConfirmed) {
          this.eliminarAlumnoSecuencial(alumnosSeleccionados);
        }
      });
    } else {
      Swal.fire({
        title: 'No hay alumnos seleccionados',
        text: 'Por favor, seleccione al menos un alumno para eliminar.',
        icon: 'info',
      });
    }
  }

  darDeBajaAlumnoSecuencial(alumnosSeleccionados: any[]) {
    if (alumnosSeleccionados.length === 0) {
      Swal.fire({
        title: 'Alumnos dados de baja',
        text: 'Los alumnos seleccionados han sido dados de baja correctamente.',
        icon: 'success',
        timer: 2000,
      });
      this.obtenerAlumnos();
      return;
    }

    const alumno = alumnosSeleccionados.pop();

    this.endpointsService.darDeBajaAlumno(alumno.id).subscribe({
      next: () => {
        this.darDeBajaAlumnoSecuencial(alumnosSeleccionados);
      },
      error: () => {
        Swal.fire({
          title: 'Error al dar de baja al alumno',
          text: `Ha ocurrido un error al intentar dar de baja al alumno ${alumno.nombre} ${alumno.apellidos}.`,
          icon: 'error',
        });
        this.darDeBajaAlumnoSecuencial(alumnosSeleccionados);
      },
    });
  }

  eliminarAlumnoSecuencial(alumnosSeleccionados: any[]) {
    if (alumnosSeleccionados.length === 0) {
      Swal.fire({
        title: 'Alumnos eliminados',
        text: 'Los alumnos seleccionados han sido eliminados correctamente.',
        icon: 'success',
        timer: 2000,
      });
      this.obtenerAlumnos();
      return;
    }

    const alumno = alumnosSeleccionados.pop();

    this.endpointsService.eliminarAlumnos(alumno.id).subscribe({
      next: () => {
        this.eliminarAlumnoSecuencial(alumnosSeleccionados);
      },
      error: () => {
        Swal.fire({
          title: 'Error al eliminar alumno',
          text: `Ha ocurrido un error al intentar eliminar al alumno ${alumno.nombre} ${alumno.apellidos}.`,
          icon: 'error',
        });
        this.eliminarAlumnoSecuencial(alumnosSeleccionados);
      },
    });
  }

  filtrarPorNombre(): void {
    this.searchSubject.next(this.nombreFiltro);
  }

  alternarInactivos(): void {
    this.mostrarInactivos = !this.mostrarInactivos;
    this.paginaActual = 1;
    this.guardarEstadoPaginacion();
    this.obtenerAlumnos();
  }

  toggleSelectAll(event: any): void {
    const checked = event.target.checked;
    this.alumnos.forEach(alumno => alumno.selected = checked);
  }

  get hasSelectedAlumnos(): boolean {
    return this.alumnos.some(alumno => alumno.selected);
  }

  get allAlumnosSelected(): boolean {
    return this.alumnos.length > 0 && this.alumnos.every(alumno => alumno.selected);
  }

  private guardarEstadoPaginacion(): void {
    const estado = {
      paginaActual: this.paginaActual,
      tamanoPagina: this.tamanoPagina,
      nombreFiltro: this.nombreFiltro,
      mostrarInactivos: this.mostrarInactivos,
    };
    sessionStorage.setItem(this.storageKey, JSON.stringify(estado));
  }

  private restaurarEstadoPaginacion(): void {
    const estadoGuardado = sessionStorage.getItem(this.storageKey);
    if (!estadoGuardado) {
      return;
    }
    try {
      const estado = JSON.parse(estadoGuardado);
      this.paginaActual = estado.paginaActual || 1;
      this.tamanoPagina = estado.tamanoPagina || this.tamanoPagina;
      this.nombreFiltro = estado.nombreFiltro || '';
      this.mostrarInactivos = estado.mostrarInactivos || false;
    } catch (error) {
      console.error('Error parsing saved pagination state:', error);
    }
  }

  darDeAltaAlumno(alumnoId: number): void {
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
        Swal.fire({
          title: 'Error',
          text: 'Error al obtener los deportes del alumno',
          icon: 'error',
        });
      },
    });
  }

  private activarDeporteDeAlumno(alumnoId: number, deporte: string): void {
    this.alumnoService.activarDeporteDeAlumno(alumnoId, deporte).subscribe({
      next: () => {
        Swal.fire({
          title: 'Alumno dado de alta',
          text: `El alumno ha sido dado de alta en ${getDeporteLabel(deporte)}.`,
          icon: 'success',
          timer: 2000,
        });
        this.obtenerAlumnos();
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: error.error || 'Error al dar de alta al alumno',
          icon: 'error',
        });
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
        Swal.fire({
          title: 'Alumno dado de alta',
          text: `El alumno ha sido dado de alta en todos sus deportes: ${deportesLabels}.`,
          icon: 'success',
          timer: 2000,
        });
        this.obtenerAlumnos();
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: error.error || 'Error al activar algunos deportes',
          icon: 'error',
        });
      },
    });
  }
}
