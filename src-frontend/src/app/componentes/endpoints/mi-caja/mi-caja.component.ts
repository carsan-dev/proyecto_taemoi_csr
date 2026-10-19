import { CommonModule } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { A11yModule } from '@angular/cdk/a11y';
import { Observable, Subject, takeUntil } from 'rxjs';
import { finalize } from 'rxjs/operators';

import {
  AlumnoDeMisGrupos,
  CargoPendienteProfesor,
  CobroCaja,
  MisCobrosPendientes,
  MotivoDeshacerCobro,
  ProductoVendible,
} from '../../../interfaces/caja';
import { AuthenticationService, esSoloProfesor } from '../../../servicios/authentication/authentication.service';
import { CajaProfesorService } from '../../../servicios/endpoints/caja-profesor.service';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { showErrorToast } from '../../../utils/toast.util';
import { diasDesde, formatearEuros, textoRelativo } from '../caja-sede/caja-calculos';

export type PestanaMiCaja = 'cobrar' | 'vender' | 'encima';

/** Cuánto dura el aviso «Cobrado … · Deshacer» antes de cerrarse solo. */
export const DURACION_AVISO_MS = 6000;

export interface GrupoAlumno {
  alumnoId: number;
  nombre: string;
  cargos: CargoPendienteProfesor[];
}

/** Hoja de confirmación de un cobro: un cargo pendiente o la venta de un producto a un alumno. */
interface HojaCobro {
  titulo: string;
  /** Texto del botón principal y del aviso posterior: «Cobrar» → «Cobrado», «Vender» → «Vendido». */
  verbo: 'Cobrar' | 'Vender';
  importe: number;
  alumno: string;
  concepto: string;
  /** Con qué motivo se deshace desde el aviso: un cargo sigue pendiente; una venta se cancela. */
  motivoDeshacer: MotivoDeshacerCobro;
  ejecutar: () => Observable<CobroCaja>;
  enviando: boolean;
  error: string | null;
}

interface HojaDeshacer {
  cobro: CobroCaja;
  motivo: MotivoDeshacerCobro;
  enviando: boolean;
  error: string | null;
}

interface AvisoCobro {
  texto: string;
  cobroId: number;
  motivo: MotivoDeshacerCobro;
  deshaciendo: boolean;
}

