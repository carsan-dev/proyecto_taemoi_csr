import { Component } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { SidebarComponent } from '../../vistas/layout/sidebar/sidebar.component';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-listado-alumnos-completo',
  standalone: true,
  imports: [CommonModule, SidebarComponent],
  templateUrl: './listado-alumnos-completo.component.html',
  styleUrl: './listado-alumnos-completo.component.scss',
})
export class ListadoAlumnosCompletoDTOComponent {
  alumnos: any[] = [];
  paginaActual: number = 1;
  tamanoPagina: number = 1;
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
        .obtenerAlumnosDTO(token, this.paginaActual, this.tamanoPagina)
        .subscribe({
          next: (response) => {
            this.alumnos = response.content;
            this.totalPaginas = response.totalPages;
            this.actualizarPaginasMostradas();
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

  actualizarPaginasMostradas() {
    const paginasAMostrar = 5;
    const mitadDePaginasAMostrar = Math.floor(paginasAMostrar / 2);

    let paginaInicio = this.paginaActual - mitadDePaginasAMostrar;
    let paginaFin = this.paginaActual + mitadDePaginasAMostrar;

    if (paginaInicio < 1) {
      paginaInicio = 1;
      paginaFin = Math.min(this.totalPaginas, paginasAMostrar);
    } else if (paginaFin > this.totalPaginas) {
      paginaFin = this.totalPaginas;
      paginaInicio = Math.max(1, this.totalPaginas - paginasAMostrar + 1);
    }

    if (paginaFin === this.totalPaginas) {
      paginaInicio = Math.max(1, paginaInicio - (paginasAMostrar - (paginaFin - paginaInicio)));
    }

    this.mostrarPaginas = Array.from({ length: paginaFin - paginaInicio + 1 }, (_, i) => paginaInicio + i);
  }
}
