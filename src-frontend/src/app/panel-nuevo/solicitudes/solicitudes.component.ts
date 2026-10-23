import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription } from 'rxjs';

import { SegmentoSolicitudes, Solicitud } from '../../interfaces/solicitud';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { SolicitudesContadorService } from '../../servicios/endpoints/solicitudes-contador.service';
import { SolicitudesService } from '../../servicios/endpoints/solicitudes.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { ResultadoConfirmacion } from '../dialogo/dialogo.component';
import { eur } from '../formato/formato-es';
import {
  ACEPTAR,
  AVANCE,
  AccionSolicitud,
  DEVOLUCION_OTRA_SEDE,
  MAX_MOTIVO,
  SEGMENTOS,
  accionesDe,
  descripcion,
  devolucionDelProfesor,
  efectoRechazo,
  etiquetaRespuesta,
  etiquetaSegmento,
  fechaHora,
  numeroSegmento,
  pasosEncargo,
  pastillaCobro,
  pastillas,
  sobreQue,
  subtitulo,
  textoAccion,
  textoResolucion,
  vacio,
} from './solicitudes-vista';

interface EstadoSegmento {
  lista: Solicitud[] | null;
  cargando: boolean;
  /** Falló la última lectura; `mensaje`, el del servidor si lo trajo. */
  error: { mensaje: string | null } | null;
}

/** Texto del botón mientras su acción está en vuelo (el de la fila o el del diálogo). */
const OCUPADA: Record<AccionSolicitud, string> = {
  aceptar: 'Aceptando…',
  denegar: 'Denegando…',
  retirar: 'Retirando…',
  encargado: 'Marcando…',
  recibido: 'Marcando…',
  rechazar: 'Rechazando…',
  devolucion: 'Registrando…',
};

/** Las primarias de la fila: el verbo de aceptar y lo que hace avanzar un encargo o lo cierra. */
const PRINCIPALES: ReadonlySet<AccionSolicitud> = new Set(['aceptar', 'encargado', 'recibido', 'devolucion']);
/** Las que dicen que no: texto en peligro, como el diálogo rojo de hoy. */
const NEGATIVAS: ReadonlySet<AccionSolicitud> = new Set(['denegar', 'rechazar']);

/**
 * Solicitudes del panel nuevo (8b-3i, P1 «acciones en la fila»). La bandeja de hoy en el lenguaje de C: pestañas
 * Pendientes, Encargos en curso y Resueltas con su número; cada fila con quién la pidió, cuándo y la sede a la
 * izquierda, el tipo, el estado, sobre qué, el importe y el motivo como cita en el centro, y las acciones a la derecha.
 * Debajo de las pendientes, los encargos en curso con su recorrido (Pedido, Encargado, Recibido y Entregado).
 *
 * Las MISMAS peticiones, cuerpos, confirmaciones y criterios por rol que `SolicitudesBandejaComponent` (con
 * `SolicitudesService`, que ya lee y escribe con `ERROR_HANDLED_LOCALLY`), con el diálogo, los avisos y los estados de
 * carga del panel nuevo. Tras cada escritura (salga bien o mal) se releen los segmentos cargados y el contador de la
 * cabecera (`SolicitudesContadorService.refrescar`), como hoy. Nada va a la URL ni al almacenamiento del navegador. Va en
 * su propio chunk detrás de `disenoNuevoGuard`.
 */
