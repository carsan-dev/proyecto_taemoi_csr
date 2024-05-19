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
  mostrarPaginas: number[] = [];

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
        .obtenerAlumnos(token, this.paginaActual, this.tamanoPagina)
        .subscribe({
          next: (response) => {
            this.alumnos = response.content;
            this.totalPaginas = response.totalPages;
          },
          error: (error) => {
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

  eliminarAlumno(id: number) {
    const token = localStorage.getItem('token');

    if (token) {
      this.endpointsService.eliminarAlumnos(id, token).subscribe({
        next: () => {
          Swal.fire({
            title: 'Alumno eliminado',
            text: 'El alumno ha sido eliminado correctamente.',
            icon: 'success',
          });
          this.obtenerAlumnos();
        },
        error: (error) => {
          Swal.fire({
            title: 'Error al eliminar alumno',
            text: 'Ha ocurrido un error al intentar eliminar el alumno.',
            icon: 'error',
          });
        },
      });
    }
  }

  eliminarAlumnosSeleccionados() {
    const token = localStorage.getItem('token');
    const alumnosSeleccionados = this.alumnos.filter(
      (alumno) => alumno.selected
    );

    if (token && alumnosSeleccionados.length > 0) {
      alumnosSeleccionados.forEach((alumno) => {
        this.endpointsService.eliminarAlumnos(alumno.id, token).subscribe({
          next: () => {
            this.obtenerAlumnos();
          },
          error: (error) => {
            Swal.fire({
              title: 'Error al eliminar alumno',
              text: `Ha ocurrido un error al intentar eliminar al alumno ${alumno.nombre} ${alumno.apellidos}.`,
              icon: 'error',
            });
          },
          complete: () => {
            this.obtenerAlumnos();
          },
        });
      });

      Swal.fire({
        title: 'Alumnos eliminados',
        text: 'Los alumnos seleccionados han sido eliminados correctamente.',
        icon: 'success',
      });
    } else {
      Swal.fire({
        title: 'No hay alumnos seleccionados',
        text: 'Por favor, seleccione al menos un alumno para eliminar.',
        icon: 'info',
      });
    }
  }
}
