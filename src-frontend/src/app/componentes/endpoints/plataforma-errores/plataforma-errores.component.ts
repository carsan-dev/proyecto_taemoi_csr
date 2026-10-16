import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { ErrorPlataforma } from '../../../interfaces/error-plataforma';
import { PlataformaService } from '../../../servicios/plataforma/plataforma.service';
import { showErrorToast } from '../../../utils/toast.util';
import { ventanaUltimosDias } from '../../../utils/ventana-fechas.util';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

type Familia = '5xx' | '4xx';

@Component({
  selector: 'app-plataforma-errores',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent, SkeletonCardComponent],
  templateUrl: './plataforma-errores.component.html',
  styleUrl: './plataforma-errores.component.scss',
})
export class PlataformaErroresComponent implements OnInit, OnDestroy {
  private readonly plataforma = inject(PlataformaService);
  private peticion: Subscription | null = null;

  readonly familias: Familia[] = ['5xx', '4xx'];

  escuelas: number[] = [];
  filtroEscuela: number | null = null;
  estados: Record<Familia, boolean> = { '5xx': true, '4xx': true };
  filtroDesde: string = '';
  filtroHasta: string = '';

  cargando = true;
  errorFechas: string | null = null;
  errores: ErrorPlataforma[] = [];

  paginaActual = 1;
  readonly tamanoPagina = 25;
  totalPaginas = 0;
  totalErrores = 0;

  ngOnInit(): void {
    this.restablecerFechas();
    this.cargar();
  }

  ngOnDestroy(): void {
    this.peticion?.unsubscribe();
  }

  alternarEstado(familia: Familia): void {
    const otra: Familia = familia === '5xx' ? '4xx' : '5xx';
    // Siempre queda al menos una familia marcada: sin ninguna la consulta no tendría sentido.
    if (this.estados[familia] && !this.estados[otra]) {
      return;
    }
    this.estados = { ...this.estados, [familia]: !this.estados[familia] };
    this.aplicarFiltros();
  }

  aplicarFiltros(): void {
    this.paginaActual = 1;
    this.cargar();
  }

  restablecer(): void {
    this.filtroEscuela = null;
    this.estados = { '5xx': true, '4xx': true };
    this.restablecerFechas();
    this.aplicarFiltros();
  }

  cambiarPagina(page: number): void {
    if (page < 1 || page > this.totalPaginas || page === this.paginaActual || this.cargando) {
      return;
    }
    this.paginaActual = page;
    this.cargar();
  }

  get estadoParam(): Familia | null {
    if (this.estados['5xx'] && this.estados['4xx']) {
      return null;
    }
    return this.estados['5xx'] ? '5xx' : '4xx';
  }

  claseEstado(estado: number | null | undefined): string {
    if (estado != null && estado >= 500) {
      return 'estado-5xx';
    }
    if (estado != null && estado >= 400) {
      return 'estado-4xx';
    }
    return 'estado-otro';
  }

  trackById(_: number, error: ErrorPlataforma): number {
    return error.id;
  }

  private restablecerFechas(): void {
    const ventana = ventanaUltimosDias();
    this.filtroDesde = ventana.desde;
    this.filtroHasta = ventana.hasta;
  }

  private cargar(): void {
    this.peticion?.unsubscribe();
    this.errorFechas = null;

    if (this.filtroDesde && this.filtroHasta && this.filtroDesde > this.filtroHasta) {
      this.vaciar();
      this.cargando = false;
      this.errorFechas = 'La fecha «Desde» es posterior a «Hasta». Corrige el periodo para ver los errores.';
      return;
    }

    this.cargando = true;
    this.peticion = this.plataforma
      .errores({
        organizacionId: this.filtroEscuela,
        estado: this.estadoParam,
        desde: this.filtroDesde || null,
        hasta: this.filtroHasta || null,
        page: this.paginaActual,
        size: this.tamanoPagina,
      })
      .subscribe({
        next: (respuesta) => {
          this.errores = respuesta?.content ?? [];
          this.totalPaginas = respuesta?.totalPages ?? 0;
          this.totalErrores = respuesta?.totalElements ?? this.errores.length;
          this.recordarEscuelas(this.errores);
          this.cargando = false;
        },
        error: (error: HttpErrorResponse) => {
          this.vaciar();
          this.cargando = false;
          if (error?.status === 400) {
            this.errorFechas = 'Las fechas no son válidas. Revisa «Desde» y «Hasta» e inténtalo de nuevo.';
            return;
          }
          showErrorToast('No se pudieron cargar los errores de plataforma');
        },
      });
  }

  private vaciar(): void {
    this.errores = [];
    this.totalPaginas = 0;
    this.totalErrores = 0;
  }

  /** El selector de escuela se construye con las escuelas vistas en los resultados. */
  private recordarEscuelas(errores: ErrorPlataforma[]): void {
    const conocidas = new Set(this.escuelas);
    errores.forEach((error) => {
      if (error.organizacionId != null) {
        conocidas.add(error.organizacionId);
      }
    });
    if (conocidas.size !== this.escuelas.length) {
      this.escuelas = Array.from(conocidas).sort((a, b) => a - b);
    }
  }
}