@Component({
  selector: 'app-solicitudes-panel',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
  ],
  templateUrl: './solicitudes.component.html',
  styleUrls: ['./solicitudes.component.scss'],
})
export class SolicitudesPanelComponent implements OnInit, OnDestroy {
  private readonly solicitudes = inject(SolicitudesService);
  private readonly contador = inject(SolicitudesContadorService);
  private readonly auth = inject(AuthenticationService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly segmentos = SEGMENTOS;
  readonly etiquetaSegmento = etiquetaSegmento;
  readonly pastillas = pastillas;
  readonly pastillaCobro = pastillaCobro;
  readonly pasosEncargo = pasosEncargo;
  readonly sobreQue = sobreQue;
  readonly fechaHora = fechaHora;
  readonly textoResolucion = textoResolucion;
  readonly etiquetaRespuesta = etiquetaRespuesta;
  readonly devolucionDelProfesor = devolucionDelProfesor;
  readonly textoAccion = textoAccion;
  readonly eur = eur;

  segmento: SegmentoSolicitudes = 'PENDIENTES';
  esPropietario = false;

  readonly estado: Record<SegmentoSolicitudes, EstadoSegmento> = {
    PENDIENTES: { lista: null, cargando: false, error: null },
    ENCARGOS: { lista: null, cargando: false, error: null },
    RESUELTAS: { lista: null, cargando: false, error: null },
  };

  /** «Retirar» en vuelo (va sin diálogo): su botón ocupado y los demás de la fila deshabilitados, sin doble envío. */
  private readonly enCurso = new Map<number, AccionSolicitud>();
  private readonly lecturas: Partial<Record<SegmentoSolicitudes, Subscription>> = {};
  private permisos?: Subscription;
  private destruido = false;

  ngOnInit(): void {
    this.resolverPermisos();
    this.cargar('PENDIENTES');
    this.cargar('ENCARGOS');
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.permisos?.unsubscribe();
    for (const segmento of SEGMENTOS) {
      this.lecturas[segmento]?.unsubscribe();
    }
  }

  // ------------------------------------------------------------------ pestañas

  elegirSegmento(segmento: SegmentoSolicitudes): void {
    this.segmento = segmento;
    if (this.estado[segmento].lista === null && !this.estado[segmento].cargando) {
      this.cargar(segmento);
    }
  }

  /** Flechas (con vuelta), Inicio y Fin; el foco sigue a la pestaña elegida. */
  teclaPestanas(evento: KeyboardEvent): void {
    const i = SEGMENTOS.indexOf(this.segmento);
    let siguiente: number | null = null;
    if (evento.key === 'ArrowRight') {
      siguiente = (i + 1) % SEGMENTOS.length;
    } else if (evento.key === 'ArrowLeft') {
      siguiente = (i - 1 + SEGMENTOS.length) % SEGMENTOS.length;
    } else if (evento.key === 'Home') {
      siguiente = 0;
    } else if (evento.key === 'End') {
      siguiente = SEGMENTOS.length - 1;
    }
    if (siguiente === null) {
      return;
    }
    evento.preventDefault();
    const destino = SEGMENTOS[siguiente];
    this.elegirSegmento(destino);
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`#sol-pestana-${destino}`)?.focus();
  }

  numero(segmento: SegmentoSolicitudes): number | null {
    return numeroSegmento(segmento, this.estado[segmento].lista);
  }

  get subtitulo(): string {
    return subtitulo(this.esPropietario);
  }

  vacio(segmento: SegmentoSolicitudes): { titulo: string; texto: string } {
    return vacio(segmento, this.esPropietario);
  }

  /** Debajo de las pendientes, los encargos en curso (los del segmento Encargos, ya leídos al entrar). */
  get encargosBajoPendientes(): Solicitud[] {
    return this.estado.ENCARGOS.lista ?? [];
  }

  // ------------------------------------------------------------------ lectura

  /** `GET /solicitudes?segmento=…`. La lista anterior sigue a la vista mientras se relee, como hoy. */
  cargar(segmento: SegmentoSolicitudes): void {
    const estado = this.estado[segmento];
    this.lecturas[segmento]?.unsubscribe();
    estado.cargando = true;
    estado.error = null;
    this.lecturas[segmento] = this.solicitudes.listar(segmento).subscribe({
      next: (lista) => {
        estado.lista = lista;
        estado.cargando = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        estado.cargando = false;
        estado.error = { mensaje: mensajeDeError(error) };
        this.cdr.markForCheck();
      },
    });
  }

  /** «Actualizar» y tras cada escritura: los segmentos ya cargados y el contador de la cabecera (como hoy). */
  recargar(): void {
    if (!this.destruido) {
      for (const segmento of SEGMENTOS) {
        if (segmento !== 'RESUELTAS' || this.estado.RESUELTAS.lista !== null) {
          this.cargar(segmento);
        }
      }
    }
    this.contador.refrescar();
  }

  // ------------------------------------------------------------------ acciones de la fila

  acciones(s: Solicitud): AccionSolicitud[] {
    return accionesDe(s, this.esPropietario);
  }

  esPrincipal(accion: AccionSolicitud): boolean {
    return PRINCIPALES.has(accion);
  }

  esNegativa(accion: AccionSolicitud): boolean {
    return NEGATIVAS.has(accion);
  }

  ocupada(s: Solicitud): boolean {
    return this.enCurso.has(s.id);
  }

  enVuelo(s: Solicitud, accion: AccionSolicitud): boolean {
    return this.enCurso.get(s.id) === accion;
  }

  textoOcupado(accion: AccionSolicitud): string {
    return OCUPADA[accion];
  }

