import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  Output,
} from '@angular/core';

@Component({
  selector: 'app-paginacion',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './paginacion.component.html',
  styleUrl: './paginacion.component.scss',
})
export class PaginacionComponent implements OnChanges {
  @Input() paginaActual: number = 1;
  @Input() totalPaginas: number = 0;
  @Input() tamanoPagina: number = 10;
  @Input() deshabilitado: boolean = false;
  @Output() pageChange = new EventEmitter<number>();
  mostrarPaginas: number[] = [];
  private esMobile: boolean = typeof window !== 'undefined' && window.innerWidth < 576;

  ngOnChanges(): void {
    this.detectarMobile();
    this.actualizarPaginasMostradas();
  }

  @HostListener('window:resize')
  onResize(): void {
    this.detectarMobile();
    this.actualizarPaginasMostradas();
  }

  private detectarMobile(): void {
    this.esMobile = window.innerWidth < 576;
  }

  cambiarPagina(pageNumber: number): void {
    if (this.deshabilitado) {
      return;
    }
    if (
      pageNumber < 1 ||
      pageNumber > this.totalPaginas ||
      pageNumber === this.paginaActual
    ) {
      return;
    }
    this.paginaActual = pageNumber;
    this.pageChange.emit(this.paginaActual);
    this.actualizarPaginasMostradas();
  }

  private actualizarPaginasMostradas(): void {
    // En móvil mostrar 3 páginas, en desktop mostrar 5
    const paginasAMostrar = this.esMobile ? 3 : 5;
    const mitad = Math.floor(paginasAMostrar / 2);

    let paginaInicio = this.paginaActual - mitad;
    let paginaFin = this.paginaActual + mitad;

    if (paginaInicio < 1) {
      paginaInicio = 1;
      paginaFin = Math.min(this.totalPaginas, paginasAMostrar);
    }

    if (paginaFin > this.totalPaginas) {
      paginaFin = this.totalPaginas;
      paginaInicio = Math.max(1, this.totalPaginas - paginasAMostrar + 1);
    }

    this.mostrarPaginas = Array.from(
      { length: paginaFin - paginaInicio + 1 },
      (_, i) => paginaInicio + i
    );
  }
}
