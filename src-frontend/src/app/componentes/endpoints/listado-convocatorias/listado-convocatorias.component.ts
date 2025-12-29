import { ChangeDetectorRef, Component, LOCALE_ID, OnInit, Pipe, PipeTransform } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule, registerLocaleData } from '@angular/common';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { formatDate } from '../../../utilities/formatear-fecha';
import { getGradoTextStyle } from '../../../utilities/grado-colors';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { trigger, transition, style, animate } from '@angular/animations';
import localeEs from '@angular/common/locales/es';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { finalize } from 'rxjs/operators';

// Register Spanish locale
registerLocaleData(localeEs, 'es');

@Pipe({
  name: 'filter',
  standalone: true
})
export class FilterPipe implements PipeTransform {
  transform(items: any[], property: string, value: any): any[] {
    if (!items || !property) {
      return items;
    }
    return items.filter(item => item[property] === value);
  }
}

@Pipe({
  name: 'capitalizeMonth',
  standalone: true
})
export class CapitalizeMonthPipe implements PipeTransform {
  transform(value: string | Date, format: string = 'dd MMMM yyyy'): string {
    if (!value) return '';

    const date = new Date(value);
    const formattedDate = new Intl.DateTimeFormat('es-ES', {
      day: '2-digit',
      month: 'long',
      year: 'numeric'
    }).format(date);

    // Capitalize first letter of month
    return formattedDate.replaceAll(/\b\w/g, (char, index) => {
      // Capitalize first character and the character after a space (month name)
      if (index === 0 || formattedDate[index - 1] === ' ') {
        return char.toUpperCase();
      }
      return char;
    });
  }
}

@Component({
  selector: 'app-listado-convocatorias',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent, FilterPipe, CapitalizeMonthPipe, SkeletonCardComponent],
  templateUrl: './listado-convocatorias.component.html',
  styleUrl: './listado-convocatorias.component.scss',
  providers: [{ provide: LOCALE_ID, useValue: 'es' }],
  animations: [
    trigger('slideDown', [
      transition(':enter', [
        style({ height: '0', opacity: '0', overflow: 'hidden' }),
        animate('300ms ease-out', style({ height: '*', opacity: '1' }))
      ]),
      transition(':leave', [
        animate('300ms ease-in', style({ height: '0', opacity: '0', overflow: 'hidden' }))
      ])
    ])
  ]
})
export class ListadoConvocatoriasComponent implements OnInit {
  convocatorias: any[] = [];
  convocatoriaSeleccionada: any;
  alumnosInscritos: any[] = [];
  alumnos: any[] = [];
  alumnosFiltrados: any[] = [];
  deportes = ['TAEKWONDO', 'KICKBOXING'];
  deporteSeleccionado = 'TAEKWONDO';
  cargando: boolean = true; // Loading state
  cargandoAlumnos: boolean = false; // Loading state for alumnos


  // Pagination for convocatorias list
  paginaActualConvocatorias = 1;
  tamanoPaginaConvocatorias = 5;
  totalPaginasConvocatorias = 0;

  // Pagination for alumnos
  paginaActual = 1;
  tamanoPagina = 10;
  alumnosCargadosCompletamente = false;

