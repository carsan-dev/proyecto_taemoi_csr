import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';

@Component({
  selector: 'app-eliminar-alumno',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent],
  templateUrl: './eliminar-alumno.component.html',
  styleUrl: './eliminar-alumno.component.scss',
})
export class EliminarAlumnoComponent implements OnInit {
  alumnos: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 10;
  totalPaginas: number = 0;
  nombreFiltro: string = '';
  mostrarInactivos: boolean = false;

  constructor(private endpointsService: EndpointsService) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.obtenerAlumnos();
    }
  }

  obtenerAlumnos() {
    const token = localStorage.getItem('token');

    if (token) {
      this.endpointsService
        .obtenerAlumnos(
          token,
          this.paginaActual,
          this.tamanoPagina,
          this.nombreFiltro,
          this.mostrarInactivos
        )
        .subscribe({
          next: (response) => {
            this.alumnos = response.content;
            this.totalPaginas = response.totalPages;
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
  }

  cambiarPagina(pageNumber: number): void {
    this.paginaActual = pageNumber;
    this.obtenerAlumnos();
  }

  darDeBajaAlumno(id: number) {
    const token = localStorage.getItem('token');

    if (token) {
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
          this.endpointsService.darDeBajaAlumno(id, token).subscribe({
            next: () => {
              Swal.fire({
                title: 'Alumno dado de baja',
                text: 'El alumno ha sido dado de baja correctamente.',
                icon: 'success',
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
  }

  eliminarAlumno(id: number) {
    const token = localStorage.getItem('token');

    if (token) {
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
          this.endpointsService.eliminarAlumnos(id, token).subscribe({
            next: () => {
              Swal.fire({
                title: 'Alumno eliminado',
                text: 'El alumno ha sido eliminado correctamente.',
                icon: 'success',
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
  }

  darDeBajaAlumnosSeleccionados() {
    const token = localStorage.getItem('token');
    const alumnosSeleccionados = this.alumnos.filter(
      (alumno) => alumno.selected
    );

    if (token && alumnosSeleccionados.length > 0) {
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
          this.darDeBajaAlumnoSecuencial(alumnosSeleccionados, token);
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
    const token = localStorage.getItem('token');
    const alumnosSeleccionados = this.alumnos.filter(
      (alumno) => alumno.selected
    );

    if (token && alumnosSeleccionados.length > 0) {
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
          this.eliminarAlumnoSecuencial(alumnosSeleccionados, token);
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

  darDeBajaAlumnoSecuencial(alumnosSeleccionados: any[], token: string) {
    if (alumnosSeleccionados.length === 0) {
      Swal.fire({
        title: 'Alumnos dados de baja',
        text: 'Los alumnos seleccionados han sido dados de baja correctamente.',
        icon: 'success',
      });
      this.obtenerAlumnos();
      return;
    }

    const alumno = alumnosSeleccionados.pop();

    this.endpointsService.darDeBajaAlumno(alumno.id, token).subscribe({
      next: () => {
        this.darDeBajaAlumnoSecuencial(alumnosSeleccionados, token);
      },
      error: () => {
        Swal.fire({
          title: 'Error al dar de baja al alumno',
          text: `Ha ocurrido un error al intentar dar de baja al alumno ${alumno.nombre} ${alumno.apellidos}.`,
          icon: 'error',
        });
        this.darDeBajaAlumnoSecuencial(alumnosSeleccionados, token);
      },
    });
  }

  eliminarAlumnoSecuencial(alumnosSeleccionados: any[], token: string) {
    if (alumnosSeleccionados.length === 0) {
      Swal.fire({
        title: 'Alumnos eliminados',
        text: 'Los alumnos seleccionados han sido eliminados correctamente.',
        icon: 'success',
      });
      this.obtenerAlumnos();
      return;
    }

    const alumno = alumnosSeleccionados.pop();

    this.endpointsService.eliminarAlumnos(alumno.id, token).subscribe({
      next: () => {
        this.eliminarAlumnoSecuencial(alumnosSeleccionados, token);
      },
      error: () => {
        Swal.fire({
          title: 'Error al eliminar alumno',
          text: `Ha ocurrido un error al intentar eliminar al alumno ${alumno.nombre} ${alumno.apellidos}.`,
          icon: 'error',
        });
        this.eliminarAlumnoSecuencial(alumnosSeleccionados, token);
      },
    });
  }

  filtrarPorNombre(): void {
    this.paginaActual = 1;
    this.obtenerAlumnos();
  }

  alternarInactivos(): void {
    this.mostrarInactivos = !this.mostrarInactivos;
    this.obtenerAlumnos();
  }
}
