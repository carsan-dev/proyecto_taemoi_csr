import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { interval, Subscription } from 'rxjs';

import { AuditoriaEvento } from '../../../interfaces/auditoria-evento';
import { AuditoriaEventoDetalle } from '../../../interfaces/auditoria-evento-detalle';
import { PaginatedResponse } from '../../../interfaces/paginated-response';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { showErrorToast } from '../../../utils/toast.util';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

type AccionFiltro = 'TODOS' | 'CREATE' | 'UPDATE' | 'DELETE' | 'READ';

export interface AccionVisible {
  etiqueta: string;
  tono: 'alta' | 'cambio' | 'baja' | 'salud' | 'neutro';
}

/** El registro solo muestra lo que se hizo con éxito: el backend lo pide con este valor. */
const RESULTADO_EXITO = 'EXITO';
const MODULO_SALUD = 'salud';

function dosDigitos(valor: number): string {
  return String(valor).padStart(2, '0');
}

function mismoDia(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "hoy 10:42", "ayer 19:03", "12/10 08:15" o "12/10/2025 08:15" si es de otro año. */
export function formatearCuando(fecha: string | Date | null | undefined, ahora: Date = new Date()): string {
  if (!fecha) {
    return '-';
  }
  const valor = fecha instanceof Date ? fecha : new Date(fecha);
  if (Number.isNaN(valor.getTime())) {
    return '-';
  }

  const hora = `${dosDigitos(valor.getHours())}:${dosDigitos(valor.getMinutes())}`;
  if (mismoDia(valor, ahora)) {
    return `hoy ${hora}`;
  }
  const ayer = new Date(ahora);
  ayer.setDate(ahora.getDate() - 1);
  if (mismoDia(valor, ayer)) {
    return `ayer ${hora}`;
  }
  const dia = `${dosDigitos(valor.getDate())}/${dosDigitos(valor.getMonth() + 1)}`;
  return valor.getFullYear() === ahora.getFullYear() ? `${dia} ${hora}` : `${dia}/${valor.getFullYear()} ${hora}`;
}

export function describirAccion(accion: string | null | undefined, modulo: string | null | undefined): AccionVisible {
  switch ((accion ?? '').toUpperCase()) {
    case 'CREATE':
      return { etiqueta: 'Alta', tono: 'alta' };
    case 'UPDATE':
      return { etiqueta: 'Cambio', tono: 'cambio' };
    case 'DELETE':
      return { etiqueta: 'Baja', tono: 'baja' };
    case 'READ':
      return (modulo ?? '').toLowerCase() === MODULO_SALUD
        ? { etiqueta: 'Salud', tono: 'salud' }
        : { etiqueta: 'Consulta', tono: 'neutro' };
    default:
      return { etiqueta: accion || '-', tono: 'neutro' };
  }
}

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

  @ViewChild('detalleTitulo') private detalleTitulo?: ElementRef<HTMLElement>;

  readonly acciones: Array<{ value: AccionFiltro; label: string }> = [
    { value: 'TODOS', label: 'Todas' },
    { value: 'CREATE', label: 'Altas' },
    { value: 'UPDATE', label: 'Cambios' },
    { value: 'DELETE', label: 'Bajas' },
    { value: 'READ', label: 'Consultas de salud' },
  ];

  modulos: string[] = [];

  filtroDesde: string | null = null;
  filtroHasta: string | null = null;
  filtroAccion: AccionFiltro = 'TODOS';
  filtroModulo: string = 'TODOS';
  filtroUsuario: string = '';
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

  actualizarAhora(): void {
    if (this.cargando || this.cargandoDetalle) {
      return;
    }
    this.cargarEventos(false);
  }

  aplicarFiltros(): void {
    this.paginaActual = 1;
    this.cargarEventos();
  }

  limpiarFiltros(): void {
    this.filtroDesde = null;
    this.filtroHasta = null;
    this.filtroAccion = 'TODOS';
    this.filtroModulo = 'TODOS';
    this.filtroUsuario = '';
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
        // El detalle se pinta bajo la tabla: llevar el foco allí para teclado y lector de pantalla.
        setTimeout(() => this.detalleTitulo?.nativeElement.focus());
      },
      error: () => {
        this.cargandoDetalle = false;
        showErrorToast('No se pudo cargar el detalle de la acción');
      },
    });
  }

  cerrarDetalle(): void {
    this.detalleSeleccionado = null;
  }

  trackById(_: number, evento: AuditoriaEvento): number {
    return evento.id;
  }

  cuando(fecha: string | Date | null | undefined): string {
    return formatearCuando(fecha);
  }

  accionVisible(evento: Pick<AuditoriaEvento, 'accion' | 'modulo'>): AccionVisible {
    return describirAccion(evento.accion, evento.modulo);
  }

  persona(evento: Pick<AuditoriaEvento, 'usuarioNombre' | 'usuarioEmail'>): string {
    return evento.usuarioNombre || evento.usuarioEmail || '-';
  }

  tieneDatosTecnicos(detalle: AuditoriaEventoDetalle | null): boolean {
    return !!detalle && (detalle.payloadJson != null || detalle.queryParamsJson != null);
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
          resultado: RESULTADO_EXITO,
          accion: this.filtroAccion === 'TODOS' ? null : this.filtroAccion,
          modulo: this.moduloParaConsulta(),
          usuario: this.filtroUsuario || null,
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
          showErrorToast('No se pudo cargar el registro de acciones');
        },
      });
  }

  /** "Consultas de salud" son lecturas del módulo salud: sin fijar el módulo, la vista completa traería todas las lecturas. */
  private moduloParaConsulta(): string | null {
    if (this.filtroAccion === 'READ') {
      return MODULO_SALUD;
    }
    return this.filtroModulo === 'TODOS' ? null : this.filtroModulo;
  }

  private iniciarAutoRefresh(): void {
    this.autoRefreshSubscription = interval(this.autoRefreshIntervalMs).subscribe(() => {
      if (this.cargando || this.cargandoDetalle) {
        return;
      }
      this.cargarEventos(false);
    });
  }
}
