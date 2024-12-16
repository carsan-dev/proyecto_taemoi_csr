import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { formatDate } from '../../../utilities/formatear-fecha';

@Component({
  selector: 'app-listado-convocatorias',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
  paginaActual = 1;
  tamanoPagina = 10;
  alumnosCargadosCompletamente = false;

  alumnoSeleccionado: number | null = null;

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerConvocatorias();
    this.cargarAlumnosPaginados();
  }

  obtenerConvocatorias(): void {
    this.endpointsService
      .obtenerConvocatorias(this.deporteSeleccionado)
      .subscribe({
        next: (data) => {
          this.convocatorias = data;
        },
        error: () => {
          Swal.fire('Error', 'No se pudo obtener las convocatorias.', 'error');
        },
      });
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
        Swal.fire('Error', 'No se pudo crear la convocatoria.', 'error');
      },
    });
  }

  seleccionarConvocatoria(convocatoria: any): void {
    this.convocatoriaSeleccionada = convocatoria;

    this.endpointsService
      .obtenerAlumnosDeConvocatoria(convocatoria.id)
      .subscribe({
        next: (data) => {
          this.alumnosInscritos = data.map(alumno => ({
            ...alumno,
            id: alumno.id
          }));
          this.filtrarAlumnos();
        },
        error: () => {
          Swal.fire(
            'Error',
            'No se pudieron obtener los alumnos de la convocatoria.',
            'error'
          );
        },
      });
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
            Swal.fire(
              '¡Eliminado!',
              'La convocatoria ha sido eliminada.',
              'success'
            );
            this.convocatorias = this.convocatorias.filter(
              (c) => c.id !== convocatoria.id
            );
            if (this.convocatoriaSeleccionada?.id === convocatoria.id) {
              this.convocatoriaSeleccionada = null;
              this.alumnosInscritos = [];
            }
          },
          error: () => {
            Swal.fire('Error', 'No se pudo eliminar la convocatoria.', 'error');
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
          Swal.fire(
            'Error',
            'No se pudo cargar los alumnos paginados.',
            'error'
          );
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
    const hayNoPagados = this.alumnosInscritos.some(alumno => !alumno.pagado);
  
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
    this.endpointsService.actualizarGradosDeConvocatoria(convocatoria.id).subscribe({
      next: () => {
        Swal.fire('Éxito', 'Grados actualizados correctamente.', 'success');
        this.seleccionarConvocatoria(convocatoria);
      },
      error: () => {
        Swal.fire('Error', 'No se pudieron actualizar los grados.', 'error');
      },
    });
  }

  actualizarAlumnoConvocatoria(alumno: any): void {
    const alumnoConvocatoriaDTO = {
      cuantiaExamen: alumno.cuantiaExamen,
      pagado: alumno.pagado,
    };
  
    this.endpointsService.actualizarAlumnoConvocatoria(alumno.id, alumnoConvocatoriaDTO).subscribe({
      next: () => {
        Swal.fire('Éxito', 'Datos del alumno actualizados correctamente.', 'success');
      },
      error: () => {
        Swal.fire('Error', 'No se pudo actualizar el alumno.', 'error');
      },
    });
  }  

  agregarAlumnoAConvocatoria(): void {
    if (!this.alumnoSeleccionado || !this.convocatoriaSeleccionada) return;

    this.endpointsService
      .agregarAlumnoAConvocatoria(
        this.alumnoSeleccionado,
        this.convocatoriaSeleccionada.id
      )
      .subscribe({
        next: () => {
          Swal.fire(
            'Alumno agregado',
            'El alumno ha sido agregado a la convocatoria.',
            'success'
          );
          this.seleccionarConvocatoria(this.convocatoriaSeleccionada);
        },
        error: () => {
          Swal.fire('Error', 'No se pudo agregar al alumno.', 'error');
        },
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
              Swal.fire(
                '¡Eliminado!',
                'El alumno ha sido eliminado de la convocatoria.',
                'success'
              );
              this.seleccionarConvocatoria(this.convocatoriaSeleccionada);
            },
            error: () => {
              Swal.fire('Error', 'No se pudo eliminar al alumno.', 'error');
            },
          });
      }
    });
  }
}
