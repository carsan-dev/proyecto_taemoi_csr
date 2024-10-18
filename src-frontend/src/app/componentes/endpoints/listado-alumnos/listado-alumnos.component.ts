import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-listado-alumnos',
  standalone: true,
  imports: [CommonModule, PaginacionComponent, FormsModule],
  templateUrl: './listado-alumnos.component.html',
  styleUrl: './listado-alumnos.component.scss',
})
export class ListadoAlumnosComponent implements OnInit {
  alumnos: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 9;
  totalPaginas: number = 0;
  nombreFiltro: string = '';
  mostrarInactivos: boolean = false;

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerAlumnos();
  }

  obtenerAlumnos() {
    this.endpointsService
      .obtenerAlumnos(
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
        error: (error) => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
  }

  calcularEdad(fechaNacimiento: string): number {
    const hoy = new Date();
    const nacimiento = new Date(fechaNacimiento);
    let edad = hoy.getFullYear() - nacimiento.getFullYear();
    const mes = hoy.getMonth() - nacimiento.getMonth();
    if (mes < 0 || (mes === 0 && hoy.getDate() < nacimiento.getDate())) {
      edad--;
    }
    return edad;
  }

  cambiarPagina(pageNumber: number): void {
    this.paginaActual = pageNumber;
    this.obtenerAlumnos();
  }

  filtrarPorNombre(): void {
    this.paginaActual = 1;
    this.obtenerAlumnos();
  }

  alternarInactivos(): void {
    this.mostrarInactivos = !this.mostrarInactivos;
    this.obtenerAlumnos();
  }

  darDeAlta(alumnoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'El alumno será dado de alta.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, dar de alta',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.darDeAltaAlumno(alumnoId).subscribe({
          next: () => {
            Swal.fire({
              title: 'Alumno dado de alta',
              text: 'El alumno ha sido dado de alta correctamente.',
              icon: 'success',
            });
            this.obtenerAlumnos(); // Vuelve a cargar la lista de alumnos
          },
          error: () => {
            Swal.fire({
              title: 'Error al dar de alta',
              text: 'Ha ocurrido un error al intentar dar de alta al alumno.',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  darDeBaja(alumnoId: number) {
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
        this.endpointsService.darDeBajaAlumno(alumnoId).subscribe({
          next: () => {
            Swal.fire({
              title: 'Alumno dado de baja',
              text: 'El alumno ha sido dado de baja correctamente.',
              icon: 'success',
            });
            this.obtenerAlumnos(); // Vuelve a cargar la lista de alumnos
          },
          error: () => {
            Swal.fire({
              title: 'Error al dar de baja',
              text: 'Ha ocurrido un error al intentar dar de baja al alumno.',
              icon: 'error',
            });
          },
        });
      }
    });
  }
}
