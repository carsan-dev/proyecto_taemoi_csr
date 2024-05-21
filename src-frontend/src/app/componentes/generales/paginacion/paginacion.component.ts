import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';

@Component({
  selector: 'app-paginacion',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './paginacion.component.html',
  styleUrl: './paginacion.component.scss'
})
export class PaginacionComponent implements OnChanges {
  @Input() paginaActual: number = 1;
  @Input() totalPaginas: number = 0;
  @Input() tamanoPagina: number = 10;
  @Output() pageChange = new EventEmitter<number>();

  mostrarPaginas: number[] = [];

  ngOnChanges(): void {
    this.actualizarPaginasMostradas();
  }

  cambiarPagina(pageNumber: number): void {
    if (pageNumber < 1 || pageNumber > this.totalPaginas || pageNumber === this.paginaActual) {
      return;
    }
    this.paginaActual = pageNumber;
    this.pageChange.emit(this.paginaActual);
    this.actualizarPaginasMostradas();
  }

  private actualizarPaginasMostradas(): void {
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