  hacer(s: Solicitud, accion: AccionSolicitud): Promise<void> {
    if (this.ocupada(s) || !this.acciones(s).includes(accion)) {
      return Promise.resolve();
    }
    switch (accion) {
      case 'aceptar':
        return this.aceptar(s);
      case 'denegar':
        return this.denegar(s);
      case 'retirar':
        return this.retirar(s);
      case 'encargado':
      case 'recibido':
        return this.avanzar(s, accion);
      case 'rechazar':
        return this.rechazar(s);
      default:
        return this.registrarDevolucion(s);
    }
  }

  /** Como hoy: confirma con el verbo y el efecto del tipo; NO_APLICABLE avisa con la razón de la API. */
  private async aceptar(s: Solicitud): Promise<void> {
    if (s.tipo === 'ENCARGO') {
      return;
    }
    const textos = ACEPTAR[s.tipo];
    const r = await this.confirmacion.confirmar<Solicitud>({
      titulo: textos.titulo,
      mensaje: `${descripcion(s)}. ${textos.efecto}`,
      accion: textos.verbo,
      accionOcupada: OCUPADA.aceptar,
      cancelar: 'Volver',
      nombre: 'aceptar-solicitud',
      ejecutar: () => this.solicitudes.aceptar(s.id),
    });
    this.alTerminar(r, 'No se ha podido aceptar la solicitud.', (resultado) => {
      if (resultado?.estado === 'NO_APLICABLE') {
        this.avisos.error(`No se ha aplicado. ${resultado.respuesta?.trim() || 'El estado cambió desde que se pidió.'}`);
      } else {
        this.avisos.exito(textos.hecho);
      }
    });
  }

  /** Como hoy: respuesta obligatoria (500) para quien lo pidió; se envía recortada. */
  private async denegar(s: Solicitud): Promise<void> {
    const campo = 'sol-respuesta';
    let respuesta = '';
    const r = await this.confirmacion.confirmar<Solicitud>({
      titulo: 'Denegar la solicitud',
      mensaje: descripcion(s),
      campos: [
        {
          id: campo,
          tipo: 'textarea',
          etiqueta: 'Respuesta',
          placeholder: 'Por qué no se hace; quien lo pidió lo verá',
          atributos: { maxlength: String(MAX_MOTIVO), 'aria-label': 'Respuesta a quien lo pidió' },
        },
      ],
      accion: 'Denegar',
      accionOcupada: OCUPADA.denegar,
      cancelar: 'Volver',
      destructivo: true,
      nombre: 'denegar-solicitud',
      comprobar: (valores) => {
        respuesta = String(valores[campo] ?? '').trim();
        return respuesta ? null : { campo, mensaje: 'Escribe la respuesta para quien lo pidió' };
      },
      ejecutar: () => this.solicitudes.denegar(s.id, respuesta),
    });
    this.alTerminar(r, 'No se ha podido denegar la solicitud.', () => this.avisos.exito('Solicitud denegada'));
  }

  /** Como hoy, sin confirmación: el botón queda ocupado hasta que responde. No se cancela al salir. */
  private retirar(s: Solicitud): Promise<void> {
    this.enCurso.set(s.id, 'retirar');
    return new Promise((resolver) => {
      this.solicitudes.retirar(s.id).subscribe({
        next: () => {
          this.avisos.exito('Solicitud retirada');
          this.trasEscribir(s);
          resolver();
        },
        error: (error) => {
          this.avisos.error(mensajeDeError(error) ?? 'No se ha podido retirar la solicitud.');
          this.trasEscribir(s);
          resolver();
        },
      });
    });
  }

  private async avanzar(s: Solicitud, accion: 'encargado' | 'recibido'): Promise<void> {
    const textos = accion === 'encargado' ? AVANCE.ENCARGADO : AVANCE.RECIBIDO;
    const r = await this.confirmacion.confirmar<Solicitud>({
      titulo: textos.titulo,
      mensaje: `${descripcion(s)}. ${textos.efecto}`,
      accion: textos.verbo,
      accionOcupada: OCUPADA[accion],
      cancelar: 'Volver',
      nombre: `marcar-${accion}`,
      ejecutar: () => (accion === 'encargado' ? this.solicitudes.marcarEncargado(s.id) : this.solicitudes.marcarRecibido(s.id)),
    });
    this.alTerminar(r, 'No se ha podido actualizar el encargo.', () => this.avisos.exito(textos.hecho));
  }

