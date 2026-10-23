import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { Observable, Subject, Subscription, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, switchMap, takeUntil } from 'rxjs/operators';

import {
  CalculoEntrega,
  EstadoSeleccion,
  ReglaMovimiento,
  TIPOS_MOVIMIENTO,
  calcularEntrega,
  estadoSeleccion,
  etiquetaTipoMovimiento,
  importeConSigno,
  importeParaCampo,
  reglaMovimiento,
} from '../../componentes/endpoints/caja-sede/caja-calculos';
import { SearchableSelectDirective } from '../../directives/searchable-select.directive';
import {
  CobroCaja,
  EntregaCaja,
  MovimientoCaja,
  MovimientoDeEntrega,
  PendienteCobrador,
  ResumenCajaSede,
  SedeCaja,
  TipoMovimientoCaja,
} from '../../interfaces/caja';
import { CrearSolicitud } from '../../interfaces/solicitud';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { CajaService, PeriodoCaja, mensajeDeError, periodoDeMes } from '../../servicios/endpoints/caja.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { SolicitudesService } from '../../servicios/endpoints/solicitudes.service';
import { AcentoEscuelaActual, AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { paginasVisibles } from '../convocatorias/convocatorias-vista';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { ParteMensaje } from '../dialogo/dialogo.component';
import { eur, numero, plural } from '../formato/formato-es';
import { variablesDelTema } from '../tema-escuela';
import {
  AccionAnular,
  CLAVE_ULTIMA_SEDE,
  CifraResumen,
  MAX_MOTIVO,
  OpcionPeriodo,
  PESTANAS,
  PestanaCaja,
  TEXTO_ENTREGA_CAMBIADA,
  accionEntrega,
  accionMovimiento,
  avisoCuadre,
  avisoEfectivo,
  cifrasResumen,
  construirPeriodos,
  cuadre,
  datosPedirEntrega,
  datosPedirMovimiento,
  diferenciaVista,
  etiquetaPestana,
  fechaDia,
  fechaHora,
  firmado,
  mensajeAnularEntrega,
  mensajeAnularMovimiento,
  nombreAlumnoCobro,
  numeroPestana,
  textoDesde,
  textoMostrando,
  textoMovimientoDeCobro,
  tonoImporte,
} from './caja-sede-vista';

/** «Recibir entrega» abierto bajo un profesor; `sedeId`, la sede en la que se abrió (no se envía a otra). */
interface FormEntrega {
  profesor: PendienteCobrador;
  sedeId: number;
  seleccionados: Set<number>;
  contado: string;
  contadoEditado: boolean;
  nota: string;
  enviando: boolean;
  error: string | null;
  disparador: HTMLElement | null;
}

interface FormMovimiento {
  sedeId: number;
  tipo: TipoMovimientoCaja;
  importe: string;
  alumnoId: number | null;
  nota: string;
  enviando: boolean;
  error: string | null;
  disparador: HTMLElement | null;
}

/** Fallo de una lectura de datos; `mensaje`, el del servidor si lo trajo. */
interface ErrorDatos {
  mensaje: string | null;
}

/**
 * Caja de sede del panel nuevo (8b-3j, P2 «pestañas Por recibir, Entregas y Movimientos»). Bajo el título, la sede (un
 * desplegable con varias; con una, su nombre) y el periodo; arriba, el resumen con saldo, pendiente de entregar,
 * recibido en el periodo y diferencias; debajo, tres pestañas con su número. Abre en «Por recibir»: el efectivo en mano
 * de cada profesor, con «Recibir entrega» abierto bajo el profesor (cobros, debería entregar, has contado, faltan o
 * sobran y nota obligatoria si no cuadra). «Entregas» lleva las anuladas tachadas con su motivo y sus correcciones, y
 * «Movimientos», «Apuntar movimiento».
 *
 * Las MISMAS peticiones, cuerpos, cálculos y criterios por rol que `CajaSedeComponent` (con `CajaService`, que ya lee y
 * escribe con `ERROR_HANDLED_LOCALLY`, y `calcularEntrega`, `importeConSigno` y `reglaMovimiento` de hoy); las
 * confirmaciones de hoy con el diálogo del panel (foco contenido) y los avisos del panel. Como hoy, solo el id de la
 * última sede va al almacenamiento del navegador (decisión 206); nada va a la URL. Va en su propio chunk detrás de
 * `disenoNuevoGuard`. Mi caja (sesión solo-profesor) no pasa por aquí (decisión 260).
 */
@Component({
  selector: 'app-caja-sede-panel',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    SearchableSelectDirective,
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
  ],
  templateUrl: './caja-sede.component.html',
  styleUrls: ['./caja-sede.component.scss'],
})
export class CajaSedePanelComponent implements OnInit, OnDestroy {
  private readonly caja = inject(CajaService);
  private readonly auth = inject(AuthenticationService);
  private readonly endpoints = inject(EndpointsService);
  private readonly solicitudes = inject(SolicitudesService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly temaActual = inject(AcentoEscuelaActual);
  private readonly destroy$ = new Subject<void>();
  /** Cada recarga completa (cambio de sede, entrega recibida) invalida la respuesta de pendientes en vuelo. */
  private readonly recarga$ = new Subject<void>();
  /** Resumen y movimientos por sede y periodo: con `switchMap` solo se aplica la última consulta, como hoy. */
  private readonly consultaPeriodo$ = new Subject<{ sede: number; rango: PeriodoCaja }>();
  /** Igual para el historial de entregas, por sede y página. */
  private readonly consultaEntregas$ = new Subject<{ sede: number; pagina: number }>();
  private permisos?: Subscription;
  private destruido = false;

  readonly pestanas = PESTANAS;
  readonly etiquetaPestana = etiquetaPestana;
  readonly tiposMovimiento = TIPOS_MOVIMIENTO;
  readonly etiquetaTipo = etiquetaTipoMovimiento;
  readonly eur = eur;
  readonly numero = numero;
  readonly plural = plural;
  readonly firmado = firmado;
  readonly tonoImporte = tonoImporte;
  readonly fechaHora = fechaHora;
  readonly fechaDia = fechaDia;
  readonly cuadre = cuadre;
  readonly diferenciaVista = diferenciaVista;
  readonly textoMovimientoDeCobro = textoMovimientoDeCobro;
  readonly nombreAlumnoCobro = nombreAlumnoCobro;
  readonly avisoCuadre = avisoCuadre;
  readonly textoMostrando = textoMostrando;
  /** La lista flotante del buscador de alumnos va al `body`: lleva copiado el tema de la escuela (8b-C). */
  readonly variablesDesplegable = computed(() => variablesDelTema(this.temaActual.aplicado()));

  esPropietario = false;
  pestana: PestanaCaja = 'recibir';

  sedes: SedeCaja[] = [];
  sedeId: number | null = null;
  cargandoSedes = true;
  errorSedes: ErrorDatos | null = null;

  readonly periodos: OpcionPeriodo[] = construirPeriodos(new Date());
  periodo: string = this.periodos[0].valor;

  /** `null` hasta la primera lectura de la sede elegida: la pestaña enseña su esqueleto. */
  resumen: ResumenCajaSede | null = null;
  pendientes: PendienteCobrador[] | null = null;
  entregas: EntregaCaja[] | null = null;
  movimientos: MovimientoCaja[] | null = null;
  totalEntregas: number | null = null;
  paginaEntregas = 1;
  totalPaginasEntregas = 0;

  cargandoResumen = false;
  cargandoPendientes = false;
  cargandoEntregas = false;
  errorDatos: ErrorDatos | null = null;
  /** Falló la última lectura de pendientes o de entregas: sin datos, la pestaña no enseña esqueleto bajo el error. */
  errorPendientes = false;
  errorEntregas = false;

  /** Nombres de alumnos para la tabla de movimientos y el selector del formulario (se cargan solo si hacen falta). */
  alumnos: Array<{ id: number; nombre: string }> = [];
  private nombresAlumnos = new Map<number, string>();
  private alumnosPedidos = false;

  formEntrega: FormEntrega | null = null;
  formMovimiento: FormMovimiento | null = null;

  ngOnInit(): void {
    this.resolverPermisos();
    this.escucharConsultasDePeriodo();
    this.escucharConsultasDeEntregas();
    this.cargarSedes();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.permisos?.unsubscribe();
    this.recarga$.next();
    this.recarga$.complete();
    this.destroy$.next();
    this.destroy$.complete();
  }

  get sedeActual(): SedeCaja | undefined {
    return this.sedes.find((s) => s.id === this.sedeId);
  }

  get periodoActual(): OpcionPeriodo {
    return this.periodos.find((p) => p.valor === this.periodo) ?? this.periodos[0];
  }

  get cifras(): CifraResumen[] {
    return cifrasResumen(this.resumen, this.periodoActual.etiqueta);
  }

  /** Con un envío en vuelo no se cambia de sede ni de periodo: lo que se envía es de la sede de la pantalla. */
  get enVuelo(): boolean {
    return !!this.formEntrega?.enviando || !!this.formMovimiento?.enviando;
  }

  get paginas(): (number | null)[] {
    return paginasVisibles(this.paginaEntregas, this.totalPaginasEntregas);
  }

  private get rango(): PeriodoCaja {
    return periodoDeMes(this.periodoActual.ano, this.periodoActual.mes);
  }

  // ------------------------------------------------------------------ pestañas

  elegirPestana(pestana: PestanaCaja): void {
    this.pestana = pestana;
  }

  /** Flechas (con vuelta), Inicio y Fin; el foco sigue a la pestaña elegida. */
  teclaPestanas(evento: KeyboardEvent): void {
    const i = PESTANAS.indexOf(this.pestana);
    let siguiente: number | null = null;
    if (evento.key === 'ArrowRight') {
      siguiente = (i + 1) % PESTANAS.length;
    } else if (evento.key === 'ArrowLeft') {
      siguiente = (i - 1 + PESTANAS.length) % PESTANAS.length;
    } else if (evento.key === 'Home') {
      siguiente = 0;
    } else if (evento.key === 'End') {
      siguiente = PESTANAS.length - 1;
    }
    if (siguiente === null) {
      return;
    }
    evento.preventDefault();
    const destino = PESTANAS[siguiente];
    this.elegirPestana(destino);
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`#caja-pestana-${destino}`)?.focus();
  }

