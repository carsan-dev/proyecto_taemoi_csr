import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { Observable, Subject, forkJoin, of, takeUntil } from 'rxjs';
import { catchError, finalize, map, switchMap } from 'rxjs/operators';
import Swal from 'sweetalert2';

import {
  CobroCaja,
  EntregaCaja,
  MovimientoCaja,
  PendienteCobrador,
  ResumenCajaSede,
  SedeCaja,
  TipoMovimientoCaja,
} from '../../../interfaces/caja';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CajaService, PeriodoCaja, mensajeDeError, periodoDeMes } from '../../../servicios/endpoints/caja.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { SearchableSelectDirective } from '../../../directives/searchable-select.directive';
import {
  CalculoEntrega,
  DIAS_AVISO_EFECTIVO,
  EstadoSeleccion,
  TIPOS_MOVIMIENTO,
  calcularEntrega,
  claseImporte,
  diasDesde,
  estadoSeleccion,
  etiquetaTipoMovimiento,
  formatearEuros,
  importeConSigno,
  importeConSignoTexto,
  importeParaCampo,
  reglaMovimiento,
  textoRelativo,
} from './caja-calculos';

const MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];
const MESES_EN_SELECTOR = 24;

interface OpcionPeriodo {
  valor: string;
  etiqueta: string;
  ano: number;
  mes: number;
}

interface DialogoEntrega {
  profesor: PendienteCobrador;
  seleccionados: Set<number>;
  contado: string;
  contadoEditado: boolean;
  nota: string;
  enviando: boolean;
  error: string | null;
}

interface DialogoMovimiento {
  tipo: TipoMovimientoCaja;
  importe: string;
  alumnoId: number | null;
  nota: string;
  enviando: boolean;
  error: string | null;
}

/**
 * Caja de sede (pieza 6b-3): saldo, lo que los profesores tienen pendiente de entregar, las entregas recibidas y los
 * movimientos sueltos de una sede. Pensada también para el responsable de sede (el selector solo trae sus sedes).
 */
@Component({
  selector: 'app-caja-sede',
  standalone: true,
  imports: [CommonModule, FormsModule, A11yModule, PaginacionComponent, SearchableSelectDirective],
  templateUrl: './caja-sede.component.html',
  styleUrl: './caja-sede.component.scss',
})
export class CajaSedeComponent implements OnInit, OnDestroy {
  private readonly caja = inject(CajaService);
  private readonly auth = inject(AuthenticationService);
  private readonly endpoints = inject(EndpointsService);
  private readonly destroy$ = new Subject<void>();
  /** Cada recarga completa (cambio de sede, entrega recibida) invalida las respuestas de pendientes y entregas. */
  private recarga$ = new Subject<void>();
  /**
   * Consultas que dependen de sede y periodo (resumen y movimientos). Con `switchMap` solo se aplica la respuesta de
   * la última: si un mes anterior contesta más tarde (flechas en el selector), se descarta.
   */
  private readonly consultaPeriodo$ = new Subject<{ sede: number; rango: PeriodoCaja }>();
  /** Igual para el historial de entregas, por sede y página. */
  private readonly consultaEntregas$ = new Subject<{ sede: number; pagina: number }>();

  readonly euros = formatearEuros;
  readonly importeFirmado = importeConSignoTexto;
  readonly claseImporte = claseImporte;
  readonly etiquetaTipo = etiquetaTipoMovimiento;
  readonly tiposMovimiento = TIPOS_MOVIMIENTO;
  readonly diasAviso = DIAS_AVISO_EFECTIVO;

  esPropietario = false;
  sedes: SedeCaja[] = [];
  sedeId: number | null = null;
  cargandoSedes = true;
  errorSedes: string | null = null;

  readonly periodos: OpcionPeriodo[] = construirPeriodos(new Date());
  periodo: string = this.periodos[0].valor;

  resumen: ResumenCajaSede | null = null;
  pendientes: PendienteCobrador[] = [];
  entregas: EntregaCaja[] = [];
  movimientos: MovimientoCaja[] = [];
  paginaEntregas = 1;
  totalPaginasEntregas = 0;
  totalEntregas = 0;

  cargandoResumen = false;
  cargandoPendientes = false;
  cargandoEntregas = false;
  cargandoMovimientos = false;
  errorDatos: string | null = null;