/** Texto sin tildes y en minúsculas, para buscar «lucia» y encontrar «Lucía». */
export function normalizarBusqueda(texto: string | null | undefined): string {
  return (texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

export function nombreCompleto(nombre: string | null | undefined, apellidos: string | null | undefined): string {
  return [nombre, apellidos].map((p) => (p ?? '').trim()).filter(Boolean).join(' ') || 'Alumno sin nombre';
}

/** Cargos agrupados por alumno, alumnos por nombre y, dentro, del más antiguo al más reciente. */
export function agruparPorAlumno(cargos: CargoPendienteProfesor[], busqueda: string = ''): GrupoAlumno[] {
  const filtro = normalizarBusqueda(busqueda);
  const grupos = new Map<number, GrupoAlumno>();
  for (const cargo of cargos) {
    const nombre = nombreCompleto(cargo.alumnoNombre, cargo.alumnoApellidos);
    if (filtro && !normalizarBusqueda(nombre).includes(filtro)) {
      continue;
    }
    let grupo = grupos.get(cargo.alumnoId);
    if (!grupo) {
      grupo = { alumnoId: cargo.alumnoId, nombre, cargos: [] };
      grupos.set(cargo.alumnoId, grupo);
    }
    grupo.cargos.push(cargo);
  }
  const porFecha = (c: CargoPendienteProfesor) => (c.fechaAsignacion ? new Date(c.fechaAsignacion).getTime() : 0);
  return Array.from(grupos.values())
    .map((g) => ({ ...g, cargos: [...g.cargos].sort((a, b) => porFecha(a) - porFecha(b)) }))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
}

/** «01/10» de una fecha ISO (o «—»). */
export function diaMes(fecha: string | null | undefined): string {
  if (!fecha) {
    return '—';
  }
  const d = new Date(fecha);
  if (Number.isNaN(d.getTime())) {
    return '—';
  }
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * «Mi caja» del profesor (pieza 6b-3b, decisión 181): cobrar en efectivo lo pendiente de sus alumnos, vender productos
 * de precio fijo y ver lo que lleva encima hasta entregarlo en la sede. Pensada para el móvil, en la puerta del tatami.
 * Solo usa `/api/caja/profesor/**`: una sesión solo profesor no puede pedir nada más.
 */
@Component({
  selector: 'app-mi-caja',
  standalone: true,
  imports: [CommonModule, FormsModule, A11yModule],
  templateUrl: './mi-caja.component.html',
  styleUrl: './mi-caja.component.scss',
})
export class MiCajaComponent implements OnInit, OnDestroy {
  private readonly caja = inject(CajaProfesorService);
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  readonly euros = formatearEuros;
  readonly diaMes = diaMes;

  /** Quien solo es profesor no tiene cabecera global: cierra sesión desde aquí. */
  soloProfesor = false;
  pestana: PestanaMiCaja = 'cobrar';

  // Por cobrar
  cargos: CargoPendienteProfesor[] = [];
  cargandoCargos = false;
  errorCargos: string | null = null;
  busquedaCargos = '';
  grupos: GrupoAlumno[] = [];

  // Encima
  encima: MisCobrosPendientes | null = null;
  cargandoEncima = false;
  errorEncima: string | null = null;

  // Vender
  alumnos: AlumnoDeMisGrupos[] = [];
  productos: ProductoVendible[] = [];
  cargandoVender = false;
  errorVender: string | null = null;
  private venderCargado = false;
  busquedaAlumno = '';
  alumnoVenta: AlumnoDeMisGrupos | null = null;

  hoja: HojaCobro | null = null;
  hojaDeshacer: HojaDeshacer | null = null;
  aviso: AvisoCobro | null = null;
  private temporizadorAviso: ReturnType<typeof setTimeout> | null = null;
  private focoAntesDeLaHoja: HTMLElement | null = null;

  ngOnInit(): void {
    this.soloProfesor = esSoloProfesor(this.auth.getRolesActuales());
    this.cargarCargos();
    this.cargarEncima();
  }

  ngOnDestroy(): void {
    this.limpiarTemporizador();
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ------------------------------------------------------------------ carga

  cargarCargos(): void {
    this.cargandoCargos = true;
    this.errorCargos = null;
    this.caja
      .cargosPendientes()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.cargandoCargos = false))
      )
      .subscribe({
        next: (cargos) => {
          this.cargos = cargos;
          this.filtrarCargos();
        },
        error: (e) => (this.errorCargos = mensajeDeError(e) ?? 'No se pudieron cargar los cobros pendientes.'),
      });
  }

  cargarEncima(): void {
    this.cargandoEncima = true;
    this.errorEncima = null;
    this.caja
      .cobrosPendientesEntrega()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.cargandoEncima = false))
      )
      .subscribe({
        next: (encima) => (this.encima = encima),
        error: (e) => (this.errorEncima = mensajeDeError(e) ?? 'No se pudo cargar lo que llevas encima.'),
      });
  }

  cargarVender(): void {
    this.cargandoVender = true;
    this.errorVender = null;
    let pendientes = 2;
    const terminar = () => {
      pendientes -= 1;
      if (pendientes === 0) {
        this.cargandoVender = false;
      }
    };
    const fallo = (e: unknown) => {
      this.venderCargado = false;
      this.errorVender = mensajeDeError(e) ?? 'No se pudo cargar el catálogo ni tus alumnos.';
    };
    this.venderCargado = true;
    this.caja
      .alumnos()
      .pipe(takeUntil(this.destroy$), finalize(terminar))
      .subscribe({
        next: (alumnos) =>
          (this.alumnos = [...alumnos].sort((a, b) =>
            nombreCompleto(a.nombre, a.apellidos).localeCompare(nombreCompleto(b.nombre, b.apellidos), 'es')
          )),
        error: fallo,
      });
    this.caja
      .productos()
      .pipe(takeUntil(this.destroy$), finalize(terminar))
      .subscribe({ next: (productos) => (this.productos = productos), error: fallo });
  }

  /** Tras cada acción: lo pendiente y lo que llevas encima cambian. */
  private recargarContadores(): void {
    this.cargarCargos();
    this.cargarEncima();
  }

  // ------------------------------------------------------------------ pestañas

  readonly pestanas: PestanaMiCaja[] = ['cobrar', 'vender', 'encima'];

  elegirPestana(pestana: PestanaMiCaja): void {
    this.pestana = pestana;
    if (pestana === 'vender' && !this.venderCargado) {
      this.cargarVender();
    }
  }

  /** Flechas izquierda/derecha entre pestañas (patrón ARIA de pestañas). */
  moverPestana(evento: KeyboardEvent): void {
    const paso = evento.key === 'ArrowRight' ? 1 : evento.key === 'ArrowLeft' ? -1 : 0;
    if (!paso) {
      return;
    }
    evento.preventDefault();
    const i = this.pestanas.indexOf(this.pestana);
    const siguiente = this.pestanas[(i + paso + this.pestanas.length) % this.pestanas.length];
    this.elegirPestana(siguiente);
    setTimeout(() => document.getElementById(`pestana-${siguiente}`)?.focus());
  }

  get numeroPorCobrar(): number {
    return this.cargos.length;
  }

  get numeroEncima(): number {
    return this.encima?.numeroCobros ?? 0;
  }

  filtrarCargos(): void {
    this.grupos = agruparPorAlumno(this.cargos, this.busquedaCargos);
  }

  get alumnosFiltrados(): AlumnoDeMisGrupos[] {
    const filtro = normalizarBusqueda(this.busquedaAlumno);
    if (!filtro) {
      return this.alumnos;
    }
    return this.alumnos.filter((a) => normalizarBusqueda(nombreCompleto(a.nombre, a.apellidos)).includes(filtro));
  }

  nombreAlumno(a: { nombre?: string | null; apellidos?: string | null } | null): string {
    return a ? nombreCompleto(a.nombre, a.apellidos) : '';
  }

  nombreCobro(c: CobroCaja): string {
    return nombreCompleto(c.alumnoNombre, c.alumnoApellidos);
  }

  cuandoCobro(c: CobroCaja): string {
    return textoRelativo(diasDesde(c.fechaCobro));
  }

  elegirAlumno(alumno: AlumnoDeMisGrupos): void {
    this.alumnoVenta = alumno;
    this.busquedaAlumno = '';
  }

  cambiarAlumno(): void {
    this.alumnoVenta = null;
    setTimeout(() => document.getElementById('buscarAlumnoVenta')?.focus());
  }

  // ------------------------------------------------------------------ cobrar y vender

  abrirCobro(cargo: CargoPendienteProfesor, evento?: Event): void {
    this.recordarFoco(evento);
    this.hoja = {
      titulo: 'Cobrar en efectivo',
      verbo: 'Cobrar',
      importe: cargo.importe,
      alumno: nombreCompleto(cargo.alumnoNombre, cargo.alumnoApellidos),
      concepto: cargo.concepto,
      motivoDeshacer: 'AUN_NO_PAGADO',
      ejecutar: () => this.caja.cobrarCargo(cargo.cargoId),
      enviando: false,
      error: null,
    };
  }

  abrirVenta(producto: ProductoVendible, evento?: Event): void {
    const alumno = this.alumnoVenta;
    if (!alumno) {
      return;
    }
    this.recordarFoco(evento);
    this.hoja = {
      titulo: 'Vender y cobrar en efectivo',
      verbo: 'Vender',
      importe: producto.precio,
      alumno: nombreCompleto(alumno.nombre, alumno.apellidos),
      concepto: producto.concepto,
      motivoDeshacer: 'NO_HUBO_VENTA',
      ejecutar: () => this.caja.venderProducto(alumno.alumnoId, producto.id),
      enviando: false,
      error: null,
    };
  }

  confirmarHoja(): void {
    const hoja = this.hoja;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.enviando = true;
    hoja.error = null;
    hoja
      .ejecutar()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (cobro) => {
          this.hoja = null;
          this.restaurarFoco();
          const hecho = hoja.verbo === 'Vender' ? 'Vendido' : 'Cobrado';
          this.mostrarAviso(`${hecho} ${formatearEuros(cobro.importe ?? hoja.importe)}`, cobro.cobroId, hoja.motivoDeshacer);
          this.recargarContadores();
        },
        error: (e) => {
          hoja.enviando = false;
          hoja.error = mensajeDeError(e) ?? 'No se pudo cobrar. Inténtalo de nuevo.';
          // Si el cargo ya no está (cobrado por otro, anulado) o el servidor sí llegó a registrar el cobro, la lista,
          // «Encima» y «Llevas encima» se ponen al día.
          this.recargarContadores();
        },
      });
  }

  cerrarHoja(): void {
    if (this.hoja?.enviando) {
      return;
    }
    this.hoja = null;
    this.restaurarFoco();
  }

  // ------------------------------------------------------------------ aviso con deshacer

  private mostrarAviso(texto: string, cobroId: number, motivo: MotivoDeshacerCobro): void {
    this.limpiarTemporizador();
    this.aviso = { texto, cobroId, motivo, deshaciendo: false };
    this.temporizadorAviso = setTimeout(() => this.cerrarAviso(), DURACION_AVISO_MS);
  }

  cerrarAviso(): void {
    if (this.aviso?.deshaciendo) {
      return;
    }
    this.limpiarTemporizador();
    this.aviso = null;
  }

  deshacerDesdeAviso(): void {
    const aviso = this.aviso;
    if (!aviso || aviso.deshaciendo) {
      return;
    }
    aviso.deshaciendo = true;
    this.limpiarTemporizador();
    this.caja
      .deshacerCobro(aviso.cobroId, aviso.motivo)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.quitarAviso(aviso);
          this.recargarContadores();
        },
        error: (e) => {
          this.quitarAviso(aviso);
          showErrorToast(mensajeDeError(e) ?? 'No se pudo deshacer el cobro.');
          this.recargarContadores();
        },
      });
  }

  /** Quita el aviso deshecho solo si sigue en pantalla: si entretanto hubo otro cobro, su aviso se queda. */
  private quitarAviso(aviso: AvisoCobro): void {
    if (this.aviso === aviso) {
      this.aviso = null;
    }
  }

  private limpiarTemporizador(): void {
    if (this.temporizadorAviso !== null) {
      clearTimeout(this.temporizadorAviso);
      this.temporizadorAviso = null;
    }
  }

  // ------------------------------------------------------------------ deshacer desde «Encima»

  abrirDeshacer(cobro: CobroCaja, evento?: Event): void {
    this.recordarFoco(evento);
    this.hojaDeshacer = { cobro, motivo: 'AUN_NO_PAGADO', enviando: false, error: null };
  }

  confirmarDeshacer(): void {
    const hoja = this.hojaDeshacer;
    if (!hoja || hoja.enviando) {
      return;
    }
    // «No hubo venta» solo vale para una venta del catálogo.
    const motivo: MotivoDeshacerCobro = hoja.cobro.venta ? hoja.motivo : 'AUN_NO_PAGADO';
    hoja.enviando = true;
    hoja.error = null;
    this.caja
      .deshacerCobro(hoja.cobro.cobroId, motivo)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.hojaDeshacer = null;
          this.restaurarFoco();
          this.recargarContadores();
        },
        error: (e) => {
          hoja.enviando = false;
          hoja.error = mensajeDeError(e) ?? 'No se pudo deshacer el cobro.';
          this.cargarEncima();
        },
      });
  }

  cerrarDeshacer(): void {
    if (this.hojaDeshacer?.enviando) {
      return;
    }
    this.hojaDeshacer = null;
    this.restaurarFoco();
  }

  // ------------------------------------------------------------------ sesión, teclado y foco

  cerrarSesion(): void {
    this.auth.forzarCierreSesion().then(() => this.router.navigate(['/login']));
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.hoja) {
      this.cerrarHoja();
    } else if (this.hojaDeshacer) {
      this.cerrarDeshacer();
    }
  }

  private recordarFoco(evento?: Event): void {
    const destino = evento?.currentTarget ?? (typeof document !== 'undefined' ? document.activeElement : null);
    this.focoAntesDeLaHoja = destino instanceof HTMLElement ? destino : null;
  }

  private restaurarFoco(): void {
    const foco = this.focoAntesDeLaHoja;
    this.focoAntesDeLaHoja = null;
    if (foco?.isConnected) {
      setTimeout(() => foco.focus());
    }
  }
}
