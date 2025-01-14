import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { FormsModule } from '@angular/forms';
import { calcularEdad } from '../../../utilities/calcular-edad';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-listado-alumnos',
  standalone: true,
  imports: [CommonModule, PaginacionComponent, FormsModule, RouterLink],
  templateUrl: './listado-alumnos.component.html',
  styleUrl: './listado-alumnos.component.scss',
})
export class ListadoAlumnosComponent implements OnInit {
  alumnos: any[] = [];
  alumnosSeleccionables: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 9;
  totalPaginas: number = 0;
  nombreFiltro: string = '';
  mostrarInactivos: boolean = false;
  mesAnoSeleccionado: string = '';
  alumnoSeleccionado: number | null = null;
  mesAnoSeleccionadoIndividual: string = '';

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerAlumnos();
    this.cargarTodosLosAlumnos();
  }

  obtenerAlumnos(): void {
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
        error: () => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
  }

  cargarTodosLosAlumnos(): void {
    this.endpointsService
      .obtenerAlumnosSinPaginar(this.mostrarInactivos)
      .subscribe({
        next: (response) => {
          this.alumnosSeleccionables = response;
        },
        error: () => {
          Swal.fire(
            'Error',
            'No se pudo cargar la lista completa de alumnos.',
            'error'
          );
        },
      });
  }

  calcularEdad(fechaNacimiento: string): number {
    return calcularEdad(fechaNacimiento); // Llama a una función utilitaria que calcule la edad
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
    this.cargarTodosLosAlumnos();
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
              timer: 2000,
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
              timer: 2000,
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

  cargarMensualidadesGenerales(): void {
    if (!this.mesAnoSeleccionado) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un mes y año.',
        icon: 'error',
      });
      return;
    }

  
    this.endpointsService
      .cargarMensualidadesGenerales(this.mesAnoSeleccionado)
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Éxito',
            text: 'Las mensualidades se han asignado correctamente.',
            icon: 'success',
            timer: 2000,
          });
          this.obtenerAlumnos();
        },
        error: () => {
          Swal.fire({
            title: 'Error',
            text: 'Ocurrió un error al asignar las mensualidades.',
            icon: 'error',
          });
        },
      });
  }

  cargarMensualidadIndividual(): void {
    if (!this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un mes/año.',
        icon: 'error',
      });
      return;
    }
  
    this.endpointsService
      .cargarMensualidadIndividual(
        this.alumnoSeleccionado,
        this.mesAnoSeleccionadoIndividual
      )
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Éxito',
            text: 'Mensualidad cargada correctamente.',
            icon: 'success',
            timer: 2000,
          });
        },
        error: (error) => {
          if (error.status === 409 && error.error.accion === 'confirmar') {
            Swal.fire({
              title: 'Atención',
              text: error.error.mensaje,
              icon: 'warning',
              showCancelButton: true,
              confirmButtonText: 'Sí, cargar',
              cancelButtonText: 'No, cancelar',
            }).then((result) => {
              if (result.isConfirmed) {
                this.forzarCargarMensualidad();
              }
            });
          } else {
            Swal.fire('Error', 'No se pudo cargar la mensualidad.', 'error');
          }
        },
      });
  }  

  forzarCargarMensualidad(): void {
    if (!this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual) {
      Swal.fire({
        title: 'Error',
        text: 'Por favor selecciona un alumno y un mes/año.',
        icon: 'error',
      });
      return;
    }
  
    this.endpointsService
      .cargarMensualidadIndividual(
        this.alumnoSeleccionado,
        this.mesAnoSeleccionadoIndividual,
        true
      )
      .subscribe({
        next: () => {
          Swal.fire('Éxito', 'Mensualidad cargada correctamente.', 'success');
        },
        error: () => {
          Swal.fire('Error', 'No se pudo cargar la mensualidad.', 'error');
        },
      });
  } 

  private formatearNombreMensualidad(mesAno: string): string {
    const [anio, mes] = mesAno.split('-');
    const meses = [
      'ENERO', 'FEBRERO', 'MARZO', 'ABRIL', 'MAYO', 'JUNIO', 
      'JULIO', 'AGOSTO', 'SEPTIEMBRE', 'OCTUBRE', 'NOVIEMBRE', 'DICIEMBRE'
    ];
    return `${meses[parseInt(mes, 10) - 1]} ${anio}`;
  }
}
