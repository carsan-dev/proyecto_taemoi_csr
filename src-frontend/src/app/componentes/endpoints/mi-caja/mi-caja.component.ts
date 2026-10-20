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
  CobroEntregadoReciente,
  MisCobrosPendientes,
  MotivoDeshacerCobro,
  PedirEncargo,
  ProductoVendible,
  SedeMiCaja,
} from '../../../interfaces/caja';
import { EstadoCobroCargo, Solicitud } from '../../../interfaces/solicitud';
import { AuthenticationService, esSoloProfesor } from '../../../servicios/authentication/authentication.service';
import { CajaProfesorService } from '../../../servicios/endpoints/caja-profesor.service';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { showErrorToast } from '../../../utils/toast.util';
import { diasDesde, formatearEuros, textoRelativo } from '../caja-sede/caja-calculos';
import {
  ETIQUETA_COBRO,
  MAX_MOTIVO,
  MAX_NOTA_ENCARGO,
  PASOS_ENCARGO,
  devolucionEnLaSede,
  encargoCaido,
  encargoEnCurso,
  esDeshacer,
  esEncargo,
  estadoCobroEncargo,
  etiquetaDeshacer,
  indicePasoEncargo,
  pideSede,
  puedeMarcarEntregado,
  puedeRegistrarDevolucion,
  puedeRetirar,
  requiereAccion,
} from './mi-caja-pedidos';

export type PestanaMiCaja = 'cobrar' | 'vender' | 'encima' | 'pedidos';

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
  /** Sin cobro (p. ej. «Encargo enviado»), el aviso no ofrece «Deshacer». */
  cobroId: number | null;
  motivo: MotivoDeshacerCobro;
  deshaciendo: boolean;
}

/** Hoja «Encargar un producto» (6c-8): alumno, producto encargable, nota y, si hace falta, la sede (D6c-9). */
interface HojaEncargo {
  alumno: AlumnoDeMisGrupos | null;
  busqueda: string;
  productoId: number | null;
  nota: string;
  sedeId: number | null;
  /** La sede se pide solo si el alumno está con él en grupos de varias sedes. */
  pedirSede: boolean;
  intentado: boolean;
  enviando: boolean;
  error: string | null;
}

/** Confirmación de una acción sobre un pedido: retirar o marcar entregado. */
interface HojaAccion {
  titulo: string;
  detalle: string;
  nota: string;
  verbo: string;
  peligro: boolean;
  hecho: string;
  ejecutar: () => Observable<Solicitud>;
  enviando: boolean;
  error: string | null;
}

/** «Registrar devolución» de un encargo caído y pagado (195): el cobro entero (D6c-11) y una nota opcional. */
interface HojaDevolucion {
  solicitud: Solicitud;
  nota: string;
  enviando: boolean;
  error: string | null;
}

