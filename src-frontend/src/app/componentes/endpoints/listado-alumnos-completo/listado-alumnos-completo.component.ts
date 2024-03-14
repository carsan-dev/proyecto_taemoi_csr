import { Component } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import { SidebarService } from '../../../servicios/generales/sidebar.service';
import { SidebarComponent } from '../../vistas/layout/sidebar/sidebar.component';

@Component({
  selector: 'app-listado-alumnos-completo',
  standalone: true,
  imports: [CommonModule, SidebarComponent],
  templateUrl: './listado-alumnos-completo.component.html',
  styleUrl: './listado-alumnos-completo.component.scss'
})
export class ListadoAlumnosCompletoDTOComponent {
  alumnos: any[] = [];
  currentPage: number = 1;
  pageSize: number = 1;
  totalPages: number = 0;
  totalPagesArray: number[] = [];

  constructor(private endpointsService: EndpointsService, private sidebarService: SidebarService) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
    this.obtenerAlumnos();
    }
  }

  obtenerAlumnos() {
    const token = localStorage.getItem('token');

    if (token) {
      this.endpointsService.enviarToken(token, this.currentPage, this.pageSize).subscribe({
        next: (response) => {
          this.alumnos = response.content;
          this.totalPages = response.totalPages;
          this.calcularTotalPagesArray();
        },
        error: (error) => {
          console.log(error);
        },
      });
    }
  }

  cambiarPagina(pageNumber: number): void {
    this.currentPage = pageNumber;
    this.obtenerAlumnos();
  }

  calcularTotalPagesArray() {
    this.totalPagesArray = Array(this.totalPages).fill(0).map((x, i) => i + 1);
  }

  alternarVisibilidadSidebar(): void {
    this.sidebarService.alternarSidebar();
  }
}
