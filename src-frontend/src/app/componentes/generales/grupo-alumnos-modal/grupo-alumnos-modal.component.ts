import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PaginacionComponent } from '../paginacion/paginacion.component';

@Component({
  selector: 'app-grupo-alumnos-modal',
  standalone: true,
  imports: [CommonModule, RouterLink, PaginacionComponent],
  templateUrl: './grupo-alumnos-modal.component.html',
  styleUrl: './grupo-alumnos-modal.component.scss'
})
export class GrupoAlumnosModalComponent implements OnInit {
  @Input() grupoNombre!: string;
  @Input() alumnos!: any[];
  @Output() cerrar = new EventEmitter<void>();

  modalVisible = false;

  // Pagination properties
  paginaActual = 1;
  tamanoPagina = 8;
  totalPaginas = 0;

  ngOnInit(): void {
    setTimeout(() => {
      this.modalVisible = true;
    }, 0);

    // Calculate total pages
    this.calcularTotalPaginas();
  }

  calcularTotalPaginas(): void {
    if (this.alumnos && this.alumnos.length > 0) {
      this.totalPaginas = Math.ceil(this.alumnos.length / this.tamanoPagina);
    }
  }

  get alumnosPaginados(): any[] {
    if (!this.alumnos || this.alumnos.length === 0) {
      return [];
    }
    const start = (this.paginaActual - 1) * this.tamanoPagina;
    const end = start + this.tamanoPagina;
    return this.alumnos.slice(start, end);
  }

  cambiarPagina(pageNumber: number): void {
    if (pageNumber >= 1 && pageNumber <= this.totalPaginas) {
      this.paginaActual = pageNumber;
    }
  }

  cerrarModal() {
    this.modalVisible = false;
    setTimeout(() => {
      this.cerrar.emit();
    }, 300);
  }
}
