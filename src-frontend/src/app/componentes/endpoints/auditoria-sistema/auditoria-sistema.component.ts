import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { interval, Subscription } from 'rxjs';

import { AuditoriaEvento } from '../../../interfaces/auditoria-evento';
import { AuditoriaEventoDetalle } from '../../../interfaces/auditoria-evento-detalle';
import { PaginatedResponse } from '../../../interfaces/paginated-response';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { showErrorToast } from '../../../utils/toast.util';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

type AccionFiltro = 'TODOS' | 'READ' | 'CREATE' | 'UPDATE' | 'DELETE';
type ResultadoFiltro = 'EXITOS' | 'ERRORES' | 'TODOS';

@Component({
  selector: 'app-auditoria-sistema',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginacionComponent, SkeletonCardComponent],
  templateUrl: './auditoria-sistema.component.html',
  styleUrl: './auditoria-sistema.component.scss',
})
export class AuditoriaSistemaComponent implements OnInit, OnDestroy {
  private readonly autoRefreshIntervalMs = 30000;
  private autoRefreshSubscription: Subscription | null = null;

  readonly acciones: Array<{ value: AccionFiltro; label: string }> = [
    { value: 'TODOS', label: 'Todas las acciones' },
    { value: 'READ', label: 'READ' },
    { value: 'CREATE', label: 'CREATE' },
    { value: 'UPDATE', label: 'UPDATE' },
    { value: 'DELETE', label: 'DELETE' },
  ];
  readonly resultados: Array<{ value: ResultadoFiltro; label: string }> = [
    { value: 'EXITOS', label: 'Operaciones exitosas' },
    { value: 'ERRORES', label: 'Errores (4xx/5xx)' },
    { value: 'TODOS', label: 'Todos' },
  ];

  modulos: string[] = [];

  filtroDesde: string | null = null;
  filtroHasta: string | null = null;
  filtroResultado: ResultadoFiltro = 'EXITOS';
  filtroAccion: AccionFiltro = 'TODOS';
  filtroModulo: string = 'TODOS';
  filtroUsuario: string = '';
  filtroEndpoint: string = '';
  filtroTexto: string = '';

  cargando = true;
  cargandoDetalle = false;

  eventos: AuditoriaEvento[] = [];
  detalleSeleccionado: AuditoriaEventoDetalle | null = null;

  paginaActual = 1;
  tamanoPagina = 25;
  totalPaginas = 0;
  totalEventos = 0;

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.cargarModulos();
    this.cargarEventos();
    this.iniciarAutoRefresh();
  }

  ngOnDestroy(): void {
    this.autoRefreshSubscription?.unsubscribe();
    this.autoRefreshSubscription = null;
  }

  aplicarFiltros(): void {
    this.paginaActual = 1;
    this.cargarEventos();
  }

  cambiarPestanaResultado(resultado: ResultadoFiltro): void {
    if (this.filtroResultado === resultado) {
      return;
    }
    this.filtroResultado = resultado;
    this.paginaActual = 1;
    this.cargarEventos();
  }

  limpiarFiltros(): void {
    this.filtroDesde = null;
    this.filtroHasta = null;
    this.filtroResultado = 'EXITOS';
    this.filtroAccion = 'TODOS';
    this.filtroModulo = 'TODOS';
    this.filtroUsuario = '';
    this.filtroEndpoint = '';
    this.filtroTexto = '';
    this.paginaActual = 1;
    this.detalleSeleccionado = null;
    this.cargarEventos();
  }

  cambiarPagina(page: number): void {
    if (page < 1 || page > this.totalPaginas || this.paginaActual === page || this.cargando) {
      return;
    }
    this.paginaActual = page;
    this.cargarEventos(false);
  }

  verDetalle(evento: AuditoriaEvento): void {
    this.cargandoDetalle = true;
    this.endpointsService.obtenerAuditoriaEventoDetalle(evento.id).subscribe({
      next: (detalle) => {
        this.detalleSeleccionado = detalle;
        this.cargandoDetalle = false;
      },
      error: () => {
        this.cargandoDetalle = false;
        showErrorToast('No se pudo cargar el detalle del evento');
      },
    });
  }

  cerrarDetalle(): void {
    this.detalleSeleccionado = null;
  }

  trackById(_: number, evento: AuditoriaEvento): number {
    return evento.id;
  }

  obtenerClaseAccion(accion: string | null | undefined): string {
    switch ((accion ?? '').toUpperCase()) {
      case 'READ':
        return 'accion-chip read';
      case 'CREATE':
        return 'accion-chip create';
      case 'UPDATE':
        return 'accion-chip update';
      case 'DELETE':
        return 'accion-chip delete';
      default:
        return 'accion-chip';
    }
  }

  formatearJson(valor: string | null | undefined): string {
    if (!valor) {
      return '-';
    }

    try {
      const parsed = JSON.parse(valor);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return valor;
    }
  }

  private cargarModulos(): void {
    this.endpointsService.obtenerAuditoriaModulos().subscribe({
      next: (modulos) => {
        this.modulos = modulos ?? [];
      },
      error: () => {
        this.modulos = [];
      },
    });
  }

  private cargarEventos(restablecerDetalle: boolean = true): void {
    this.cargando = true;

    if (restablecerDetalle) {
      this.detalleSeleccionado = null;
    }

    this.endpointsService
      .obtenerAuditoriaEventos(
        {
          desde: this.filtroDesde,
          hasta: this.filtroHasta,
          resultado: this.mapearResultadoFiltro(this.filtroResultado),
          accion: this.filtroAccion === 'TODOS' ? null : this.filtroAccion,
          modulo: this.filtroModulo === 'TODOS' ? null : this.filtroModulo,
          usuario: this.filtroUsuario || null,
          endpoint: this.filtroEndpoint || null,
          texto: this.filtroTexto || null,
        },
        this.paginaActual,
        this.tamanoPagina
      )
      .subscribe({
        next: (respuesta: PaginatedResponse<AuditoriaEvento>) => {
          this.eventos = respuesta?.content ?? [];
          this.totalPaginas = respuesta?.totalPages ?? 0;
          this.totalEventos = respuesta?.totalElements ?? this.eventos.length;
          this.cargando = false;
        },
        error: () => {
          this.cargando = false;
          this.eventos = [];
          this.totalPaginas = 0;
          this.totalEventos = 0;
          showErrorToast('No se pudieron cargar los eventos de auditoria');
        },
      });
  }

  private iniciarAutoRefresh(): void {
    this.autoRefreshSubscription = interval(this.autoRefreshIntervalMs).subscribe(() => {
      if (this.cargando || this.cargandoDetalle) {
        return;
      }
      this.cargarEventos(false);
    });
  }

  private mapearResultadoFiltro(resultado: ResultadoFiltro): string | null {
    switch (resultado) {
      case 'EXITOS':
        return 'EXITO';
      case 'ERRORES':
        return 'ERROR';
      default:
        return null;
    }
  }
}