  /** Nombres de alumnos para la tabla de movimientos y el selector del diálogo (se cargan solo si hacen falta). */
  alumnos: Array<{ id: number; nombre: string }> = [];
  private nombresAlumnos = new Map<number, string>();
  private alumnosPedidos = false;

  dialogoEntrega: DialogoEntrega | null = null;
  dialogoMovimiento: DialogoMovimiento | null = null;
  private focoAntesDelDialogo: HTMLElement | null = null;


  ngOnInit(): void {
    this.resolverPermisos();
    this.escucharConsultasDePeriodo();
    this.escucharConsultasDeEntregas();
    this.cargarSedes();
  }

  ngOnDestroy(): void {
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

  private get rango(): PeriodoCaja {
    return periodoDeMes(this.periodoActual.ano, this.periodoActual.mes);
  }

  // ------------------------------------------------------------------ carga

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
          this.sedeId = sedes[0]?.id ?? null;
          if (this.sedeId !== null) {
            this.recargarTodo();
          }
        },
        error: (error) => {
          this.errorSedes = mensajeDeError(error) ?? 'No se han podido cargar las sedes.';
        },
      });
  }

  onSedeChange(): void {
    this.paginaEntregas = 1;
    this.recargarTodo();
  }

  onPeriodoChange(): void {
    this.cargarPeriodo();
  }

  recargarTodo(): void {
    this.recarga$.next();
    this.errorDatos = null;
    this.cargarPeriodo();
    this.cargarPendientes();
    this.cargarEntregas();
  }

  /** Resumen y movimientos de la sede y el periodo actuales; los indicadores de carga siguen a la última consulta. */
  private cargarPeriodo(): void {
    const sede = this.sedeId;
    if (sede === null) {
      return;
    }
    this.cargandoResumen = true;
    this.cargandoMovimientos = true;
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
        this.cargandoMovimientos = false;
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
      });
  }

  private cargarPendientes(): void {
    const sede = this.sedeId;
    if (sede === null) {
      return;
    }
    this.cargandoPendientes = true;
    this.caja
      .pendienteEntrega(sede)
      .pipe(
        finalize(() => (this.cargandoPendientes = false)),
        takeUntil(this.recarga$)
      )
      .subscribe({
        next: (pendientes) => (this.pendientes = pendientes),
        error: (error) => this.fallo(error),
      });
  }

  cargarEntregas(): void {
    const sede = this.sedeId;
    if (sede === null) {
      return;
    }
    this.cargandoEntregas = true;
    this.consultaEntregas$.next({ sede, pagina: this.paginaEntregas });
  }

  /**
   * Historial de entregas por sede y página. Con `switchMap` solo se aplica la última consulta: una página anterior
   * que conteste tarde, o la de otra sede, se descarta; el indicador de carga sigue a la última.
   */
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
          this.fallo(error);
          return;
        }
        if (valor) {
          this.entregas = valor.content;
          this.totalPaginasEntregas = valor.totalPages ?? 0;
          this.totalEntregas = valor.totalElements ?? valor.content.length;
        }
      });
  }

  cambiarPaginaEntregas(pagina: number): void {
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
        },
        error: () => {
          this.alumnosPedidos = false;
        },
      });
  }

  private fallo(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return;
    }
    this.errorDatos = mensajeDeError(error) ?? 'No se han podido cargar los datos de la caja. Inténtalo de nuevo.';
  }

  // ------------------------------------------------------------------ presentación

  nombreAlumno(alumnoId: number | null): string {
    if (alumnoId === null || alumnoId === undefined) {
      return '—';
    }
    return this.nombresAlumnos.get(alumnoId) ?? '—';
  }

  nombreAlumnoCobro(cobro: CobroCaja): string {
    return `${cobro.alumnoNombre ?? ''} ${cobro.alumnoApellidos ?? ''}`.trim() || '—';
  }

  diasEnMano(profesor: PendienteCobrador): number | null {
    return diasDesde(profesor.cobroMasAntiguo);
  }

  textoDesde(profesor: PendienteCobrador): string {
    return textoRelativo(this.diasEnMano(profesor));
  }

  avisoEfectivo(profesor: PendienteCobrador): boolean {
    const dias = this.diasEnMano(profesor);
    return dias !== null && dias > DIAS_AVISO_EFECTIVO;
  }

  /** «cuadra», «falta 1,00 €», «sobra 0,50 €». */
  textoDiferencia(diferencia: number): string {
    if (Math.round(diferencia * 100) === 0) {
      return 'cuadra';
    }
    return diferencia < 0 ? `falta ${this.euros(-diferencia)}` : `sobra ${this.euros(diferencia)}`;
  }

  claseDiferencia(diferencia: number): string {
    const cent = Math.round(diferencia * 100);
    return cent === 0 ? 'chip-cuadra' : cent < 0 ? 'chip-falta' : 'chip-sobra';
  }

  entregaAnulada(entrega: EntregaCaja): boolean {
    return entrega.estado === 'ANULADA';
  }

  movimientoAnulado(movimiento: MovimientoCaja): boolean {
    return movimiento.estado === 'ANULADO';
  }

  /** El propietario anula movimientos sueltos; el automático de una entrega se anula anulando la entrega. */
  puedeAnularMovimiento(movimiento: MovimientoCaja): boolean {
    return this.esPropietario && !this.movimientoAnulado(movimiento) && movimiento.entregaCajaId === null;
  }

  puedeAnularEntrega(entrega: EntregaCaja): boolean {
    return this.esPropietario && !this.entregaAnulada(entrega);
  }

  claseDiferenciasResumen(): string {
    const cent = Math.round((this.resumen?.diferenciasEnPeriodo ?? 0) * 100);
    return cent < 0 ? 'valor-negativo' : cent > 0 ? 'valor-positivo' : '';
  }

  // ------------------------------------------------------------------ diálogo: recibir entrega

  abrirEntrega(profesor: PendienteCobrador, evento?: Event): void {
    this.recordarFoco(evento);
    const seleccionados = new Set(profesor.cobros.map((c) => c.cobroId));
    const declarado = calcularEntrega(profesor.cobros, seleccionados, '0').declarado;
    this.dialogoEntrega = {
      profesor,
      seleccionados,
      contado: importeParaCampo(declarado),
      contadoEditado: false,
      nota: '',
      enviando: false,
      error: null,
    };
  }

  get calculoEntrega(): CalculoEntrega | null {
    const d = this.dialogoEntrega;
    return d ? calcularEntrega(d.profesor.cobros, d.seleccionados, d.contado) : null;
  }

  alternarCobro(cobroId: number, marcado: boolean): void {
    const d = this.dialogoEntrega;
    if (!d) {
      return;
    }
    if (marcado) {
      d.seleccionados.add(cobroId);
    } else {
      d.seleccionados.delete(cobroId);
    }
    d.seleccionados = new Set(d.seleccionados);
    this.sincronizarContado(d);
  }

  /** Casilla «Seleccionar todos los cobros»: todos, ninguno o algunos (indeterminada). */
  get estadoSeleccionCobros(): EstadoSeleccion {
    const d = this.dialogoEntrega;
    return d ? estadoSeleccion(d.profesor.cobros.length, d.seleccionados.size) : 'ninguno';
  }

  alternarTodos(marcado: boolean): void {
    const d = this.dialogoEntrega;
    if (!d) {
      return;
    }
    d.seleccionados = marcado ? new Set(d.profesor.cobros.map((c) => c.cobroId)) : new Set();
    this.sincronizarContado(d);
  }

  /** Mientras no se haya escrito a mano, lo contado sigue a lo declarado. */
  private sincronizarContado(d: DialogoEntrega): void {
    if (!d.contadoEditado) {
      d.contado = importeParaCampo(calcularEntrega(d.profesor.cobros, d.seleccionados, '0').declarado);
    }
  }

  onContadoInput(valor: string): void {
    if (this.dialogoEntrega) {
      this.dialogoEntrega.contado = valor;
      this.dialogoEntrega.contadoEditado = true;
    }
  }

  puedeRecibir(): boolean {
    const d = this.dialogoEntrega;
    const calculo = this.calculoEntrega;
    if (!d || !calculo || d.enviando || d.seleccionados.size === 0 || calculo.contado === null) {
      return false;
    }
    return !calculo.notaObligatoria || d.nota.trim().length > 0;
  }

  recibirEntrega(): void {
    const d = this.dialogoEntrega;
    const calculo = this.calculoEntrega;
    const sede = this.sedeId;
    if (!d || !calculo || sede === null || !this.puedeRecibir() || calculo.contado === null) {
      return;
    }
    d.enviando = true;
    d.error = null;
    const nota = d.nota.trim();
    this.caja
      .registrarEntrega(sede, {
        entregadoPorUsuarioId: d.profesor.usuarioId,
        cobroIds: d.profesor.cobros.filter((c) => d.seleccionados.has(c.cobroId)).map((c) => c.cobroId),
        importeContado: calculo.contado,
        nota: nota || null,
      })
      .pipe(finalize(() => (d.enviando = false)))
      .subscribe({
        next: () => {
          d.enviando = false;
          this.cerrarEntrega();
          showSuccessToast(`Entrega de ${d.profesor.nombre} recibida`);
          this.paginaEntregas = 1;
          this.recargarTodo();
        },
        error: (error) => {
          d.enviando = false;
          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.cerrarEntrega();
            this.recargarTodo();
            Swal.fire({
              title: 'La entrega ha cambiado',
              text: 'Alguien ha registrado o cambiado estos cobros mientras tanto. Hemos recargado la caja: revisa lo pendiente y vuelve a recibir la entrega.',
              icon: 'warning',
              confirmButtonText: 'Entendido',
              confirmButtonColor: '#2b6cb0',
            });
            return;
          }
          d.error = mensajeDeError(error) ?? 'No se ha podido registrar la entrega. Inténtalo de nuevo.';
        },
      });
  }

  cerrarEntrega(): void {
    if (this.dialogoEntrega?.enviando) {
      return;
    }
    this.dialogoEntrega = null;
    this.restaurarFoco();
  }

  // ------------------------------------------------------------------ diálogo: apuntar movimiento

  abrirMovimiento(evento?: Event): void {
    this.recordarFoco(evento);
    this.cargarAlumnos();
    this.dialogoMovimiento = { tipo: 'DEVOLUCION', importe: '', alumnoId: null, nota: '', enviando: false, error: null };
  }

  get reglaDialogoMovimiento() {
    return reglaMovimiento(this.dialogoMovimiento?.tipo ?? 'OTRO');
  }

  onTipoMovimientoChange(): void {
    if (this.dialogoMovimiento && this.reglaDialogoMovimiento.alumno === 'oculto') {
      this.dialogoMovimiento.alumnoId = null;
    }
  }

  get importeMovimiento(): number | null {
    const d = this.dialogoMovimiento;
    return d ? importeConSigno(d.tipo, d.importe) : null;
  }

  /** Hay algo escrito en el importe y no es válido: se avisa en línea y no se envía. */
  get importeMovimientoNoValido(): boolean {
    const d = this.dialogoMovimiento;
    return !!d && d.importe.trim() !== '' && this.importeMovimiento === null;
  }

  puedeApuntar(): boolean {
    const d = this.dialogoMovimiento;
    if (!d || d.enviando || this.importeMovimiento === null || !d.nota.trim()) {
      return false;
    }
    return this.reglaDialogoMovimiento.alumno !== 'obligatorio' || d.alumnoId !== null;
  }

  apuntarMovimiento(): void {
    const d = this.dialogoMovimiento;
    const sede = this.sedeId;
    const importe = this.importeMovimiento;
    if (!d || sede === null || importe === null || !this.puedeApuntar()) {
      return;
    }
    d.enviando = true;
    d.error = null;
    this.caja
      .crearMovimiento(sede, {
        tipo: d.tipo,
        importe,
        alumnoId: this.reglaDialogoMovimiento.alumno === 'oculto' ? null : d.alumnoId,
        nota: d.nota.trim(),
      })
      .pipe(finalize(() => (d.enviando = false)))
      .subscribe({
        next: () => {
          d.enviando = false;
          this.cerrarMovimiento();
          showSuccessToast('Movimiento apuntado');
          this.cargarPeriodo();
        },
        error: (error) => {
          d.error = mensajeDeError(error) ?? 'No se ha podido apuntar el movimiento. Inténtalo de nuevo.';
        },
      });
  }

  cerrarMovimiento(): void {
    if (this.dialogoMovimiento?.enviando) {
      return;
    }
    this.dialogoMovimiento = null;
    this.restaurarFoco();
  }

  // ------------------------------------------------------------------ anular (solo propietario)

  anularEntrega(entrega: EntregaCaja): void {
    if (!this.puedeAnularEntrega(entrega)) {
      return;
    }
    this.pedirMotivo(
      'Anular entrega',
      `Entrega de ${entrega.entregadoPorNombre ?? 'un profesor'} por ${this.euros(entrega.importeContado)}. Sus cobros vuelven a estar pendientes de entregar.`
    ).then((motivo) => {
      if (motivo === null) {
        return;
      }
      this.caja.anularEntrega(entrega.id, motivo).subscribe({
        next: () => {
          showSuccessToast('Entrega anulada');
          this.recargarTodo();
        },
        error: (error) => this.errorDeAccion(error, 'No se ha podido anular la entrega.'),
      });
    });
  }

  anularMovimiento(movimiento: MovimientoCaja): void {
    if (!this.puedeAnularMovimiento(movimiento)) {
      return;
    }
    this.pedirMotivo(
      'Anular movimiento',
      `${this.etiquetaTipo(movimiento.tipo)} de ${this.euros(movimiento.importe)}. Deja de contar en la caja.`
    ).then((motivo) => {
      if (motivo === null) {
        return;
      }
      this.caja.anularMovimiento(movimiento.id, motivo).subscribe({
        next: () => {
          showSuccessToast('Movimiento anulado');
          this.cargarPeriodo();
        },
        error: (error) => this.errorDeAccion(error, 'No se ha podido anular el movimiento.'),
      });
    });
  }

  /** La API exige motivo para anular. Devuelve el motivo o `null` si se cancela. */
  private pedirMotivo(titulo: string, texto: string): Promise<string | null> {
    return Swal.fire({
      title: titulo,
      text: texto,
      icon: 'warning',
      input: 'textarea',
      inputLabel: 'Motivo',
      inputPlaceholder: 'Por qué se anula',
      inputAttributes: { maxlength: '500', 'aria-label': 'Motivo de la anulación' },
      inputValidator: (valor: string) => ((valor ?? '').trim() ? null : 'Indica el motivo de la anulación'),
      showCancelButton: true,
      confirmButtonText: 'Anular',
      cancelButtonText: 'Volver',
      confirmButtonColor: '#c53030',
    }).then((resultado) => (resultado.isConfirmed ? String(resultado.value ?? '').trim() : null));
  }

  private errorDeAccion(error: unknown, porDefecto: string): void {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.recargarTodo();
    }
    showErrorToast(mensajeDeError(error) ?? porDefecto);
  }

  // ------------------------------------------------------------------ teclado y foco

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.dialogoEntrega) {
      this.cerrarEntrega();
    } else if (this.dialogoMovimiento) {
      this.cerrarMovimiento();
    }
  }

  private recordarFoco(evento?: Event): void {
    const destino = evento?.currentTarget ?? (typeof document !== 'undefined' ? document.activeElement : null);
    this.focoAntesDelDialogo = destino instanceof HTMLElement ? destino : null;
  }

  private restaurarFoco(): void {
    const foco = this.focoAntesDelDialogo;
    this.focoAntesDelDialogo = null;
    if (foco && typeof foco.focus === 'function') {
      setTimeout(() => foco.focus());
    }
  }

  private resolverPermisos(): void {
    if (this.auth.rolesEstanCargados()) {
      this.esPropietario = this.auth.tieneRolAdmin();
      return;
    }
    this.auth
      .obtenerRoles()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => (this.esPropietario = this.auth.tieneRolAdmin()),
        error: () => (this.esPropietario = false),
      });
  }

}

/** Los últimos meses, del actual hacia atrás («Octubre 2026»). */
export function construirPeriodos(hoy: Date, cuantos: number = MESES_EN_SELECTOR): OpcionPeriodo[] {
  const periodos: OpcionPeriodo[] = [];
  for (let i = 0; i < cuantos; i++) {
    const fecha = new Date(hoy.getFullYear(), hoy.getMonth() - i, 1);
    const ano = fecha.getFullYear();
    const mes = fecha.getMonth() + 1;
    periodos.push({ valor: `${ano}-${String(mes).padStart(2, '0')}`, etiqueta: `${MESES[mes - 1]} ${ano}`, ano, mes });
  }
  return periodos;
}
