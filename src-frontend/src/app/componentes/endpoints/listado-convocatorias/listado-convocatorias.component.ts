import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { formatDate } from '../../../utilities/formatear-fecha';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';

@Component({
  selector: 'app-listado-convocatorias',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent],
  templateUrl: './listado-convocatorias.component.html',
  styleUrl: './listado-convocatorias.component.scss',
})
export class ListadoConvocatoriasComponent implements OnInit {
  convocatorias: any[] = [];
  convocatoriaSeleccionada: any;
  alumnosInscritos: any[] = [];
  alumnos: any[] = [];
  alumnosFiltrados: any[] = [];
  deportes = ['TAEKWONDO', 'KICKBOXING'];
  deporteSeleccionado = 'TAEKWONDO';

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
    this.cargarAlumnosPaginados();
  }

  obtenerConvocatorias(): void {
    this.endpointsService
      .obtenerConvocatorias(this.deporteSeleccionado)
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
    this.paginaActualConvocatorias = pageNumber;
  }

  crearConvocatoria(): void {
    const nuevaConvocatoria = {
      fechaConvocatoria: new Date(),
      deporte: this.deporteSeleccionado,
    };
    this.endpointsService.crearConvocatoria(nuevaConvocatoria).subscribe({
      next: (data) => {
        this.convocatorias.push(data);
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
      this.alumnosFiltrados = this.alumnos.filter(
        (alumno) => alumno.aptoParaExamen
      );
      return;
    }

    this.alumnosFiltrados = this.alumnos.filter(
      (alumno) =>
        alumno.aptoParaExamen &&
        !this.alumnosInscritos.some(
          (inscrito) => inscrito.alumnoId === alumno.id
        )
    );
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
}