  numeroDe(pestana: PestanaCaja): number | null {
    return numeroPestana(pestana, { pendientes: this.pendientes, totalEntregas: this.totalEntregas, movimientos: this.movimientos });
  }

  // ------------------------------------------------------------------ sede y periodo

  cargarSedes(): void {
    this.cargandoSedes = true;
    this.errorSedes = null;
    this.caja
      .sedesGestionables()
      .pipe(
        finalize(() => (this.cargandoSedes = false)),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (sedes) => {
          this.sedes = sedes;
          const guardada = this.leerSedeGuardada();
          this.sedeId = sedes.find((s) => s.id === guardada)?.id ?? sedes[0]?.id ?? null;
          if (this.sedeId !== null) {
            this.recargarTodo();
          }
        },
        error: (error) => {
          this.errorSedes = { mensaje: mensajeDeError(error) };
        },
      });
  }

  /**
   * Otra sede: se guarda su id (como hoy), se cierran los formularios abiertos sin enviar y se vacía lo de la anterior
   * mientras llega lo de la nueva, para no recibir ni anular sobre lo que ya no es de la sede elegida.
   */
  elegirSede(valor: string): void {
    const id = Number(valor);
    if (this.enVuelo || !this.sedes.some((s) => s.id === id) || id === this.sedeId) {
      return;
    }
    this.sedeId = id;
    this.guardarSede();
    this.formEntrega = null;
    this.formMovimiento = null;
    this.resumen = null;
    this.pendientes = null;
    this.entregas = null;
    this.movimientos = null;
    this.totalEntregas = null;
    this.totalPaginasEntregas = 0;
    this.paginaEntregas = 1;
    this.recargarTodo();
  }

  elegirPeriodo(valor: string): void {
    if (this.enVuelo || !this.periodos.some((p) => p.valor === valor)) {
      return;
    }
    this.periodo = valor;
    this.cargarPeriodo();
  }

  private leerSedeGuardada(): number | null {
    try {
      const valor = globalThis.localStorage?.getItem(CLAVE_ULTIMA_SEDE);
      const id = valor === null || valor === undefined ? NaN : Number(valor);
      return Number.isInteger(id) ? id : null;
    } catch {
      return null;
    }
  }

  private guardarSede(): void {
    if (this.sedeId === null) {
      return;
    }
    try {
      globalThis.localStorage?.setItem(CLAVE_ULTIMA_SEDE, String(this.sedeId));
    } catch {
      // Sin almacenamiento la pantalla funciona igual.
    }
  }

  // ------------------------------------------------------------------ lectura

  recargarTodo(): void {
    if (this.destruido) {
      return;
    }
    this.recarga$.next();
    this.errorDatos = null;
    this.errorPendientes = false;
    this.errorEntregas = false;
    this.cargarPeriodo();
    this.cargarPendientes();
    this.cargarEntregas();
  }

  private cargarPeriodo(): void {
    const sede = this.sedeId;
    if (sede === null || this.destruido) {
      return;
    }
    this.cargandoResumen = true;
    this.consultaPeriodo$.next({ sede, rango: this.rango });
  }

  private escucharConsultasDePeriodo(): void {
    const capturar = <T>(peticion: Observable<T>) =>
      peticion.pipe(
        map((valor) => ({ valor, error: null as unknown })),
        catchError((error: unknown) => of({ valor: null as T | null, error }))
      );
    this.consultaPeriodo$
      .pipe(
        switchMap(({ sede, rango }) =>
          forkJoin({
            resumen: capturar(this.caja.resumen(sede, rango)),
            movimientos: capturar(this.caja.movimientos(sede, rango)),
          })
        ),
        takeUntil(this.destroy$)
      )
      .subscribe(({ resumen, movimientos }) => {
        this.cargandoResumen = false;
        if (resumen.error) {
          this.fallo(resumen.error);
        } else if (resumen.valor) {
          this.resumen = resumen.valor;
        }
        if (movimientos.error) {
          this.fallo(movimientos.error);
        } else if (movimientos.valor) {
          this.movimientos = movimientos.valor;
          if (movimientos.valor.some((m) => m.alumnoId !== null)) {
            this.cargarAlumnos();
          }
        }
        this.cdr.markForCheck();
      });
  }

  private cargarPendientes(): void {
    const sede = this.sedeId;
    if (sede === null) {
      return;
    }
    this.cargandoPendientes = true;
    this.errorPendientes = false;
    this.caja
      .pendienteEntrega(sede)
      .pipe(
        finalize(() => (this.cargandoPendientes = false)),
        takeUntil(this.recarga$)
      )
      .subscribe({
        next: (pendientes) => {
          if (sede !== this.sedeId) {
            return;
          }
          this.pendientes = pendientes;
          this.revisarEntregaAbierta(pendientes);
          this.cdr.markForCheck();
        },
        error: (error) => (this.errorPendientes = this.fallo(error)),
      });
  }

  /**
   * «Recibir entrega» abierto (y sin envío en vuelo) cuando llegan pendientes nuevos (anular una entrega, «Reintentar»,
   * un 409): si el profesor ya no está o sus cobros cambian (ids o importes), se cierra sin enviar y se avisa. Así lo
   * marcado, «Debería entregar» y el envío nunca salen de unos cobros distintos de los que se pintan.
   */
  private revisarEntregaAbierta(pendientes: PendienteCobrador[]): void {
    const f = this.formEntrega;
    if (!f || f.enviando || f.sedeId !== this.sedeId) {
      return;
    }
    const ahora = pendientes.find((p) => p.usuarioId === f.profesor.usuarioId);
    if (ahora && mismosCobros(f.profesor.cobros, ahora.cobros)) {
      return;
    }
    // El foco se devuelve si estaba en el formulario o se había perdido; si está en otra parte (otra pestaña), se queda.
    const activo = typeof document !== 'undefined' ? document.activeElement : null;
    const seccion = this.host.nativeElement.querySelector(`#caja-entrega-${f.profesor.usuarioId}`);
    const devolver = !activo || activo === document.body || !!seccion?.contains(activo);
    this.formEntrega = null;
    if (devolver) {
      const fila = `.caja__profesor[data-usuario="${f.profesor.usuarioId}"] .caja__recibir`;
      this.devolverFoco(null, ...(ahora ? [fila] : []), '#caja-pestana-recibir');
    }
    this.avisos.info(`Los cobros pendientes de ${f.profesor.nombre} han cambiado. Vuelve a abrir «Recibir entrega».`);
  }

  private cargarEntregas(): void {
    const sede = this.sedeId;
    if (sede === null || this.destruido) {
      return;
    }
    this.cargandoEntregas = true;
    this.errorEntregas = false;
    this.consultaEntregas$.next({ sede, pagina: this.paginaEntregas });
  }

  private escucharConsultasDeEntregas(): void {
    this.consultaEntregas$
      .pipe(
        switchMap(({ sede, pagina }) =>
          this.caja.entregas(sede, pagina).pipe(
            map((valor) => ({ valor, error: null as unknown })),
            catchError((error: unknown) => of({ valor: null, error }))
          )
        ),
        takeUntil(this.destroy$)
      )
      .subscribe(({ valor, error }) => {
        this.cargandoEntregas = false;
        if (error) {
          this.errorEntregas = this.fallo(error);
        } else if (valor) {
          this.entregas = valor.content;
          this.totalPaginasEntregas = valor.totalPages ?? 0;
          this.totalEntregas = valor.totalElements ?? valor.content.length;
        }
        this.cdr.markForCheck();
      });
  }

  irAPagina(pagina: number): void {
    if (pagina === this.paginaEntregas || pagina < 1 || pagina > this.totalPaginasEntregas) {
      return;
    }
    this.paginaEntregas = pagina;
    this.cargarEntregas();
  }

  private cargarAlumnos(): void {
    if (this.alumnosPedidos) {
      return;
    }
    this.alumnosPedidos = true;
    this.endpoints
      .obtenerTodosLosAlumnosSinPaginar(true)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (lista) => {
          this.alumnos = (lista ?? [])
            .filter((a) => a?.id)
            .map((a) => ({ id: Number(a.id), nombre: `${a.nombre ?? ''} ${a.apellidos ?? ''}`.trim() || `Alumno ${a.id}` }))
            .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
          this.nombresAlumnos = new Map(this.alumnos.map((a) => [a.id, a.nombre]));
          this.cdr.markForCheck();
        },
        error: () => {
          this.alumnosPedidos = false;
        },
      });
  }

  /** Como hoy: un 401 lo atiende la sesión; el resto, el aviso de la página con el `mensaje` si lo trae. Dice si lo enseña. */
  private fallo(error: unknown): boolean {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return false;
    }
    this.errorDatos = { mensaje: mensajeDeError(error) };
    this.cdr.markForCheck();
    return true;
  }

  // ------------------------------------------------------------------ presentación

  nombreAlumno(alumnoId: number | null): string {
    if (alumnoId === null || alumnoId === undefined) {
      return '—';
    }
    return this.nombresAlumnos.get(alumnoId) ?? '—';
  }

  textoDesde(profesor: PendienteCobrador): string {
    return textoDesde(profesor);
  }

  avisoEfectivo(profesor: PendienteCobrador): boolean {
    return avisoEfectivo(profesor);
  }

  correcciones(entrega: EntregaCaja): MovimientoDeEntrega[] {
    return entrega.movimientosDeCobro ?? [];
  }

  accionEntrega(entrega: EntregaCaja): AccionAnular | null {
    return accionEntrega(entrega, this.esPropietario);
  }

  accionMovimiento(movimiento: MovimientoCaja): AccionAnular | null {
    return accionMovimiento(movimiento, this.esPropietario);
  }

  // ------------------------------------------------------------------ «Recibir entrega», bajo el profesor

  entregaAbierta(profesor: PendienteCobrador): boolean {
    return this.formEntrega?.profesor.usuarioId === profesor.usuarioId;
  }

  /** Abre bajo el profesor (cerrando el de otro, si no se está enviando) o lo cierra si ya estaba abierto. */
  alternarEntrega(profesor: PendienteCobrador, evento?: Event): void {
    if (this.formEntrega?.enviando) {
      return;
    }
    if (this.entregaAbierta(profesor)) {
      this.cerrarEntrega();
      return;
    }
    this.abrirEntrega(profesor, evento);
  }

  /** Como hoy: todos los cobros marcados y lo contado prefijado con lo declarado. El foco entra al formulario. */
  abrirEntrega(profesor: PendienteCobrador, evento?: Event): void {
    const sede = this.sedeId;
    if (sede === null || this.formEntrega?.enviando) {
      return;
    }
    const seleccionados = new Set(profesor.cobros.map((c) => c.cobroId));
    const declarado = calcularEntrega(profesor.cobros, seleccionados, '0').declarado;
    this.formEntrega = {
      profesor,
      sedeId: sede,
      seleccionados,
      contado: importeParaCampo(declarado),
      contadoEditado: false,
      nota: '',
      enviando: false,
      error: null,
      disparador: disparadorDe(evento),
    };
    this.cdr.detectChanges();
    this.host.nativeElement
      .querySelector<HTMLElement>(`#caja-entrega-${profesor.usuarioId} input:not(:disabled), #caja-entrega-${profesor.usuarioId} textarea`)
      ?.focus();
  }

  get calculoEntrega(): CalculoEntrega | null {
    const f = this.formEntrega;
    return f ? calcularEntrega(f.profesor.cobros, f.seleccionados, f.contado) : null;
  }

  get estadoSeleccionCobros(): EstadoSeleccion {
    const f = this.formEntrega;
    return f ? estadoSeleccion(f.profesor.cobros.length, f.seleccionados.size) : 'ninguno';
  }

  alternarCobro(cobroId: number, marcado: boolean): void {
    const f = this.formEntrega;
    if (!f || f.enviando) {
      return;
    }
    const seleccionados = new Set(f.seleccionados);
    if (marcado) {
      seleccionados.add(cobroId);
    } else {
      seleccionados.delete(cobroId);
    }
    f.seleccionados = seleccionados;
    this.sincronizarContado(f);
  }

  alternarTodos(marcado: boolean): void {
    const f = this.formEntrega;
    if (!f || f.enviando) {
      return;
    }
    f.seleccionados = marcado ? new Set(f.profesor.cobros.map((c) => c.cobroId)) : new Set();
    this.sincronizarContado(f);
  }

  /** Mientras no se haya escrito a mano, lo contado sigue a lo declarado (como hoy). */
  private sincronizarContado(f: FormEntrega): void {
    if (!f.contadoEditado) {
      f.contado = importeParaCampo(calcularEntrega(f.profesor.cobros, f.seleccionados, '0').declarado);
    }
  }

  onContadoInput(valor: string): void {
    if (this.formEntrega && !this.formEntrega.enviando) {
      this.formEntrega.contado = valor;
      this.formEntrega.contadoEditado = true;
    }
  }

  onNotaEntrega(valor: string): void {
    if (this.formEntrega && !this.formEntrega.enviando) {
      this.formEntrega.nota = valor;
    }
  }

  /** La regla de hoy: algún cobro marcado, un importe válido y la nota si no cuadra; nunca con un envío en vuelo. */
  puedeRecibir(): boolean {
    const f = this.formEntrega;
    const calculo = this.calculoEntrega;
    if (!f || !calculo || f.enviando || f.seleccionados.size === 0 || calculo.contado === null) {
      return false;
    }
    return !calculo.notaObligatoria || f.nota.trim().length > 0;
  }

  /** `POST /caja/sedes/{id}/entregas` con el cuerpo de hoy: quién entrega, los cobros marcados, lo contado y la nota. */
  recibirEntrega(): void {
    const f = this.formEntrega;
    const calculo = this.calculoEntrega;
    if (!f || !calculo || !this.puedeRecibir() || calculo.contado === null || f.sedeId !== this.sedeId || this.cargandoPendientes) {
      return;
    }
    f.enviando = true;
    f.error = null;
    const nota = f.nota.trim();
    this.caja
      .registrarEntrega(f.sedeId, {
        entregadoPorUsuarioId: f.profesor.usuarioId,
        cobroIds: f.profesor.cobros.filter((c) => f.seleccionados.has(c.cobroId)).map((c) => c.cobroId),
        importeContado: calculo.contado,
        nota: nota || null,
      })
      .subscribe({
        next: () => {
          f.enviando = false;
          this.avisos.exito(`Entrega de ${f.profesor.nombre} recibida`);
          if (this.destruido) {
            return;
          }
          this.cerrarEntrega();
          this.paginaEntregas = 1;
          this.recargarTodo();
        },
        error: (error) => {
          f.enviando = false;
          if (error instanceof HttpErrorResponse && error.status === 409) {
            const mensaje = mensajeDeError(error);
            this.avisos.error(`La entrega ha cambiado. ${mensaje ? `${mensaje} ` : ''}${TEXTO_ENTREGA_CAMBIADA}`);
            if (!this.destruido) {
              this.cerrarEntrega();
              this.recargarTodo();
            }
            return;
          }
          f.error = mensajeDeError(error) ?? 'No se ha podido registrar la entrega. Inténtalo de nuevo.';
          this.cdr.markForCheck();
        },
      });
  }

  cerrarEntrega(): void {
    const f = this.formEntrega;
    if (!f || f.enviando) {
      return;
    }
    this.formEntrega = null;
    this.devolverFoco(f.disparador, '#caja-pestana-recibir');
  }

  // ------------------------------------------------------------------ «Apuntar movimiento», en su pestaña

  alternarMovimiento(evento?: Event): void {
    if (this.formMovimiento) {
      this.cerrarMovimiento();
      return;
    }
    this.abrirMovimiento(evento);
  }

  /** Como hoy: abre con «Devolución», sin importe, alumno ni nota, y pide los alumnos si aún no están. */
  abrirMovimiento(evento?: Event): void {
    const sede = this.sedeId;
    if (sede === null || this.formMovimiento?.enviando) {
      return;
    }
    this.cargarAlumnos();
    this.formMovimiento = {
      sedeId: sede,
      tipo: 'DEVOLUCION',
      importe: '',
      alumnoId: null,
      nota: '',
      enviando: false,
      error: null,
      disparador: disparadorDe(evento),
    };
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>('#caja-mov-tipo')?.focus();
  }

  get reglaFormMovimiento(): ReglaMovimiento {
    return reglaMovimiento(this.formMovimiento?.tipo ?? 'OTRO');
  }

  /** Al cambiar de tipo, un tipo sin alumno lo borra (como hoy). Solo los cinco tipos de hoy. */
  cambiarTipo(valor: string): void {
    const f = this.formMovimiento;
    const tipo = TIPOS_MOVIMIENTO.find((t) => t.valor === valor)?.valor;
    if (!f || f.enviando || !tipo) {
      return;
    }
    f.tipo = tipo;
    if (this.reglaFormMovimiento.alumno === 'oculto') {
      f.alumnoId = null;
    }
  }

  /** El buscador de alumnos de hoy: vacío es «sin alumno»; si no, uno de la lista. */
  elegirAlumno(valor: string): void {
    const f = this.formMovimiento;
    if (!f || f.enviando) {
      return;
    }
    const id = valor === '' ? null : Number(valor);
    f.alumnoId = id !== null && this.alumnos.some((a) => a.id === id) ? id : null;
  }

  onImporteMovimiento(valor: string): void {
    if (this.formMovimiento && !this.formMovimiento.enviando) {
      this.formMovimiento.importe = valor;
    }
  }

  onNotaMovimiento(valor: string): void {
    if (this.formMovimiento && !this.formMovimiento.enviando) {
      this.formMovimiento.nota = valor;
    }
  }

  get importeMovimiento(): number | null {
    const f = this.formMovimiento;
    return f ? importeConSigno(f.tipo, f.importe) : null;
  }

  /** Hay algo escrito en el importe y no es válido: se avisa en línea y no se envía. */
  get importeMovimientoNoValido(): boolean {
    const f = this.formMovimiento;
    return !!f && f.importe.trim() !== '' && this.importeMovimiento === null;
  }

  puedeApuntar(): boolean {
    const f = this.formMovimiento;
    if (!f || f.enviando || this.importeMovimiento === null || !f.nota.trim()) {
      return false;
    }
    return this.reglaFormMovimiento.alumno !== 'obligatorio' || f.alumnoId !== null;
  }

  /** `POST /caja/sedes/{id}/movimientos` con el cuerpo de hoy; el alumno va nulo en los tipos que no lo llevan. */
  apuntarMovimiento(): void {
    const f = this.formMovimiento;
    const importe = this.importeMovimiento;
    if (!f || importe === null || !this.puedeApuntar() || f.sedeId !== this.sedeId) {
      return;
    }
    f.enviando = true;
    f.error = null;
    this.caja
      .crearMovimiento(f.sedeId, {
        tipo: f.tipo,
        importe,
        alumnoId: this.reglaFormMovimiento.alumno === 'oculto' ? null : f.alumnoId,
        nota: f.nota.trim(),
      })
      .subscribe({
        next: () => {
          f.enviando = false;
          this.avisos.exito('Movimiento apuntado');
          if (this.destruido) {
            return;
          }
          this.cerrarMovimiento();
          this.cargarPeriodo();
        },
        error: (error) => {
          f.enviando = false;
          f.error = mensajeDeError(error) ?? 'No se ha podido apuntar el movimiento. Inténtalo de nuevo.';
          this.cdr.markForCheck();
        },
      });
  }

  cerrarMovimiento(): void {
    const f = this.formMovimiento;
    if (!f || f.enviando) {
      return;
    }
    this.formMovimiento = null;
    this.devolverFoco(f.disparador, '#caja-apuntar');
  }

  // ------------------------------------------------------------------ anular (propietario)

  /** Como hoy: destructivo, motivo obligatorio (500) recortado; sus cobros vuelven a pendientes y se relee todo. */
  async anularEntrega(entrega: EntregaCaja): Promise<void> {
    if (this.accionEntrega(entrega) !== 'anular') {
      return;
    }
    const r = await this.confirmarAnular('Anular entrega', mensajeAnularEntrega(entrega), 'anular-entrega', (motivo) =>
      this.caja.anularEntrega(entrega.id, motivo)
    );
    if (r === 'error') {
      return;
    }
    if (r === 'hecho') {
      this.avisos.exito('Entrega anulada');
      this.recargarTodo();
    }
  }

  /** Ídem con un movimiento suelto; se relee el periodo, como hoy. */
  async anularMovimiento(movimiento: MovimientoCaja): Promise<void> {
    if (this.accionMovimiento(movimiento) !== 'anular') {
      return;
    }
    const r = await this.confirmarAnular('Anular movimiento', mensajeAnularMovimiento(movimiento), 'anular-movimiento', (motivo) =>
      this.caja.anularMovimiento(movimiento.id, motivo)
    );
    if (r === 'hecho') {
      this.avisos.exito('Movimiento anulado');
      this.cargarPeriodo();
    }
  }

  /**
   * El diálogo de hoy (`pedirMotivo`): motivo obligatorio, «Anular» en peligro y «Volver». Con la petición en vuelo el
   * diálogo sigue abierto con el botón ocupado: sin doble envío. Un error avisa con el `mensaje`; un 409 relee todo.
   */
  private async confirmarAnular(
    titulo: string,
    mensaje: string,
    nombre: string,
    ejecutar: (motivo: string) => Observable<unknown>
  ): Promise<'hecho' | 'cancelado' | 'error'> {
    const campo = 'caja-motivo';
    let motivo = '';
    const r = await this.confirmacion.confirmar({
      titulo,
      mensaje,
      campos: [
        {
          id: campo,
          tipo: 'textarea',
          etiqueta: 'Motivo',
          placeholder: 'Por qué se anula',
          atributos: { maxlength: String(MAX_MOTIVO), 'aria-label': 'Motivo de la anulación' },
        },
      ],
      accion: 'Anular',
      accionOcupada: 'Anulando…',
      cancelar: 'Volver',
      destructivo: true,
      nombre,
      comprobar: (valores) => {
        motivo = String(valores[campo] ?? '').trim();
        return motivo ? null : { campo, mensaje: 'Indica el motivo de la anulación' };
      },
      ejecutar: () => ejecutar(motivo),
    });
    if (r.error) {
      this.errorDeAccion(r.error, nombre === 'anular-entrega' ? 'No se ha podido anular la entrega.' : 'No se ha podido anular el movimiento.');
      return 'error';
    }
    return r.confirmado ? 'hecho' : 'cancelado';
  }

  private errorDeAccion(error: unknown, porDefecto: string): void {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.recargarTodo();
    }
    this.avisos.error(mensajeDeError(error) ?? porDefecto);
  }

  // ------------------------------------------------------------------ pedir anulación (gestión, D6c-8)

  pedirAnularEntrega(entrega: EntregaCaja): Promise<void> {
    if (this.accionEntrega(entrega) !== 'pedir') {
      return Promise.resolve();
    }
    return this.pedirAlPropietario(
      'Pedir al propietario que anule esta entrega',
      datosPedirEntrega(entrega),
      'Mientras tanto la entrega sigue contando en la caja.',
      { tipo: 'ANULAR_ENTREGA', entregaId: entrega.id },
      'pedir-anular-entrega'
    );
  }

  pedirAnularMovimiento(movimiento: MovimientoCaja): Promise<void> {
    if (this.accionMovimiento(movimiento) !== 'pedir') {
      return Promise.resolve();
    }
    return this.pedirAlPropietario(
      'Pedir al propietario que anule este movimiento',
      datosPedirMovimiento(movimiento),
      'Mientras tanto el movimiento sigue contando en la caja.',
      { tipo: 'ANULAR_MOVIMIENTO', movimientoId: movimiento.id },
      'pedir-anular-movimiento'
    );
  }

  /**
   * `pedirAlPropietario` de hoy con el diálogo del panel: los datos del recurso, la nota de que mientras tanto nada
   * cambia y el motivo obligatorio (500); el mismo `POST /solicitudes` con `{...peticion, motivo}`, en vuelo con el
   * diálogo ocupado. Un error (409 «ya hay una pendiente») avisa con el `mensaje` del servidor.
   */
  private async pedirAlPropietario(
    titulo: string,
    datos: Array<[string, string]>,
    nota: string,
    peticion: Omit<CrearSolicitud, 'motivo'>,
    nombre: string
  ): Promise<void> {
    const campo = 'caja-motivo-solicitud';
    let motivo = '';
    const elementos: ParteMensaje[][] = datos
      .filter(([, valor]) => valor?.trim())
      .map(([etiqueta, valor]) => [{ fuerte: etiqueta }, `: ${valor}`]);
    const r = await this.confirmacion.confirmar({
      titulo,
      bloques: [
        { tipo: 'lista', elementos },
        { tipo: 'parrafo', partes: [nota] },
      ],
      campos: [
        {
          id: campo,
          tipo: 'textarea',
          etiqueta: 'Motivo',
          placeholder: 'Qué ha pasado y por qué hay que cambiarlo',
          atributos: { maxlength: String(MAX_MOTIVO), 'aria-label': 'Motivo de la solicitud' },
        },
      ],
      accion: 'Enviar solicitud',
      accionOcupada: 'Enviando…',
      cancelar: 'Cancelar',
      nombre,
      comprobar: (valores) => {
        motivo = String(valores[campo] ?? '').trim();
        return motivo ? null : { campo, mensaje: 'Escribe el motivo: el propietario lo verá al decidir' };
      },
      ejecutar: () => this.solicitudes.crear({ ...peticion, motivo }),
    });
    if (r.error) {
      this.avisos.error(mensajeDeError(r.error) ?? 'No se ha podido enviar la solicitud.');
    } else if (r.confirmado) {
      this.avisos.exito('Solicitud enviada al propietario');
    }
  }

  // ------------------------------------------------------------------ foco y permisos

  /** Tras cerrar un formulario, el foco vuelve a quien lo abrió o, si ya no está (la fila se releyó), a la primera alternativa que haya. */
  private devolverFoco(disparador: HTMLElement | null, ...alternativas: string[]): void {
    if (this.destruido) {
      return;
    }
    setTimeout(() => {
      const raiz = this.host.nativeElement;
      const destino = disparador?.isConnected
        ? disparador
        : alternativas.map((a) => raiz.querySelector<HTMLElement>(a)).find((e): e is HTMLElement => !!e);
      destino?.focus();
    });
  }

  /** Propietario = `ROLE_ADMIN`, como hoy; si los roles no se pueden leer, falla cerrado (pide en vez de anular). */
  private resolverPermisos(): void {
    if (this.auth.rolesEstanCargados()) {
      this.esPropietario = this.auth.tieneRolAdmin();
      return;
    }
    this.permisos = this.auth.obtenerRoles().subscribe({
      next: () => {
        this.esPropietario = this.auth.tieneRolAdmin();
        this.cdr.markForCheck();
      },
      error: () => {
        this.esPropietario = false;
        this.cdr.markForCheck();
      },
    });
  }
}

/** Los mismos cobros (ids e importes), sin mirar el orden. */
function mismosCobros(antes: CobroCaja[], ahora: CobroCaja[]): boolean {
  if (antes.length !== ahora.length) {
    return false;
  }
  const importes = new Map(antes.map((c) => [c.cobroId, c.importe]));
  return importes.size === ahora.length && ahora.every((c) => importes.has(c.cobroId) && importes.get(c.cobroId) === c.importe);
}

function disparadorDe(evento?: Event): HTMLElement | null {
  const destino = evento?.currentTarget ?? (typeof document !== 'undefined' ? document.activeElement : null);
  return destino instanceof HTMLElement ? destino : null;
}
