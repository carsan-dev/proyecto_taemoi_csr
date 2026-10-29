import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, finalize } from 'rxjs';

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
} from '../../interfaces/caja';
import { EstadoCobroCargo, Solicitud } from '../../interfaces/solicitud';
import { CajaProfesorService } from '../../servicios/endpoints/caja-profesor.service';
import { mensajeDeError } from '../../servicios/endpoints/caja.service';
import { diasDesde, textoRelativo } from '../../componentes/endpoints/caja-sede/caja-calculos';
import {
  GrupoAlumno,
  PestanaMiCaja,
  agruparPorAlumno,
  diaMes,
  nombreCompleto,
  normalizarBusqueda,
} from './mi-caja-cargos';
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
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { OpcionPastilla, ParteMensaje, ResultadoConfirmacion } from '../dialogo/dialogo.component';
import { eur, numero, plural } from '../formato/formato-es';
import { EurPipe } from '../formato/eur.pipe';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';

/** Más reciente primero, por fecha de la solicitud (como la pantalla de hoy). */
function masReciente(a: Solicitud, b: Solicitud): number {
  return (b.solicitadoEn ?? '').localeCompare(a.solicitadoEn ?? '');
}

/** Campo del formulario «Encargar un producto». */
export type CampoEncargo = 'alumno' | 'producto' | 'nota' | 'sede';

/** Un error del formulario «Encargar un producto»: el campo, su etiqueta (para el resumen) y el texto de hoy. */
export interface ErrorEncargo {
  campo: CampoEncargo;
  etiqueta: string;
  mensaje: string;
}

/** Panel «Encargar un producto» (como la hoja de hoy): alumno, producto encargable, nota y, si hace falta, la sede. */
interface HojaEncargo {
  alumno: AlumnoDeMisGrupos | null;
  busqueda: string;
  productoId: number | null;
  nota: string;
  sedeId: number | null;
  /** La sede se pide solo si el alumno está con él en grupos de varias sedes (D6c-9). */
  pedirSede: boolean;
  intentado: boolean;
  enviando: boolean;
  error: string | null;
}

/** Panel «Pedir deshacer» un cobro ya entregado (193): qué ha pasado y el motivo, obligatorio. */
interface HojaPedirDeshacer {
  cobro: CobroEntregadoReciente;
  motivo: string;
  motivoDeshacer: MotivoDeshacerCobro;
  intentado: boolean;
  enviando: boolean;
  error: string | null;
}

/** Las dos respuestas a «Qué ha pasado»; «No hubo venta», solo en una venta del catálogo (como hoy). */
const AUN_NO_PAGADO: OpcionPastilla = { valor: 'AUN_NO_PAGADO', texto: 'Aún no ha pagado (queda pendiente)' };
const NO_HUBO_VENTA: OpcionPastilla = { valor: 'NO_HUBO_VENTA', texto: 'No hubo venta (se cancela el cargo)' };

/** Ids del DOM de cada campo de «Encargar un producto», para el resumen de errores. */
const ID_CAMPO_ENCARGO: Record<CampoEncargo, string> = {
  alumno: 'mi-caja-encargo-alumno',
  producto: 'mi-caja-encargo-producto',
  nota: 'mi-caja-encargo-nota',
  sede: 'mi-caja-encargo-sede',
};

/** «LP» de «Lucía Prado Gil»: la inicial del nombre y la del primer apellido. */
export function iniciales(nombre: string | null | undefined, apellidos: string | null | undefined): string {
  const primera = (texto: string | null | undefined) => (texto ?? '').trim().charAt(0).toLocaleUpperCase('es-ES');
  return primera(nombre) + primera(apellidos) || '?';
}

/**
 * Mi caja del profesor en el panel nuevo (8b-3p; boceto de la ronda 5, propuesta P1, y su «2 bis»). Conserva lo que hace
 * la pantalla de hoy (`componentes/endpoints/mi-caja/`) con la gramática del panel: «Llevas encima» como bloque (columna
 * fija desde 1024 px), cuatro pestañas, cargos agrupados por alumno, Vender en dos pasos (alumno y producto), los
 * diálogos del panel para cobrar, vender, deshacer, retirar, marcar entregado y registrar devolución, y paneles laterales
 * para encargar y pedir deshacer. Cada llamada es la misma que hoy: mismo método, mismos ids y mismo cuerpo, con las
 * mismas validaciones y la misma recarga después, también si falla.
 */
