import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';

@Component({
  selector: 'app-listado-alumnos',
  standalone: true,
  imports: [CommonModule, PaginacionComponent],
  templateUrl: './listado-alumnos.component.html',
  styleUrl: './listado-alumnos.component.scss'
})
export class ListadoAlumnosComponent implements OnInit {
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
}