  alumnoSeleccionado: number | null = null;

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly changeDetectorRef: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.obtenerConvocatorias();
  }

  obtenerConvocatorias(): void {
    this.cargando = true;
    this.endpointsService
      .obtenerConvocatorias(this.deporteSeleccionado)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (data) => {
          this.convocatorias = data.map((conv: any) => ({ ...conv, expanded: false }));
          this.totalPaginasConvocatorias = Math.ceil(this.convocatorias.length / this.tamanoPaginaConvocatorias);
          this.paginaActualConvocatorias = 1;
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudo obtener las convocatorias.',
            icon: 'error',
          });
        },
      });
  }

  get convocatoriasPaginadas(): any[] {
    const start = (this.paginaActualConvocatorias - 1) * this.tamanoPaginaConvocatorias;
    const end = start + this.tamanoPaginaConvocatorias;
    return this.convocatorias.slice(start, end);
  }

  cambiarPaginaConvocatorias(pageNumber: number): void {
    if (this.cargando || pageNumber === this.paginaActualConvocatorias) {
      return;
    }
    this.paginaActualConvocatorias = pageNumber;
  }

  getGradoStyle(tipoGrado: string): string {
    return getGradoTextStyle(tipoGrado);
  }

  crearConvocatoria(): void {
    // Get today's date in YYYY-MM-DD format for the default value
    const today = new Date().toISOString().split('T')[0];

    Swal.fire({
      title: 'Crear Convocatoria',
      html: `
        <div style="text-align: left; margin-bottom: 20px;">
          <label for="swal-deporte" style="display: block; margin-bottom: 5px; font-weight: 600;">
            Deporte:
          </label>
          <select id="swal-deporte" class="swal2-input" style="width: 100%; padding: 10px; margin: 0;">
            <option value="TAEKWONDO" ${this.deporteSeleccionado === 'TAEKWONDO' ? 'selected' : ''}>TAEKWONDO</option>
            <option value="KICKBOXING" ${this.deporteSeleccionado === 'KICKBOXING' ? 'selected' : ''}>KICKBOXING</option>
          </select>
        </div>
        <div style="text-align: left;">
          <label for="swal-fecha" style="display: block; margin-bottom: 5px; font-weight: 600;">
            Fecha de Convocatoria:
          </label>
          <input type="date" id="swal-fecha" class="swal2-input" value="${today}" style="width: 100%; padding: 10px; margin: 0;">
        </div>
      `,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Crear',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      preConfirm: () => {
        const deporteElement = document.getElementById('swal-deporte') as HTMLSelectElement;
        const fechaElement = document.getElementById('swal-fecha') as HTMLInputElement;

        const deporte = deporteElement.value;
        const fecha = fechaElement.value;

        if (!fecha) {
          Swal.showValidationMessage('Por favor, selecciona una fecha');
          return false;
        }

        return { deporte, fecha };
      },
    }).then((result) => {
      if (result.isConfirmed && result.value) {
        const { deporte, fecha } = result.value;

        // Show confirmation dialog
        Swal.fire({
          title: '¿Estás seguro?',
          html: `Vas a crear una convocatoria de <strong>${deporte}</strong> para <strong>${new Date(fecha + 'T00:00:00').toLocaleDateString('es-ES', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
          })}</strong>.`,
          icon: 'warning',
          showCancelButton: true,
          confirmButtonText: 'Sí, crear',
          cancelButtonText: 'Cancelar',
          confirmButtonColor: '#3085d6',
          cancelButtonColor: '#d33',
        }).then((confirmResult) => {
          if (confirmResult.isConfirmed) {
            // Create the convocatoria
            const nuevaConvocatoria = {
              fechaConvocatoria: new Date(fecha + 'T00:00:00'),
              deporte: deporte,
            };

            this.endpointsService.crearConvocatoria(nuevaConvocatoria).subscribe({
              next: (data) => {
                Swal.fire({
                  title: '¡Creada!',
                  text: 'La convocatoria ha sido creada correctamente.',
                  icon: 'success',
                  timer: 2000,
                });
                this.convocatorias.push({ ...data, expanded: false });
                this.totalPaginasConvocatorias = Math.ceil(this.convocatorias.length / this.tamanoPaginaConvocatorias);
              },
              error: () => {
                Swal.fire({
                  title: 'Error',
                  text: 'No se pudo crear la convocatoria.',
                  icon: 'error',
                });
              },
            });
          }
        });
      }
    });
  }

  seleccionarConvocatoria(convocatoria: any): void {
    // Toggle expansion
    convocatoria.expanded = !convocatoria.expanded;

    // If expanding, load the data
    if (convocatoria.expanded) {
      this.convocatoriaSeleccionada = convocatoria;

      this.endpointsService
        .obtenerAlumnosDeConvocatoria(convocatoria.id)
        .subscribe({
          next: (data) => {
            this.alumnosInscritos = data.map((alumno) => ({
              ...alumno,
              id: alumno.id,
            }));
            this.filtrarAlumnos();
          },
          error: () => {
            Swal.fire({
              title: 'Error',
              text: 'No se pudieron obtener los alumnos de la convocatoria.',
              icon: 'error',
            });
            convocatoria.expanded = false;
          },
        });
    } else {
      // If collapsing, clear the selection if it's the current one
      if (this.convocatoriaSeleccionada?.id === convocatoria.id) {
        this.convocatoriaSeleccionada = null;
        this.alumnosInscritos = [];
      }
    }
  }

  eliminarConvocatoria(convocatoria: any): void {
    Swal.fire({
      title: '¿Estás seguro?',
      text: `Vas a eliminar la convocatoria del ${formatDate(
        convocatoria.fechaConvocatoria
      )}. Esta acción no se puede deshacer.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarConvocatoria(convocatoria.id).subscribe({
          next: () => {
            Swal.fire({
              title: '¡Eliminado!',
              text: 'La convocatoria ha sido eliminada.',
              icon: 'success',
              timer: 2000,
            });
            this.convocatorias = this.convocatorias.filter(
              (c) => c.id !== convocatoria.id
            );
            if (this.convocatoriaSeleccionada?.id === convocatoria.id) {
              this.convocatoriaSeleccionada = null;
              this.alumnosInscritos = [];
            }
          },
          error: () => {
            Swal.fire({
              title: 'Error',
              text: 'No se pudo eliminar la convocatoria.',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  cargarAlumnosPaginados(): void {
    if (this.alumnosCargadosCompletamente) return;

    this.endpointsService
      .obtenerAlumnos(this.paginaActual, this.tamanoPagina)
      .subscribe({
        next: (response) => {
          this.alumnos = [...this.alumnos, ...response.content];
          this.paginaActual++;

          if (response.totalPages === this.paginaActual) {
            this.alumnosCargadosCompletamente = true;
          }

          this.filtrarAlumnos();
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudo cargar los alumnos paginados.',
            icon: 'error',
          });
        },
      });
  }

  filtrarAlumnos(): void {
    if (!this.convocatoriaSeleccionada) {
      this.alumnosFiltrados = [];
      return;
    }

    this.cargandoAlumnos = true;
    this.endpointsService
      .obtenerAlumnosElegiblesParaConvocatoria(this.convocatoriaSeleccionada.deporte)
      .subscribe({
        next: (alumnos) => {
          // Filter out already inscribed students
          this.alumnosFiltrados = alumnos.filter(
            (alumno) => !this.alumnosInscritos.some(
              (inscrito) => inscrito.alumnoId === alumno.id
            )
          );
          this.cargandoAlumnos = false;
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudieron cargar los alumnos elegibles.',
            icon: 'error',
          });
          this.cargandoAlumnos = false;
        },
      });
  }

  actualizarGrados(convocatoria: any): void {
    const hayNoPagados = this.alumnosInscritos.some((alumno) => !alumno.pagado);

    if (hayNoPagados) {
      Swal.fire({
        title: '¿Estás seguro?',
        text: 'Hay alumnos que no han pagado la convocatoria. ¿Deseas continuar?',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: 'Sí, actualizar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
      }).then((result) => {
        if (result.isConfirmed) {
          this.procesarActualizacionDeGrados(convocatoria);
        }
      });
    } else {
      this.procesarActualizacionDeGrados(convocatoria);
    }
  }

  procesarActualizacionDeGrados(convocatoria: any): void {
    this.endpointsService
      .actualizarGradosDeConvocatoria(convocatoria.id)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Éxito',
            text: 'Grados actualizados correctamente.',
            icon: 'success',
            timer: 2000,
          });
          this.seleccionarConvocatoria(convocatoria);
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudieron actualizar los grados.',
            icon: 'error',
          });
        },
      });
  }

  actualizarAlumnoConvocatoria(alumno: any): void {
    const alumnoConvocatoriaDTO = {
      cuantiaExamen: alumno.cuantiaExamen,
      pagado: alumno.pagado,
    };

    this.endpointsService
      .actualizarAlumnoConvocatoria(alumno.id, alumnoConvocatoriaDTO)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Éxito',
            text: 'Datos del alumno actualizados correctamente.',
            icon: 'success',
            timer: 2000,
          });
          this.seleccionarConvocatoria(this.convocatoriaSeleccionada);
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'No se pudo actualizar el alumno.',
            icon: 'error',
          });
        },
      });
  }

  agregarAlumnoAConvocatoria(): void {
    if (!this.alumnoSeleccionado || !this.convocatoriaSeleccionada) return;

    Swal.fire({
      title: 'Selecciona el tipo de producto',
      text: '¿Asignar el precio por antigüedad o por recompensa?',
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Por Recompensa',
      cancelButtonText: 'Por Antigüedad',
    }).then((result) => {
      const porRecompensa = result.isConfirmed; // true si selecciona recompensa
      this.endpointsService
        .agregarAlumnoAConvocatoria(
          this.alumnoSeleccionado!,
          this.convocatoriaSeleccionada.id,
          porRecompensa
        )
        .subscribe({
          next: () => {
            Swal.fire({
              title: 'Alumno agregado',
              text: 'El alumno ha sido asignado correctamente.',
              icon: 'success',
              timer: 2000,
            });
            this.seleccionarConvocatoria(this.convocatoriaSeleccionada);
          },
          error: () => {
            Swal.fire({
              title: 'Error',
              text: 'No se pudo agregar al alumno.',
              icon: 'error',
            });
          },
        });
    });
  }

  eliminarAlumnoDeConvocatoria(alumno: any): void {
    if (!this.convocatoriaSeleccionada) return;

    Swal.fire({
      title: '¿Estás seguro?',
      text: `Vas a eliminar al alumno ${alumno.nombre} de la convocatoria.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService
          .eliminarAlumnoDeConvocatoria(
            alumno.alumnoId,
            this.convocatoriaSeleccionada.id
          )
          .subscribe({
            next: () => {
              Swal.fire({
                title: '¡Eliminado!',
                text: 'El alumno ha sido eliminado de la convocatoria.',
                icon: 'success',
                timer: 2000,
              });
              this.seleccionarConvocatoria(this.convocatoriaSeleccionada);
            },
            error: () => {
              Swal.fire({
                title: 'Error',
                text: 'No se pudo eliminar al alumno.',
                icon: 'error',
              });
            },
          });
      }
    });
  }

  generarReporte(convocatoria: any): void {
    // Show loading message
    Swal.fire({
      title: 'Generando informe...',
      text: 'Por favor, espere mientras se genera el PDF',
      allowOutsideClick: false,
      didOpen: () => {
        Swal.showLoading();
      }
    });

    this.endpointsService.descargarInformePDFConvocatoria(convocatoria.id).subscribe({
      next: (blob: Blob) => {
        // Close loading message
        Swal.close();

        // Create download link
        const url = globalThis.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;

        // Format filename
        const fechaStr = new Date(convocatoria.fechaConvocatoria).toLocaleDateString('es-ES').replaceAll('/', '_');
        link.download = `informe_convocatoria_${convocatoria.deporte}_${fechaStr}.pdf`;

        // Trigger download
        link.click();

        // Cleanup
        globalThis.URL.revokeObjectURL(url);

        Swal.fire({
          title: 'Éxito',
          text: 'El informe se ha descargado correctamente.',
          icon: 'success',
          timer: 2000,
        });
      },
      error: () => {
        Swal.close();
        Swal.fire({
          title: 'Error',
          text: 'No se pudo generar el informe.',
          icon: 'error',
        });
      },
    });
  }
}