@Component({
  selector: 'pn-mi-caja',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    AcentoEscuelaDirective,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    EsqueletoComponent,
    EurPipe,
    PanelLateralComponent,
  ],
  templateUrl: './mi-caja.component.html',
  styleUrl: './mi-caja.component.scss',
})
export class MiCajaPanelComponent implements OnInit {
  private readonly caja = inject(CajaProfesorService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly destruir = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly diaMes = diaMes;
  readonly numero = numero;
  readonly pasosEncargo = PASOS_ENCARGO;
  readonly puedeRetirar = puedeRetirar;
  readonly puedeMarcarEntregado = puedeMarcarEntregado;
  readonly puedeRegistrarDevolucion = puedeRegistrarDevolucion;
  readonly devolucionEnLaSede = devolucionEnLaSede;
  readonly encargoCaido = encargoCaido;
  readonly indicePasoEncargo = indicePasoEncargo;
  readonly etiquetaDeshacer = etiquetaDeshacer;
  readonly maxNotaEncargo = MAX_NOTA_ENCARGO;
  readonly maxMotivo = MAX_MOTIVO;
  readonly idCampoEncargo = ID_CAMPO_ENCARGO;
  readonly esqueletos = [0, 1, 2];

  readonly pestanas: PestanaMiCaja[] = ['cobrar', 'vender', 'encima', 'pedidos'];
  pestana: PestanaMiCaja = 'cobrar';

  // Por cobrar
  cargos: CargoPendienteProfesor[] = [];
  cargandoCargos = false;
  errorCargos: string | null = null;
  busquedaCargos = '';
  grupos: GrupoAlumno[] = [];
  /** Cargos con un cobro en vuelo: no se cobran dos veces. */
  readonly cobrando = new Set<number>();
  private dialogoAbierto = false;
  /** Cobros ya enviados a deshacer desde el aviso: como hoy, se pide una sola vez. */
  private readonly deshaciendo = new Set<number>();

  // Vender
  alumnos: AlumnoDeMisGrupos[] = [];
  productos: ProductoVendible[] = [];
  cargandoVender = false;
  errorVender: string | null = null;
  private venderCargado = false;
  busquedaAlumno = '';
  alumnoVenta: AlumnoDeMisGrupos | null = null;
  /** Producto con una venta en vuelo. */
  vendiendo: number | null = null;

  // Encima
  encima: MisCobrosPendientes | null = null;
  cargandoEncima = false;
  errorEncima: string | null = null;

  // Entregados recientes
  entregadosAbierto = false;
  entregados: CobroEntregadoReciente[] | null = null;
  cargandoEntregados = false;
  errorEntregados: string | null = null;

  // Pedidos
  solicitudes: Solicitud[] = [];
  cargandoSolicitudes = false;
  errorSolicitudes: string | null = null;
  solicitudesCargadas = false;

  // Encargar
  productosEncargables: ProductoVendible[] = [];
  cargandoEncargo = false;
  errorCargaEncargo: string | null = null;
  private alumnosCargados = false;
  private encargablesCargados = false;
  hojaEncargo: HojaEncargo | null = null;

  // Pedir deshacer
  hojaPedirDeshacer: HojaPedirDeshacer | null = null;

  private cargaCargos?: Subscription;
  private cargaEncima?: Subscription;
  private cargaSolicitudes?: Subscription;
  private cargaEntregados?: Subscription;
  /** Tras una acción, si el botón que tenía el foco desaparece con la recarga, el foco va a la pestaña. */
  private focoPorRecolocar = false;
  /** Falso al destruir la pantalla: el aviso de «Deshacer» sobrevive a ella y no debe recargar nada. */
  private viva = true;

  ngOnInit(): void {
    this.destruir.onDestroy(() => (this.viva = false));
    this.cargarCargos();
    this.cargarEncima();
    this.cargarSolicitudes();
  }

  // ------------------------------------------------------------------ carga

  cargarCargos(): void {
    this.cargaCargos?.unsubscribe();
    this.cargandoCargos = true;
    this.errorCargos = null;
    this.cargaCargos = this.caja
      .cargosPendientes()
      .pipe(
        takeUntilDestroyed(this.destruir),
        finalize(() => (this.cargandoCargos = false))
      )
      .subscribe({
        next: (cargos) => {
          this.cargos = cargos;
          this.filtrarCargos();
          this.recolocarFoco();
        },
        error: (e) => {
          this.errorCargos = mensajeDeError(e) ?? 'No se pudieron cargar los cobros pendientes.';
          this.focoPorRecolocar = false;
        },
      });
  }

  cargarEncima(): void {
    this.cargaEncima?.unsubscribe();
    this.cargandoEncima = true;
    this.errorEncima = null;
    this.cargaEncima = this.caja
      .cobrosPendientesEntrega()
      .pipe(
        takeUntilDestroyed(this.destruir),
        finalize(() => (this.cargandoEncima = false))
      )
      .subscribe({
        next: (encima) => {
          this.encima = encima;
          this.recolocarFoco();
        },
        error: (e) => {
          this.errorEncima = mensajeDeError(e) ?? 'No se pudo cargar lo que llevas encima.';
          this.focoPorRecolocar = false;
        },
      });
  }

  cargarSolicitudes(): void {
    this.cargaSolicitudes?.unsubscribe();
    this.cargandoSolicitudes = true;
    this.errorSolicitudes = null;
    this.cargaSolicitudes = this.caja
      .misSolicitudes()
      .pipe(
        takeUntilDestroyed(this.destruir),
        finalize(() => (this.cargandoSolicitudes = false))
      )
      .subscribe({
        next: (lista) => {
          this.solicitudes = lista;
          this.solicitudesCargadas = true;
          this.recolocarFoco();
        },
        error: (e) => {
          this.errorSolicitudes = mensajeDeError(e) ?? 'No se pudieron cargar tus pedidos.';
          this.focoPorRecolocar = false;
        },
      });
  }

  /** Alumnos de sus grupos y catálogo de venta (las mismas dos llamadas que hoy). */
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
      .pipe(takeUntilDestroyed(this.destruir), finalize(terminar))
      .subscribe({ next: (alumnos) => this.guardarAlumnos(alumnos), error: fallo });
    this.caja
      .productos()
      .pipe(takeUntilDestroyed(this.destruir), finalize(terminar))
      .subscribe({ next: (productos) => (this.productos = productos), error: fallo });
  }