/** «Pedir deshacer» un cobro ya entregado (193): motivo obligatorio y qué ha pasado (MotivoDeshacer, como en A4). */
interface HojaPedirDeshacer {
  cobro: CobroEntregadoReciente;
  motivo: string;
  motivoDeshacer: MotivoDeshacerCobro;
  intentado: boolean;
  enviando: boolean;
  error: string | null;
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

/** Más reciente primero, por fecha de la solicitud. */
function masReciente(a: Solicitud, b: Solicitud): number {
  return (b.solicitadoEn ?? '').localeCompare(a.solicitadoEn ?? '');
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

  // Pedidos (6c-8)
  solicitudes: Solicitud[] = [];
  cargandoSolicitudes = false;
  errorSolicitudes: string | null = null;
  solicitudesCargadas = false;
  productosEncargables: ProductoVendible[] = [];
  cargandoEncargo = false;
  errorCargaEncargo: string | null = null;
  private alumnosCargados = false;
  private encargablesCargados = false;

  // Entregados recientes (6c-8, D6c-14)
  entregadosAbierto = false;
  entregados: CobroEntregadoReciente[] | null = null;
  cargandoEntregados = false;
  errorEntregados: string | null = null;

  readonly pasosEncargo = PASOS_ENCARGO;
  readonly maxNotaEncargo = MAX_NOTA_ENCARGO;
  readonly maxMotivo = MAX_MOTIVO;
  readonly puedeRetirar = puedeRetirar;
  readonly puedeMarcarEntregado = puedeMarcarEntregado;
  readonly puedeRegistrarDevolucion = puedeRegistrarDevolucion;
  readonly devolucionEnLaSede = devolucionEnLaSede;
  readonly encargoCaido = encargoCaido;
  readonly indicePasoEncargo = indicePasoEncargo;
  readonly etiquetaDeshacer = etiquetaDeshacer;

  hoja: HojaCobro | null = null;
  hojaDeshacer: HojaDeshacer | null = null;
  hojaEncargo: HojaEncargo | null = null;
  hojaAccion: HojaAccion | null = null;
  hojaDevolucion: HojaDevolucion | null = null;
  hojaPedirDeshacer: HojaPedirDeshacer | null = null;
  aviso: AvisoCobro | null = null;
  private temporizadorAviso: ReturnType<typeof setTimeout> | null = null;
  private focoAntesDeLaHoja: HTMLElement | null = null;

  ngOnInit(): void {
    this.soloProfesor = esSoloProfesor(this.auth.getRolesActuales());
    this.cargarCargos();
    this.cargarEncima();
    this.cargarSolicitudes();
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
        next: (alumnos) => this.guardarAlumnos(alumnos),
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

  readonly pestanas: PestanaMiCaja[] = ['cobrar', 'vender', 'encima', 'pedidos'];

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

  private mostrarAviso(texto: string, cobroId: number | null = null, motivo: MotivoDeshacerCobro = 'AUN_NO_PAGADO'): void {
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
    if (!aviso || aviso.deshaciendo || aviso.cobroId === null) {
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

  // ------------------------------------------------------------------ pedidos (6c-8)

  cargarSolicitudes(): void {
    this.cargandoSolicitudes = true;
    this.errorSolicitudes = null;
    this.caja
      .misSolicitudes()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.cargandoSolicitudes = false))
      )
      .subscribe({
        next: (lista) => {
          this.solicitudes = lista;
          this.solicitudesCargadas = true;
        },
        error: (e) => (this.errorSolicitudes = mensajeDeError(e) ?? 'No se pudieron cargar tus pedidos.'),
      });
  }

  /** Lo que espera su acción: marcar entregado o registrar la devolución que lleva encima. */
  get numeroPedidos(): number {
    return this.solicitudes.filter(requiereAccion).length;
  }

  /** En curso: primero lo que espera su acción y, dentro, lo más reciente. */
  get encargosEnCurso(): Solicitud[] {
    return this.solicitudes
      .filter(encargoEnCurso)
      .sort((a, b) => Number(requiereAccion(b)) - Number(requiereAccion(a)) || masReciente(a, b));
  }

  get encargosTerminados(): Solicitud[] {
    return this.solicitudes.filter((s) => esEncargo(s) && !encargoEnCurso(s)).sort(masReciente);
  }

  /** Sus solicitudes de deshacer, también las pendientes cuyo cobro ya no sale en «Entregados recientes». */
  get solicitudesDeshacer(): Solicitud[] {
    return this.solicitudes
      .filter(esDeshacer)
      .sort((a, b) => Number(b.estado === 'PENDIENTE') - Number(a.estado === 'PENDIENTE') || masReciente(a, b));
  }

  estadoCobro(s: Solicitud): EstadoCobroCargo | null {
    return estadoCobroEncargo(s);
  }

  etiquetaCobro(estado: EstadoCobroCargo): string {
    return ETIQUETA_COBRO[estado];
  }

  nombreSolicitud(s: Solicitud): string {
    return nombreCompleto(s.alumnoNombre, s.alumnoApellidos);
  }

  // -------------------------------------------------- encargar

  abrirEncargo(evento?: Event): void {
    this.recordarFoco(evento);
    this.hojaEncargo = {
      alumno: null,
      busqueda: '',
      productoId: null,
      nota: '',
      sedeId: null,
      pedirSede: false,
      intentado: false,
      enviando: false,
      error: null,
    };
    this.cargarDatosEncargo();
  }

  /** Alumnos de sus grupos y productos encargables, una sola vez por visita. */
  cargarDatosEncargo(): void {
    if (this.alumnosCargados && this.encargablesCargados) {
      return;
    }
    this.cargandoEncargo = true;
    this.errorCargaEncargo = null;
    let pendientes = 0;
    const terminar = () => {
      pendientes -= 1;
      if (pendientes <= 0) {
        this.cargandoEncargo = false;
      }
    };
    const fallo = (e: unknown) =>
      (this.errorCargaEncargo = mensajeDeError(e) ?? 'No se pudieron cargar tus alumnos ni los productos.');
    if (!this.alumnosCargados) {
      pendientes += 1;
      this.caja
        .alumnos()
        .pipe(takeUntil(this.destroy$), finalize(terminar))
        .subscribe({ next: (alumnos) => this.guardarAlumnos(alumnos), error: fallo });
    }
    if (!this.encargablesCargados) {
      pendientes += 1;
      this.caja
        .productosEncargables()
        .pipe(takeUntil(this.destroy$), finalize(terminar))
        .subscribe({
          next: (productos) => {
            this.productosEncargables = productos;
            this.encargablesCargados = true;
          },
          error: fallo,
        });
    }
  }

  private guardarAlumnos(alumnos: AlumnoDeMisGrupos[]): void {
    this.alumnos = [...alumnos].sort((a, b) =>
      nombreCompleto(a.nombre, a.apellidos).localeCompare(nombreCompleto(b.nombre, b.apellidos), 'es')
    );
    this.alumnosCargados = true;
  }

  get alumnosEncargoFiltrados(): AlumnoDeMisGrupos[] {
    const filtro = normalizarBusqueda(this.hojaEncargo?.busqueda);
    if (!filtro) {
      return this.alumnos;
    }
    return this.alumnos.filter((a) => normalizarBusqueda(nombreCompleto(a.nombre, a.apellidos)).includes(filtro));
  }

  elegirAlumnoEncargo(alumno: AlumnoDeMisGrupos): void {
    const hoja = this.hojaEncargo;
    if (!hoja) {
      return;
    }
    hoja.alumno = alumno;
    hoja.busqueda = '';
    hoja.sedeId = null;
    hoja.pedirSede = (alumno.sedes?.length ?? 0) > 1;
    hoja.error = null;
  }

  cambiarAlumnoEncargo(): void {
    const hoja = this.hojaEncargo;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.alumno = null;
    hoja.pedirSede = false;
    hoja.sedeId = null;
    hoja.error = null;
    setTimeout(() => document.getElementById('buscarAlumnoEncargo')?.focus());
  }

  /** D6c-9: las sedes de sus grupos con el alumno; se elige entre ellas solo si son dos o más. */
  get opcionesSede(): SedeMiCaja[] {
    const propias = this.hojaEncargo?.alumno?.sedes ?? [];
    return propias.length > 1 ? [...propias].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')) : [];
  }

  get productoEncargo(): ProductoVendible | null {
    const id = this.hojaEncargo?.productoId;
    return this.productosEncargables.find((p) => p.id === id) ?? null;
  }

  /** El primer campo que falta o no vale, para señalarlo. */
  get campoEncargoConError(): 'alumno' | 'producto' | 'nota' | 'sede' | null {
    const hoja = this.hojaEncargo;
    if (!hoja) {
      return null;
    }
    if (!hoja.alumno) {
      return 'alumno';
    }
    if (hoja.productoId === null) {
      return 'producto';
    }
    const nota = hoja.nota.trim();
    if (!nota || nota.length > MAX_NOTA_ENCARGO) {
      return 'nota';
    }
    if (hoja.pedirSede && hoja.sedeId === null) {
      return 'sede';
    }
    return null;
  }

  get errorEncargo(): string | null {
    switch (this.campoEncargoConError) {
      case 'alumno':
        return 'Elige el alumno.';
      case 'producto':
        return 'Elige el producto.';
      case 'nota':
        return this.hojaEncargo!.nota.trim()
          ? `La nota admite como máximo ${MAX_NOTA_ENCARGO} caracteres.`
          : 'Escribe la nota (talla, color…).';
      case 'sede':
        return 'Elige la sede.';
      default:
        return null;
    }
  }

  confirmarEncargo(): void {
    const hoja = this.hojaEncargo;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.intentado = true;
    if (this.campoEncargoConError) {
      return;
    }
    const peticion: PedirEncargo = { productoId: hoja.productoId!, nota: hoja.nota.trim() };
    if (hoja.pedirSede && hoja.sedeId !== null) {
      peticion.sedeId = Number(hoja.sedeId);
    }
    hoja.enviando = true;
    hoja.error = null;
    this.caja
      .pedirEncargo(hoja.alumno!.alumnoId, peticion)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.hojaEncargo = null;
          this.restaurarFoco();
          this.mostrarAviso('Encargo enviado');
          this.cargarSolicitudes();
          this.cargarCargos();
        },
        error: (e) => {
          hoja.enviando = false;
          const mensaje = mensajeDeError(e);
          if (pideSede(mensaje) && !hoja.pedirSede) {
            // D6c-9: el alumno está con él en grupos de varias sedes; ahora se le pide elegirla. El campo nuevo ya
            // lo explica, así que no se repite el mensaje como error.
            hoja.pedirSede = true;
            hoja.intentado = false;
            hoja.error = null;
            setTimeout(() => document.getElementById('sedeEncargo')?.focus());
            return;
          }
          hoja.error = mensaje ?? 'No se pudo enviar el encargo. Inténtalo de nuevo.';
        },
      });
  }

  cerrarEncargo(): void {
    if (this.hojaEncargo?.enviando) {
      return;
    }
    this.hojaEncargo = null;
    this.restaurarFoco();
  }

  // -------------------------------------------------- retirar y marcar entregado

  abrirRetirar(s: Solicitud, evento?: Event): void {
    this.recordarFoco(evento);
    const encargo = esEncargo(s);
    this.hojaAccion = {
      titulo: encargo ? 'Retirar el encargo' : 'Retirar la solicitud',
      detalle: this.detalleSolicitud(s),
      nota: encargo
        ? 'Si el alumno aún no lo ha pagado, se cancela su cargo; si ya lo pagó, quedará la devolución pendiente.'
        : 'El cobro sigue como está y puedes volver a pedirlo.',
      verbo: encargo ? 'Retirar encargo' : 'Retirar solicitud',
      peligro: true,
      hecho: encargo ? 'Encargo retirado' : 'Solicitud retirada',
      ejecutar: () => this.caja.retirarSolicitud(s.id),
      enviando: false,
      error: null,
    };
  }

  abrirEntregado(s: Solicitud, evento?: Event): void {
    this.recordarFoco(evento);
    const sinCobrar = this.estadoCobro(s) === 'PENDIENTE';
    this.hojaAccion = {
      titulo: 'Marcar entregado al alumno',
      detalle: this.detalleSolicitud(s),
      nota: sinCobrar
        ? 'Confirma que el alumno ya lo tiene. El cargo sigue en «Por cobrar» hasta que lo pague.'
        : 'Confirma que el alumno ya lo tiene. No se puede deshacer.',
      verbo: 'Marcar entregado',
      peligro: false,
      hecho: 'Encargo entregado',
      ejecutar: () => this.caja.marcarEntregado(s.id),
      enviando: false,
      error: null,
    };
  }

  confirmarAccion(): void {
    const hoja = this.hojaAccion;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.enviando = true;
    hoja.error = null;
    hoja
      .ejecutar()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.hojaAccion = null;
          this.restaurarFoco();
          this.mostrarAviso(hoja.hecho);
          this.cargarSolicitudes();
          this.cargarCargos();
        },
        error: (e) => {
          hoja.enviando = false;
          hoja.error = mensajeDeError(e) ?? 'No se pudo completar. Inténtalo de nuevo.';
          this.cargarSolicitudes();
        },
      });
  }

  cerrarAccion(): void {
    if (this.hojaAccion?.enviando) {
      return;
    }
    this.hojaAccion = null;
    this.restaurarFoco();
  }

  private detalleSolicitud(s: Solicitud): string {
    return [this.nombreSolicitud(s), s.concepto].filter(Boolean).join(' · ');
  }

  // -------------------------------------------------- registrar devolución (195)

  abrirDevolucion(s: Solicitud, evento?: Event): void {
    this.recordarFoco(evento);
    this.hojaDevolucion = { solicitud: s, nota: '', enviando: false, error: null };
  }

  confirmarDevolucion(): void {
    const hoja = this.hojaDevolucion;
    if (!hoja || hoja.enviando) {
      return;
    }
    const nota = hoja.nota.trim();
    if (nota.length > MAX_MOTIVO) {
      hoja.error = `La nota admite como máximo ${MAX_MOTIVO} caracteres.`;
      return;
    }
    hoja.enviando = true;
    hoja.error = null;
    this.caja
      .registrarDevolucion(hoja.solicitud.id, nota || null)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.hojaDevolucion = null;
          this.restaurarFoco();
          this.mostrarAviso(`Devolución registrada ${formatearEuros(hoja.solicitud.importe ?? 0)}`);
          this.cargarSolicitudes();
          this.cargarEncima();
        },
        error: (e) => {
          hoja.enviando = false;
          hoja.error = mensajeDeError(e) ?? 'No se pudo registrar la devolución. Inténtalo de nuevo.';
          this.cargarSolicitudes();
        },
      });
  }

  cerrarDevolucion(): void {
    if (this.hojaDevolucion?.enviando) {
      return;
    }
    this.hojaDevolucion = null;
    this.restaurarFoco();
  }

  // -------------------------------------------------- entregados recientes y pedir deshacer (193, D6c-14)

  alternarEntregados(): void {
    this.entregadosAbierto = !this.entregadosAbierto;
    if (this.entregadosAbierto && this.entregados === null && !this.cargandoEntregados) {
      this.cargarEntregados();
    }
  }

  cargarEntregados(): void {
    this.cargandoEntregados = true;
    this.errorEntregados = null;
    this.caja
      .cobrosEntregadosRecientes()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.cargandoEntregados = false))
      )
      .subscribe({
        next: (lista) => (this.entregados = lista),
        error: (e) => (this.errorEntregados = mensajeDeError(e) ?? 'No se pudieron cargar los cobros entregados.'),
      });
  }

  nombreEntregado(c: CobroEntregadoReciente): string {
    return nombreCompleto(c.alumnoNombre, c.alumnoApellidos);
  }

  abrirPedirDeshacer(cobro: CobroEntregadoReciente, evento?: Event): void {
    this.recordarFoco(evento);
    this.hojaPedirDeshacer = {
      cobro,
      motivo: '',
      motivoDeshacer: 'AUN_NO_PAGADO',
      intentado: false,
      enviando: false,
      error: null,
    };
  }

  get errorPedirDeshacer(): string | null {
    const motivo = this.hojaPedirDeshacer?.motivo.trim() ?? '';
    if (!motivo) {
      return 'Escribe el motivo.';
    }
    return motivo.length > MAX_MOTIVO ? `El motivo admite como máximo ${MAX_MOTIVO} caracteres.` : null;
  }

  confirmarPedirDeshacer(): void {
    const hoja = this.hojaPedirDeshacer;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.intentado = true;
    if (this.errorPedirDeshacer) {
      return;
    }
    // «No hubo venta» solo vale para una venta suya del catálogo (como en A4).
    const motivoDeshacer: MotivoDeshacerCobro = hoja.cobro.venta ? hoja.motivoDeshacer : 'AUN_NO_PAGADO';
    hoja.enviando = true;
    hoja.error = null;
    this.caja
      .pedirDeshacer(hoja.cobro.cobroId, { motivo: hoja.motivo.trim(), motivoDeshacer })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.hojaPedirDeshacer = null;
          this.restaurarFoco();
          this.mostrarAviso('Solicitud enviada al propietario');
          this.cargarEntregados();
          this.cargarSolicitudes();
        },
        error: (e) => {
          hoja.enviando = false;
          hoja.error = mensajeDeError(e) ?? 'No se pudo enviar la solicitud. Inténtalo de nuevo.';
          this.cargarEntregados();
        },
      });
  }

  cerrarPedirDeshacer(): void {
    if (this.hojaPedirDeshacer?.enviando) {
      return;
    }
    this.hojaPedirDeshacer = null;
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
    } else if (this.hojaEncargo) {
      this.cerrarEncargo();
    } else if (this.hojaAccion) {
      this.cerrarAccion();
    } else if (this.hojaDevolucion) {
      this.cerrarDevolucion();
    } else if (this.hojaPedirDeshacer) {
      this.cerrarPedirDeshacer();
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