  /** Como hoy: motivo obligatorio (D6c-1) y qué pasa con el cargo; cae el encargo (§6.3). */
  private async rechazar(s: Solicitud): Promise<void> {
    const campo = 'sol-motivo';
    let motivo = '';
    const r = await this.confirmacion.confirmar<Solicitud>({
      titulo: 'Rechazar el encargo',
      mensaje: `${descripcion(s)}. ${efectoRechazo(s)}`,
      campos: [
        {
          id: campo,
          tipo: 'textarea',
          etiqueta: 'Motivo',
          placeholder: 'Por qué no se puede atender; el profesor lo verá',
          atributos: { maxlength: String(MAX_MOTIVO), 'aria-label': 'Motivo del rechazo' },
        },
      ],
      accion: 'Rechazar',
      accionOcupada: OCUPADA.rechazar,
      cancelar: 'Volver',
      destructivo: true,
      nombre: 'rechazar-encargo',
      comprobar: (valores) => {
        motivo = String(valores[campo] ?? '').trim();
        return motivo ? null : { campo, mensaje: 'Escribe el motivo para el profesor' };
      },
      ejecutar: () => this.solicitudes.rechazar(s.id, motivo),
    });
    this.alTerminar(r, 'No se ha podido rechazar el encargo.', () => this.avisos.exito('Encargo rechazado'));
  }

  /**
   * Como hoy (195, D6c-11): confirma alumno, concepto e importe (el cobro entero; no se pide) con una nota opcional
   * (`null` si va vacía). Un 403 es la nota F2 de la 6c-5: el cobro es de otra sede, y se explica quién la registra.
   */
  private async registrarDevolucion(s: Solicitud): Promise<void> {
    const campo = 'sol-nota';
    let nota: string | null = null;
    const datos: Array<[string, string]> = [
      ['Alumno', sobreQue(s)],
      ['Concepto', s.concepto?.trim() ?? ''],
      ['Importe', s.importe === null ? '' : eur(s.importe)],
    ];
    const r = await this.confirmacion.confirmar<Solicitud>({
      titulo: 'Registrar devolución',
      bloques: [
        { tipo: 'lista', elementos: datos.filter(([, valor]) => valor).map(([etiqueta, valor]) => [`${etiqueta}: `, { fuerte: valor }]) },
        { tipo: 'parrafo', partes: ['Se devuelve el cobro entero y sale de la caja de la sede.'] },
      ],
      campos: [
        {
          id: campo,
          tipo: 'textarea',
          etiqueta: 'Nota (opcional)',
          placeholder: 'A quién y cómo se ha devuelto',
          atributos: { maxlength: String(MAX_MOTIVO), 'aria-label': 'Nota de la devolución' },
        },
      ],
      accion: 'Registrar devolución',
      accionOcupada: OCUPADA.devolucion,
      cancelar: 'Volver',
      nombre: 'registrar-devolucion',
      comprobar: (valores) => {
        nota = String(valores[campo] ?? '').trim() || null;
        return null;
      },
      ejecutar: () => this.solicitudes.registrarDevolucion(s.id, nota),
    });
    if (r.error instanceof HttpErrorResponse && r.error.status === 403) {
      this.avisos.error(`No puedes registrarla. ${DEVOLUCION_OTRA_SEDE}`);
      this.recargar();
      return;
    }
    this.alTerminar(r, 'No se ha podido registrar la devolución.', () => this.avisos.exito('Devolución registrada'));
  }

  /**
   * Los diálogos son modales (la fila no se puede pulsar mientras están abiertos) y, con la petición en vuelo, su botón
   * queda ocupado y no se pueden cerrar: sin doble envío. Cancelar no hace nada; si se envió, el aviso (el `mensaje`
   * del servidor si falló) y releer como hoy.
   */
  private alTerminar(r: ResultadoConfirmacion<Solicitud>, porDefecto: string, bien: (respuesta?: Solicitud) => void): void {
    if (r.error) {
      this.avisos.error(mensajeDeError(r.error) ?? porDefecto);
    } else if (r.confirmado) {
      bien(r.respuesta);
    } else {
      return;
    }
    this.recargar();
  }

  private trasEscribir(s: Solicitud): void {
    this.enCurso.delete(s.id);
    this.recargar();
    if (!this.destruido) {
      this.cdr.markForCheck();
    }
  }

  /** Propietario = `ROLE_ADMIN`, como hoy; si los roles no se pueden leer, falla cerrado (sin aceptar ni denegar). */
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

/** El de `caja.service` (`mensajeDeError`): el cuerpo si es texto, o su `mensaje` (o `message`); si no, `null`. */
function mensajeDeError(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const cuerpo = error.error;
  if (typeof cuerpo === 'string' && cuerpo.trim()) {
    return cuerpo.trim();
  }
  const mensaje = cuerpo?.mensaje ?? cuerpo?.message;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje.trim() : null;
}