  private guardarAlumnos(alumnos: AlumnoDeMisGrupos[]): void {
    this.alumnos = [...alumnos].sort((a, b) =>
      nombreCompleto(a.nombre, a.apellidos).localeCompare(nombreCompleto(b.nombre, b.apellidos), 'es')
    );
    this.alumnosCargados = true;
  }

  /** Tras cada acción: lo pendiente y lo que llevas encima cambian (como hoy). */
  private recargarContadores(): void {
    this.cargarCargos();
    this.cargarEncima();
  }

  // ------------------------------------------------------------------ pestañas

  /** «Vender» pide sus alumnos y el catálogo la primera vez que se abre, como hoy. */
  elegirPestana(pestana: PestanaMiCaja): void {
    this.pestana = pestana;
    if (pestana === 'vender' && !this.venderCargado) {
      this.cargarVender();
    }
  }

  /** Flechas izquierda y derecha entre pestañas, con vuelta (patrón ARIA de pestañas, como hoy). */
  moverPestana(evento: KeyboardEvent): void {
    const paso = evento.key === 'ArrowRight' ? 1 : evento.key === 'ArrowLeft' ? -1 : 0;
    if (!paso) {
      return;
    }
    evento.preventDefault();
    const i = this.pestanas.indexOf(this.pestana);
    const siguiente = this.pestanas[(i + paso + this.pestanas.length) % this.pestanas.length];
    this.elegirPestana(siguiente);
    setTimeout(() => this.pestanaDom(siguiente)?.focus());
  }

  get numeroPorCobrar(): number {
    return this.cargos.length;
  }

  get numeroEncima(): number {
    return this.encima?.numeroCobros ?? 0;
  }

  /** Cargos que se ven con la búsqueda. */
  get cargosVisibles(): number {
    return this.grupos.reduce((total, g) => total + g.cargos.length, 0);
  }

  cobrosSinEntregar(n: number): string {
    return plural(n, 'cobro sin entregar', 'cobros sin entregar');
  }

  buscar(evento: Event): void {
    this.busquedaCargos = (evento.target as HTMLInputElement).value;
    this.filtrarCargos();
  }

  quitarBusqueda(): void {
    this.busquedaCargos = '';
    this.filtrarCargos();
    setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('#mi-caja-buscar')?.focus());
  }

  filtrarCargos(): void {
    this.grupos = agruparPorAlumno(this.cargos, this.busquedaCargos);
  }

  inicialesGrupo(grupo: GrupoAlumno): string {
    const cargo = grupo.cargos[0];
    return iniciales(cargo?.alumnoNombre, cargo?.alumnoApellidos);
  }

  // ------------------------------------------------------------------ cobrar

  /**
   * «Cobrar»: el diálogo del panel con la cifra en ocre y, al confirmar, la misma llamada que hoy
   * (`cobrarCargo(cargoId)`). Tras cobrar, el aviso «Cobrado …» con «Deshacer» y la recarga de los contadores; si falla,
   * el error del servidor en un aviso y también la recarga (el cargo pudo cobrarse o anularse entretanto).
   */
  async abrirCobro(cargo: CargoPendienteProfesor): Promise<void> {
    const id = cargo.cargoId;
    if (this.cobrando.has(id) || this.capaAbierta) {
      return;
    }
    this.dialogoAbierto = true;
    const importe = eur(cargo.importe);
    const resultado = await this.confirmacion.confirmar<CobroCaja>({
      titulo: `Cobrar a ${nombreCompleto(cargo.alumnoNombre, cargo.alumnoApellidos)}`,
      mensaje: 'Queda en tu efectivo hasta que lo entregues en la sede.',
      destacado: { etiqueta: cargo.concepto, valor: importe },
      accion: `Cobrar ${importe}`,
      accionOcupada: 'Cobrando…',
      nombre: 'mi-caja-cobrar',
      ejecutar: () => {
        this.cobrando.add(id);
        return this.caja.cobrarCargo(id);
      },
    });
    this.dialogoAbierto = false;
    this.cobrando.delete(id);
    if (!resultado.confirmado) {
      return;
    }
    this.focoPorRecolocar = true;
    if (resultado.error !== undefined) {
      this.avisos.error(mensajeDeError(resultado.error) ?? 'No se pudo cobrar. Inténtalo de nuevo.');
      this.recargarContadores();
      return;
    }
    const cobro = resultado.respuesta;
    const cobroId = cobro?.cobroId ?? null;
    this.avisos.exito(
      `Cobrado ${eur(cobro?.importe ?? cargo.importe)}`,
      cobroId === null ? undefined : { texto: 'Deshacer', alPulsar: () => this.deshacerDesdeAviso(cobroId, 'AUN_NO_PAGADO') }
    );
    this.recargarContadores();
  }

  /** «Deshacer» del aviso: la misma llamada que hoy (`deshacerCobro(cobroId, motivo)`), una sola vez. */
  deshacerDesdeAviso(cobroId: number, motivo: MotivoDeshacerCobro): void {
    if (this.deshaciendo.has(cobroId)) {
      return;
    }
    this.deshaciendo.add(cobroId);
    // Sin takeUntilDestroyed: el aviso sobrevive a la pantalla y la petición debe completarse igual.
    this.caja.deshacerCobro(cobroId, motivo).subscribe({
      next: () => {
        if (this.viva) {
          this.recargarContadores();
        }
      },
      error: (e) => {
        this.avisos.error(mensajeDeError(e) ?? 'No se pudo deshacer el cobro.');
        if (this.viva) {
          this.recargarContadores();
        }
      },
    });
  }

  // ------------------------------------------------------------------ vender

  /** Alumnos que se ven con la búsqueda de «Vender» (sin tildes, como hoy). */
  get alumnosFiltrados(): AlumnoDeMisGrupos[] {
    return this.filtrarAlumnos(this.busquedaAlumno);
  }

  private filtrarAlumnos(busqueda: string | null | undefined): AlumnoDeMisGrupos[] {
    const filtro = normalizarBusqueda(busqueda);
    if (!filtro) {
      return this.alumnos;
    }
    return this.alumnos.filter((a) => normalizarBusqueda(nombreCompleto(a.nombre, a.apellidos)).includes(filtro));
  }

  nombreAlumno(a: { nombre?: string | null; apellidos?: string | null } | null): string {
    return a ? nombreCompleto(a.nombre, a.apellidos) : '';
  }

  inicialesAlumno(a: AlumnoDeMisGrupos): string {
    return iniciales(a.nombre, a.apellidos);
  }

  buscarAlumno(evento: Event): void {
    this.busquedaAlumno = (evento.target as HTMLInputElement).value;
  }

  quitarBusquedaAlumno(): void {
    this.busquedaAlumno = '';
    this.enfocar('#mi-caja-buscar-alumno');
  }

  /** Primer paso de la venta: el alumno. El foco pasa a «Cambiar» (la lista desaparece). */
  elegirAlumno(alumno: AlumnoDeMisGrupos): void {
    this.alumnoVenta = alumno;
    this.busquedaAlumno = '';
    this.enfocar('#mi-caja-cambiar-alumno');
  }

  cambiarAlumno(): void {
    this.alumnoVenta = null;
    this.enfocar('#mi-caja-buscar-alumno');
  }

  /**
   * «Vender»: el mismo diálogo de cobro que «Cobrar», con los textos de hoy para la venta, y la misma llamada que hoy
   * (`venderProducto(alumnoId, productoId)`). Tras vender, el aviso «Vendido …» con «Deshacer» (que deshace con «No hubo
   * venta», como hoy) y la recarga de los contadores; si falla, el error en un aviso y también la recarga.
   */
  async abrirVenta(producto: ProductoVendible): Promise<void> {
    const alumno = this.alumnoVenta;
    if (!alumno || this.vendiendo !== null || this.capaAbierta) {
      return;
    }
    this.dialogoAbierto = true;
    const importe = eur(producto.precio);
    const resultado = await this.confirmacion.confirmar<CobroCaja>({
      titulo: 'Vender y cobrar en efectivo',
      mensaje: 'Queda en tu efectivo hasta que lo entregues en la sede.',
      destacado: { etiqueta: `${nombreCompleto(alumno.nombre, alumno.apellidos)} · ${producto.concepto}`, valor: importe },
      accion: `Vender ${importe}`,
      accionOcupada: 'Vendiendo…',
      nombre: 'mi-caja-vender',
      ejecutar: () => {
        this.vendiendo = producto.id;
        return this.caja.venderProducto(alumno.alumnoId, producto.id);
      },
    });
    this.dialogoAbierto = false;
    this.vendiendo = null;
    if (!resultado.confirmado) {
      return;
    }
    this.focoPorRecolocar = true;
    if (resultado.error !== undefined) {
      this.avisos.error(mensajeDeError(resultado.error) ?? 'No se pudo cobrar. Inténtalo de nuevo.');
      this.recargarContadores();
      return;
    }
    const cobro = resultado.respuesta;
    const cobroId = cobro?.cobroId ?? null;
    this.avisos.exito(
      `Vendido ${eur(cobro?.importe ?? producto.precio)}`,
      cobroId === null ? undefined : { texto: 'Deshacer', alPulsar: () => this.deshacerDesdeAviso(cobroId, 'NO_HUBO_VENTA') }
    );
    this.recargarContadores();
  }

  // ------------------------------------------------------------------ encima

  nombreCobro(c: CobroCaja | CobroEntregadoReciente): string {
    return nombreCompleto(c.alumnoNombre, c.alumnoApellidos);
  }

  cuandoCobro(c: CobroCaja): string {
    return textoRelativo(diasDesde(c.fechaCobro));
  }

  /**
   * «Deshacer» desde Encima: diálogo de peligro con «Qué ha pasado» y la misma llamada que hoy
   * (`deshacerCobro(cobroId, motivo)`). «No hubo venta» solo se ofrece, y solo vale, en una venta del catálogo.
   */
  async abrirDeshacer(cobro: CobroCaja): Promise<void> {
    if (this.capaAbierta) {
      return;
    }
    this.dialogoAbierto = true;
    const campo = 'mi-caja-que-ha-pasado';
    let elegido: string | undefined;
    const resultado = await this.confirmacion.confirmar<void>({
      titulo: 'Deshacer el cobro',
      mensaje: [{ fuerte: this.nombreCobro(cobro) }, ` · ${cobro.concepto} · ${eur(cobro.importe)}`],
      campos: [
        {
          id: campo,
          tipo: 'pastillas',
          etiqueta: 'Qué ha pasado (obligatorio)',
          opciones: cobro.venta ? [AUN_NO_PAGADO, NO_HUBO_VENTA] : [AUN_NO_PAGADO],
          valor: 'AUN_NO_PAGADO',
        },
      ],
      accion: 'Deshacer cobro',
      accionOcupada: 'Deshaciendo…',
      destructivo: true,
      nombre: 'mi-caja-deshacer',
      comprobar: (valores) => {
        elegido = valores[campo];
        return null;
      },
      ejecutar: () => {
        const motivo: MotivoDeshacerCobro = cobro.venta && elegido === 'NO_HUBO_VENTA' ? 'NO_HUBO_VENTA' : 'AUN_NO_PAGADO';
        return this.caja.deshacerCobro(cobro.cobroId, motivo);
      },
    });
    this.dialogoAbierto = false;
    if (!resultado.confirmado) {
      return;
    }
    this.focoPorRecolocar = true;
    if (resultado.error !== undefined) {
      this.avisos.error(mensajeDeError(resultado.error) ?? 'No se pudo deshacer el cobro.');
      this.cargarEncima();
      return;
    }
    this.recargarContadores();
  }

  alternarEntregados(): void {
    this.entregadosAbierto = !this.entregadosAbierto;
    if (this.entregadosAbierto && this.entregados === null && !this.cargandoEntregados) {
      this.cargarEntregados();
    }
  }

  cargarEntregados(): void {
    this.cargaEntregados?.unsubscribe();
    this.cargandoEntregados = true;
    this.errorEntregados = null;
    this.cargaEntregados = this.caja
      .cobrosEntregadosRecientes()
      .pipe(
        takeUntilDestroyed(this.destruir),
        finalize(() => (this.cargandoEntregados = false))
      )
      .subscribe({
        next: (lista) => {
          this.entregados = lista;
          this.recolocarFoco();
        },
        error: (e) => {
          this.errorEntregados = mensajeDeError(e) ?? 'No se pudieron cargar los cobros entregados.';
          this.focoPorRecolocar = false;
        },
      });
  }

  abrirPedirDeshacer(cobro: CobroEntregadoReciente): void {
    if (this.capaAbierta) {
      return;
    }
    this.hojaPedirDeshacer = {
      cobro,
      motivo: '',
      motivoDeshacer: 'AUN_NO_PAGADO',
      intentado: false,
      enviando: false,
      error: null,
    };
  }

  /** «Entregado el 03/10 en Umbrete», como hoy. */
  entregadoEl(c: CobroEntregadoReciente): string {
    return c.sedeNombre ? `${diaMes(c.fechaEntrega)} en ${c.sedeNombre}` : diaMes(c.fechaEntrega);
  }

  get errorPedirDeshacer(): string | null {
    const motivo = this.hojaPedirDeshacer?.motivo.trim() ?? '';
    if (!motivo) {
      return 'Escribe el motivo.';
    }
    return motivo.length > MAX_MOTIVO ? `El motivo admite como máximo ${MAX_MOTIVO} caracteres.` : null;
  }

  ponerMotivoPedirDeshacer(evento: Event): void {
    if (this.hojaPedirDeshacer) {
      this.hojaPedirDeshacer.motivo = (evento.target as HTMLTextAreaElement).value;
    }
  }

  elegirQueHaPasado(motivo: MotivoDeshacerCobro): void {
    if (this.hojaPedirDeshacer && !this.hojaPedirDeshacer.enviando) {
      this.hojaPedirDeshacer.motivoDeshacer = motivo;
    }
  }

  /** «Enviar solicitud»: la misma llamada y el mismo cuerpo que hoy (`pedirDeshacer(cobroId, {motivo, motivoDeshacer})`). */
  confirmarPedirDeshacer(): void {
    const hoja = this.hojaPedirDeshacer;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.intentado = true;
    if (this.errorPedirDeshacer) {
      this.enfocar('#mi-caja-motivo-pedir');
      return;
    }
    // «No hubo venta» solo vale para una venta suya del catálogo (como en A4).
    const motivoDeshacer: MotivoDeshacerCobro = hoja.cobro.venta ? hoja.motivoDeshacer : 'AUN_NO_PAGADO';
    hoja.enviando = true;
    hoja.error = null;
    this.caja
      .pedirDeshacer(hoja.cobro.cobroId, { motivo: hoja.motivo.trim(), motivoDeshacer })
      .pipe(takeUntilDestroyed(this.destruir))
      .subscribe({
        next: () => {
          this.hojaPedirDeshacer = null;
          this.focoPorRecolocar = true;
          this.avisos.exito('Solicitud enviada al propietario');
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
  }

  // ------------------------------------------------------------------ pedidos

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

  get sinPedidos(): boolean {
    return this.encargosEnCurso.length + this.encargosTerminados.length + this.solicitudesDeshacer.length === 0;
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

  abrirEncargo(): void {
    if (this.capaAbierta) {
      return;
    }
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

  /** Alumnos de sus grupos y productos encargables, una sola vez por visita (las mismas llamadas que hoy). */
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
        .pipe(takeUntilDestroyed(this.destruir), finalize(terminar))
        .subscribe({ next: (alumnos) => this.guardarAlumnos(alumnos), error: fallo });
    }
    if (!this.encargablesCargados) {
      pendientes += 1;
      this.caja
        .productosEncargables()
        .pipe(takeUntilDestroyed(this.destruir), finalize(terminar))
        .subscribe({
          next: (productos) => {
            this.productosEncargables = productos;
            this.encargablesCargados = true;
          },
          error: fallo,
        });
    }
  }

  get alumnosEncargoFiltrados(): AlumnoDeMisGrupos[] {
    return this.filtrarAlumnos(this.hojaEncargo?.busqueda);
  }

  buscarAlumnoEncargo(evento: Event): void {
    if (this.hojaEncargo) {
      this.hojaEncargo.busqueda = (evento.target as HTMLInputElement).value;
    }
  }

  elegirAlumnoEncargo(alumno: AlumnoDeMisGrupos): void {
    const hoja = this.hojaEncargo;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.alumno = alumno;
    hoja.busqueda = '';
    hoja.sedeId = null;
    hoja.pedirSede = (alumno.sedes?.length ?? 0) > 1;
    hoja.error = null;
    this.enfocar('#mi-caja-encargo-cambiar');
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
    this.enfocar('#mi-caja-encargo-alumno');
  }

  elegirProductoEncargo(id: number): void {
    if (this.hojaEncargo && !this.hojaEncargo.enviando) {
      this.hojaEncargo.productoId = id;
    }
  }

  ponerNotaEncargo(evento: Event): void {
    if (this.hojaEncargo) {
      this.hojaEncargo.nota = (evento.target as HTMLTextAreaElement).value;
    }
  }

  elegirSedeEncargo(evento: Event): void {
    if (this.hojaEncargo) {
      const valor = (evento.target as HTMLSelectElement).value;
      this.hojaEncargo.sedeId = valor === '' ? null : Number(valor);
    }
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

  /** Todos los campos que faltan o no valen, en orden, con los textos de hoy. */
  get erroresEncargo(): ErrorEncargo[] {
    const hoja = this.hojaEncargo;
    if (!hoja) {
      return [];
    }
    const errores: ErrorEncargo[] = [];
    if (!hoja.alumno) {
      errores.push({ campo: 'alumno', etiqueta: 'Alumno', mensaje: 'Elige el alumno.' });
    }
    if (hoja.productoId === null) {
      errores.push({ campo: 'producto', etiqueta: 'Producto', mensaje: 'Elige el producto.' });
    }
    const nota = hoja.nota.trim();
    if (!nota) {
      errores.push({ campo: 'nota', etiqueta: 'Nota', mensaje: 'Escribe la nota (talla, color…).' });
    } else if (nota.length > MAX_NOTA_ENCARGO) {
      errores.push({ campo: 'nota', etiqueta: 'Nota', mensaje: `La nota admite como máximo ${MAX_NOTA_ENCARGO} caracteres.` });
    }
    if (hoja.pedirSede && hoja.sedeId === null) {
      errores.push({ campo: 'sede', etiqueta: 'Sede', mensaje: 'Elige la sede.' });
    }
    return errores;
  }

  /** El error en línea de un campo, solo tras intentar enviar. */
  errorEncargo(campo: CampoEncargo): string | null {
    if (!this.hojaEncargo?.intentado) {
      return null;
    }
    return this.erroresEncargo.find((e) => e.campo === campo)?.mensaje ?? null;
  }

  get tituloResumenEncargo(): string {
    const n = this.erroresEncargo.length;
    return `Revisa ${plural(n, 'campo', 'campos')}`;
  }

  /** Un enlace del resumen de errores lleva el foco a su campo. */
  irACampoEncargo(evento: Event, campo: CampoEncargo): void {
    evento.preventDefault();
    this.enfocar(`#${ID_CAMPO_ENCARGO[campo]}`);
  }

  /** «Encargar»: la misma llamada y el mismo cuerpo que hoy (`pedirEncargo(alumnoId, {productoId, nota, sedeId?})`). */
  confirmarEncargo(): void {
    const hoja = this.hojaEncargo;
    if (!hoja || hoja.enviando) {
      return;
    }
    hoja.intentado = true;
    if (this.erroresEncargo.length) {
      this.enfocar('.mi-caja__resumen-errores');
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
      .pipe(takeUntilDestroyed(this.destruir))
      .subscribe({
        next: () => {
          this.hojaEncargo = null;
          this.focoPorRecolocar = true;
          this.avisos.exito('Encargo enviado');
          this.cargarSolicitudes();
          this.cargarCargos();
        },
        error: (e) => {
          hoja.enviando = false;
          const mensaje = mensajeDeError(e);
          if (pideSede(mensaje) && !hoja.pedirSede) {
            // D6c-9: el alumno está con él en grupos de varias sedes; ahora se le pide elegirla. El campo nuevo ya lo
            // explica, así que no se repite el mensaje como error (como hoy).
            hoja.pedirSede = true;
            hoja.intentado = false;
            hoja.error = null;
            this.enfocar(`#${ID_CAMPO_ENCARGO.sede}`);
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
  }

  // -------------------------------------------------- retirar, marcar entregado y registrar devolución

  /** «Ana Gil · Dobok infantil» en partes, con el nombre en negrita. */
  private detalleSolicitud(s: Solicitud): ParteMensaje[] {
    const partes: ParteMensaje[] = [{ fuerte: this.nombreSolicitud(s) }];
    if (s.concepto) {
      partes.push(` · ${s.concepto}`);
    }
    return partes;
  }

  /** «Retirar»: diálogo de peligro con los textos de hoy y la misma llamada (`retirarSolicitud(id)`). */
  abrirRetirar(s: Solicitud): Promise<void> {
    const encargo = esEncargo(s);
    return this.accionSolicitud({
      titulo: encargo ? 'Retirar el encargo' : 'Retirar la solicitud',
      detalle: this.detalleSolicitud(s),
      nota: encargo
        ? 'Si el alumno aún no lo ha pagado, se cancela su cargo; si ya lo pagó, quedará la devolución pendiente.'
        : 'El cobro sigue como está y puedes volver a pedirlo.',
      verbo: encargo ? 'Retirar encargo' : 'Retirar solicitud',
      ocupado: 'Retirando…',
      peligro: true,
      hecho: encargo ? 'Encargo retirado' : 'Solicitud retirada',
      nombre: 'mi-caja-retirar',
      ejecutar: () => this.caja.retirarSolicitud(s.id),
    });
  }

  /** «Marcar entregado al alumno»: diálogo normal con los textos de hoy y la misma llamada (`marcarEntregado(id)`). */
  abrirEntregado(s: Solicitud): Promise<void> {
    const sinCobrar = this.estadoCobro(s) === 'PENDIENTE';
    return this.accionSolicitud({
      titulo: 'Marcar entregado al alumno',
      detalle: this.detalleSolicitud(s),
      nota: sinCobrar
        ? 'Confirma que el alumno ya lo tiene. El cargo sigue en «Por cobrar» hasta que lo pague.'
        : 'Confirma que el alumno ya lo tiene. No se puede deshacer.',
      verbo: 'Marcar entregado',
      ocupado: 'Marcando…',
      peligro: false,
      hecho: 'Encargo entregado',
      nombre: 'mi-caja-entregado',
      ejecutar: () => this.caja.marcarEntregado(s.id),
    });
  }

  /** Retirar o marcar entregado: tras hacerlo, el aviso y la recarga de hoy; si falla, el error y releer los pedidos. */
  private async accionSolicitud(a: {
    titulo: string;
    detalle: ParteMensaje[];
    nota: string;
    verbo: string;
    ocupado: string;
    peligro: boolean;
    hecho: string;
    nombre: string;
    ejecutar: () => Observable<Solicitud>;
  }): Promise<void> {
    if (this.capaAbierta) {
      return;
    }
    this.dialogoAbierto = true;
    const resultado = await this.confirmacion.confirmar<Solicitud>({
      titulo: a.titulo,
      mensaje: a.detalle,
      bloques: [{ tipo: 'parrafo', partes: [a.nota] }],
      accion: a.verbo,
      accionOcupada: a.ocupado,
      destructivo: a.peligro,
      nombre: a.nombre,
      ejecutar: a.ejecutar,
    });
    this.dialogoAbierto = false;
    this.terminarAccion(resultado, 'No se pudo completar. Inténtalo de nuevo.', () => {
      this.avisos.exito(a.hecho);
      this.cargarSolicitudes();
      this.cargarCargos();
    });
  }

  /**
   * «Registrar devolución» de un encargo caído y pagado (195): diálogo de peligro con la cifra en tinta, la nota
   * opcional y la misma llamada que hoy (`registrarDevolucion(id, nota || null)`).
   */
  async abrirDevolucion(s: Solicitud): Promise<void> {
    if (this.capaAbierta) {
      return;
    }
    this.dialogoAbierto = true;
    const campo = 'mi-caja-nota-devolucion';
    let nota = '';
    const importe = eur(s.importe ?? 0);
    const resultado = await this.confirmacion.confirmar<Solicitud>({
      titulo: 'Registrar devolución',
      mensaje: this.detalleSolicitud(s),
      bloques: [{ tipo: 'parrafo', partes: ['Se devuelve el cobro entero y sale de lo que llevas encima. Da el dinero a la familia.'] }],
      destacado: { etiqueta: '', valor: importe },
      campos: [
        {
          id: campo,
          tipo: 'textarea',
          etiqueta: 'Nota (opcional)',
          placeholder: 'Por ejemplo, a quién se lo diste',
          atributos: { maxlength: String(MAX_MOTIVO) },
        },
      ],
      accion: `Devolver ${importe}`,
      accionOcupada: 'Devolviendo…',
      destructivo: true,
      nombre: 'mi-caja-devolucion',
      comprobar: (valores) => {
        nota = String(valores[campo] ?? '').trim();
        return nota.length > MAX_MOTIVO
          ? { campo, mensaje: `La nota admite como máximo ${MAX_MOTIVO} caracteres.` }
          : null;
      },
      ejecutar: () => this.caja.registrarDevolucion(s.id, nota || null),
    });
    this.dialogoAbierto = false;
    this.terminarAccion(resultado, 'No se pudo registrar la devolución. Inténtalo de nuevo.', () => {
      this.avisos.exito(`Devolución registrada ${importe}`);
      this.cargarSolicitudes();
      this.cargarEncima();
    });
  }

  /** Cancelar no hace nada; si falló, el error del servidor en un aviso y releer los pedidos (como hoy). */
  private terminarAccion(r: ResultadoConfirmacion<Solicitud>, porDefecto: string, bien: () => void): void {
    if (!r.confirmado) {
      return;
    }
    this.focoPorRecolocar = true;
    if (r.error !== undefined) {
      this.avisos.error(mensajeDeError(r.error) ?? porDefecto);
      this.cargarSolicitudes();
      return;
    }
    bien();
  }

  // ------------------------------------------------------------------ foco y capas

  /** Un solo diálogo o panel a la vez: mientras hay uno abierto, ningún botón abre otro. */
  get capaAbierta(): boolean {
    return this.dialogoAbierto || this.hojaEncargo !== null || this.hojaPedirDeshacer !== null;
  }

  /** Lleva el foco a un elemento de la pantalla (o al primer control dentro de él) tras pintarse. */
  private enfocar(selector: string): void {
    setTimeout(() => {
      const elemento = this.host.nativeElement.querySelector<HTMLElement>(selector);
      const control = elemento?.matches('input, select, textarea, button, a, [tabindex]')
        ? elemento
        : elemento?.querySelector<HTMLElement>('input:not(:disabled), select:not(:disabled), textarea:not(:disabled), button:not(:disabled)');
      (control ?? elemento)?.focus();
    });
  }

  private pestanaDom(pestana: PestanaMiCaja): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>(`#mi-caja-pestana-${pestana}`);
  }

  /** Si el botón que abrió el diálogo ya no está (la fila se fue tras cobrar), el foco va a la pestaña actual. */
  private recolocarFoco(): void {
    if (!this.focoPorRecolocar) {
      return;
    }
    this.focoPorRecolocar = false;
    setTimeout(() => {
      const activo = typeof document !== 'undefined' ? document.activeElement : null;
      if (!activo || activo === document.body || !activo.isConnected) {
        this.pestanaDom(this.pestana)?.focus();
      }
    });
  }
}
