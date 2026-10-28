import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { PlantillasPdfComponent } from './plantillas/plantillas-pdf.component';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import {
  MOTIVO_CANCELACION_MAX,
  MOTIVO_CANCELACION_POR_DEFECTO,
  PreinscripcionService,
} from '../../features/preinscripcion/preinscripcion.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { plural } from '../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import { AltaPreinscripcion } from './alta-preinscripcion';
import { FinalizarAltaComponent } from './finalizar-alta.component';
import {
  ESTADOS,
  FILTROS_VACIOS,
  FilaPreinscripcion,
  Filtros,
  Solicitud,
  TAMANO_PAGINA,
  alternarTurno,
  esAsignacionActual,
  esPlazaActual,
  etiquetaEmail,
  fechaHora,
  filaLista,
  haceCuanto,
  necesitaForzar,
  nombreCompleto,
  opcionesTemporada,
  ordenarTurnos,
  puedeCambiarAsignacion,
  puedeEliminar,
  puedeReenviarCambio,
  textoEmailInicial,
  textoIntentos,
  textoPastilla,
  textoRango,
  tonoEstado,
  turnoFueraEdad,
  turnosCambiados,
} from './preinscripciones-vista';

type EstadoCarga = 'cargando' | 'listo' | 'error';
type Pestana = 'solicitudes' | 'plantillas';
/** Lo que está en vuelo en el detalle: sin doble envío. */
type Accion = 'reenviar' | 'cambio' | 'confirmacion' | 'descargar' | null;

interface Editor {
  cargando: boolean;
  turnos: any[];
  ids: number[];
  guardando: boolean;
}

/**
 * Preinscripciones del panel nuevo (8b-3h, P1 «lista con detalle al lado»). Lista a la izquierda (nombre, estado con el
 * puesto en la lista de espera, referencia y DNI, disciplina, hace cuánto llegó y «Con observación»; paginación debajo) y
 * el detalle a la derecha, siempre a la vista en escritorio: datos, «Horario» con «Cambiar la asignación», «Avisos por
 * correo», «Firma y ficha» y las acciones al pie («Revisar y finalizar alta», «Reenviar solicitud original» y «Más» con
 * cancelar y eliminar definitivamente). En el móvil, la lista y, al tocar, el detalle con «Volver a la lista».
 *
 * Las MISMAS peticiones, cuerpos y confirmaciones que `PreinscripcionesAdminComponent` (con `PreinscripcionService` para
 * las escrituras), con el diálogo, los avisos y los estados de carga del panel nuevo. La selección vive en el estado del
 * componente: ningún dato personal va a la URL ni al almacenamiento del navegador; la firma se pide con la sesión y se
 * enseña con una URL `blob:` en memoria que se libera al cambiar de solicitud. Plantillas PDF usa el editor del panel.
 * Va en su propio chunk detrás de `disenoNuevoGuard`.
 */
@Component({
  selector: 'app-preinscripciones-panel',
  standalone: true,
  imports: [
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    FinalizarAltaComponent,
    PlantillasPdfComponent,
  ],
  templateUrl: './preinscripciones.component.html',
  styleUrls: ['./preinscripciones.component.scss'],
})
export class PreinscripcionesPanelComponent implements OnInit, OnDestroy {
  private readonly api = inject(PreinscripcionService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly catalogo = inject(CatalogoService);
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** El editor de plantillas pregunta por los cambios sin publicar antes de salir de la pestaña. */
  @ViewChild(PlantillasPdfComponent) plantillas?: PlantillasPdfComponent;

  readonly estados = ESTADOS;
  readonly tamanoPagina = TAMANO_PAGINA;
  readonly etiquetaEmail = etiquetaEmail;
  readonly textoIntentos = textoIntentos;
  readonly textoEmailInicial = textoEmailInicial;
  readonly textoPastilla = textoPastilla;
  readonly tonoEstado = tonoEstado;
  readonly fechaHora = fechaHora;
  readonly nombreCompleto = nombreCompleto;

  pestana: Pestana = 'solicitudes';
  estado: EstadoCarga = 'cargando';
  /** Como hoy, un solo juego de filtros: «Filtrar», «Actualizar» y las páginas leen lo que haya escrito. */
  filtros: Filtros = { ...FILTROS_VACIOS };
  items: Solicitud[] = [];
  filas: FilaPreinscripcion[] = [];
  /** Página de la API (desde 0). */
  pagina = 0;
  total = 0;
  paginas = 0;

  seleccion: Solicitud | null = null;
  firmaUrl: string | null = null;
  firmaEstado: 'cargando' | 'lista' | 'error' | null = null;
  /** Fichas compatibles (`alumnosCoincidentes`), que llegan con el detalle de una pendiente (R2-P7a). */
  fichasEstado: 'cargando' | 'lista' | 'error' | null = null;
  editor: Editor | null = null;
  alta: AltaPreinscripcion | null = null;
  accion: Accion = null;
  menuAbierto = false;

  private carga?: Subscription;
  private firmaSub?: Subscription;
  private fichasSub?: Subscription;
  private turnosSub?: Subscription;
  /** Las escrituras no se cancelan al salir; lo que toca la vista tras destruir, sí se protege. */
  private destruido = false;
  private escuchaFuera = false;

  get disciplinas(): { codigo: string; nombre: string }[] {
    return this.catalogo.disciplinasActivas().map((d) => ({ codigo: d.codigo, nombre: d.nombre }));
  }

  get temporadas(): string[] {
    return opcionesTemporada(new Date(), this.items.map((i) => i['temporada']), this.filtros.temporada);
  }

  get textoRango(): string {
    return `${textoRango(this.pagina, TAMANO_PAGINA, this.total)} ${this.total === 1 ? 'preinscripción' : 'preinscripciones'}`;
  }

  get hayFiltros(): boolean {
    return Object.values(this.filtros).some((v) => !!v);
  }

  get esAdmin(): boolean {
    return this.auth.tieneRolAdmin();
  }

  get puedeCambiarAsignacion(): boolean {
    return puedeCambiarAsignacion(this.seleccion);
  }

  get puedeReenviarCambio(): boolean {
    return puedeReenviarCambio(this.seleccion);
  }

  get puedeEliminar(): boolean {
    return puedeEliminar(this.seleccion, this.esAdmin);
  }

  /** Lo que lleva el menú «Más»: cancelar (pendiente o en lista de espera) y eliminar definitivamente (cancelada, Administración). */
  get opcionCancelar(): string | null {
    const estado = this.seleccion?.['estado'];
    return estado === 'PENDIENTE' ? 'Cancelar solicitud' : estado === 'EN_LISTA_ESPERA' ? 'Cancelar lista de espera' : null;
  }

  get hayMas(): boolean {
    return !!this.opcionCancelar || this.puedeEliminar;
  }

  get hayAcciones(): boolean {
    const estado = this.seleccion?.['estado'];
    return estado === 'PENDIENTE' || estado === 'EN_LISTA_ESPERA' || this.hayMas;
  }

  get puedeFinalizar(): boolean {
    return this.seleccion?.['estado'] === 'PENDIENTE' && this.fichasEstado === 'lista';
  }

  get numeroFichas(): number {
    return (this.seleccion?.['alumnosCoincidentes'] || []).length;
  }

  get textoFichas(): string {
    return plural(this.numeroFichas, 'ficha compatible', 'fichas compatibles');
  }

  get llegada(): string {
    return haceCuanto(this.seleccion?.['creadaEn'], new Date());
  }

  etiquetaDisciplina(codigo: string | null | undefined): string {
    return this.catalogo.etiquetaDisciplina(codigo);
  }

  discapacidad(s: Solicitud): string {
    const v = s['tieneDiscapacidad'];
    return v == null ? 'No recogido' : v ? 'Sí' : 'No';
  }

  ngOnInit(): void {
    this.pestana = this.route.snapshot.queryParamMap.get('tab') === 'plantillas' ? 'plantillas' : 'solicitudes';
    this.cargar();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.carga?.unsubscribe();
    this.firmaSub?.unsubscribe();
    this.fichasSub?.unsubscribe();
    this.turnosSub?.unsubscribe();
    this.quitarEscucha();
    this.liberarFirma();
  }

  // ------------------------------------------------------------------ pestañas

  /** `canDeactivate` (`salirSinGuardarGuard`): salir de la pantalla con cambios sin publicar en la plantilla pregunta antes. */
  puedeSalir(): boolean | Promise<boolean> {
    return this.pestana === 'plantillas' && this.plantillas ? this.plantillas.puedeSalir() : true;
  }

  /** Como hoy: con cambios sin guardar en la plantilla, se pregunta antes de salir; la pestaña va a `?tab=`. */
  async cambiarPestana(pestana: Pestana): Promise<void> {
    if (pestana === this.pestana) {
      return;
    }
    if (this.pestana === 'plantillas' && this.plantillas && !(await this.plantillas.puedeSalir())) {
      return;
    }
    this.pestana = pestana;
    this.cerrarMenu(false);
    this.router.navigate([], { relativeTo: this.route, queryParams: { tab: pestana }, queryParamsHandling: 'merge' });
    this.cdr.markForCheck();
  }

  /** Flechas, Inicio y Fin entre las dos pestañas (patrón «tabs» de WAI-ARIA). */
  teclaPestanas(evento: KeyboardEvent): void {
    const orden: Pestana[] = ['solicitudes', 'plantillas'];
    const actual = orden.indexOf(this.pestana);
    let siguiente: number | null = null;
    if (evento.key === 'ArrowRight' || evento.key === 'ArrowLeft') {
      siguiente = (actual + 1) % 2;
    } else if (evento.key === 'Home') {
      siguiente = 0;
    } else if (evento.key === 'End') {
      siguiente = 1;
    }
    if (siguiente === null) {
      return;
    }
    evento.preventDefault();
    const destino = orden[siguiente];
    this.cambiarPestana(destino).then(() => {
      this.host.nativeElement.querySelector<HTMLElement>(`#preins-pestana-${this.pestana}`)?.focus();
    });
  }

  // ------------------------------------------------------------------ lista

  /**
   * `GET /preinscripciones` con los filtros y la página de hoy. Una página vacía que ya no existe (se borró la última
   * fila) vuelve a la anterior, como hoy. `silencioso` (tras una escritura): sin esqueleto y la selección se refresca.
   */
  cargar(silencioso = false): void {
    this.carga?.unsubscribe();
    if (!silencioso) {
      this.estado = 'cargando';
    }
    this.carga = this.lecturas.preinscripciones({ ...this.filtros }, this.pagina, TAMANO_PAGINA).subscribe({
      next: (r) => {
        const content: Solicitud[] = r?.content ?? [];
        if (!content.length && this.pagina > 0 && r?.totalElements > 0) {
          this.pagina--;
          this.cargar(silencioso);
          return;
        }
        this.items = content;
        this.pagina = Number.isInteger(r?.page) ? r.page : this.pagina;
        this.total = Number(r?.totalElements) || 0;
        this.paginas = Number(r?.totalPages) || 0;
        const ahora = new Date();
        this.filas = content.map((s) => filaLista(s, ahora, (c) => this.etiquetaDisciplina(c)));
        this.estado = 'listo';
        this.refrescarSeleccion();
        this.cdr.markForCheck();
      },
      error: () => {
        if (silencioso && this.estado === 'listo') {
          this.avisos.error('No se pudo actualizar la lista de preinscripciones.');
        } else {
          this.estado = 'error';
        }
        this.cdr.markForCheck();
      },
    });
  }

  filtrar(evento?: Event): void {
    evento?.preventDefault();
    this.pagina = 0;
    this.cargar();
  }

  actualizar(): void {
    this.cargar();
  }

  ponerFiltro(clave: keyof Filtros, valor: string): void {
    this.filtros = { ...this.filtros, [clave]: valor };
  }

  quitarFiltros(): void {
    this.filtros = { ...FILTROS_VACIOS };
    this.filtrar();
  }

  /** Como hoy: cambiar de página cierra el detalle. */
  irAPagina(destino: number): void {
    if (this.estado === 'cargando' || destino < 0 || destino >= this.paginas) {
      return;
    }
    this.pagina = destino;
    this.cerrarDetalle();
    this.cargar();
  }

  esElegida(f: FilaPreinscripcion): boolean {
    return this.seleccion?.['referencia'] === f.referencia;
  }

  // ------------------------------------------------------------------ detalle

  elegirFila(indice: number): void {
    const item = this.items[indice];
    if (item) {
      this.seleccionar(item);
    }
  }

  /**
   * Abre una solicitud, como hoy: pide su firma y, si está pendiente, su detalle con las fichas compatibles. Con un
   * guardado de turnos o un alta en vuelo no cambia. En el móvil, el foco pasa al detalle.
   */
  seleccionar(item: Solicitud): void {
    if (this.editor?.guardando || this.alta) {
      return;
    }
    const misma = this.seleccion?.['referencia'] === item['referencia'];
    this.cerrarMenu(false);
    this.editor = null;
    this.turnosSub?.unsubscribe();
    if (!misma || this.firmaEstado === 'error') {
      this.liberarFirma();
      this.seleccion = item;
      if (item['estado'] === 'PENDIENTE') {
        this.cargarFichas(item['referencia']);
      } else {
        this.descartarFichas();
      }
      this.cargarFirma(item['referencia']);
    }
    // Con la lista escondida (lista y detalle no caben juntos), el foco pasa al detalle.
    this.cdr.detectChanges();
    const lista = this.host.nativeElement.querySelector<HTMLElement>('.preins__columna-lista');
    if (lista && lista.getClientRects().length === 0) {
      this.host.nativeElement.querySelector<HTMLElement>('#preins-detalle-titulo')?.focus();
    }
  }

  /** «Volver a la lista» (móvil): cierra el detalle y devuelve el foco a la fila. */
  volver(): void {
    const referencia = this.seleccion?.['referencia'];
    this.cerrarDetalle();
    this.cdr.detectChanges();
    const filas = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.preins__fila'));
    filas.find((f) => f.dataset['ref'] === referencia)?.focus();
  }

  cerrarDetalle(): void {
    this.cerrarMenu(false);
    this.editor = null;
    this.turnosSub?.unsubscribe();
    this.firmaSub?.unsubscribe();
    this.alta = null;
    this.liberarFirma();
    this.descartarFichas();
    this.seleccion = null;
  }

  /** Tras releer: la solicitud abierta toma sus datos nuevos si sigue en la página; si no, se cierra. */
  private refrescarSeleccion(): void {
    const actual = this.seleccion;
    if (!actual) {
      return;
    }
    const nueva = this.items.find((i) => i['referencia'] === actual['referencia']);
    if (!nueva) {
      this.cerrarDetalle();
      return;
    }
    this.seleccion = { ...nueva, alumnosCoincidentes: actual['alumnosCoincidentes'] };
    if (nueva['estado'] !== 'PENDIENTE') {
      this.descartarFichas();
    } else if (actual['estado'] !== 'PENDIENTE') {
      this.cargarFichas(nueva['referencia']);
    }
  }

  cargarFirma(referencia: string): void {
    this.firmaSub?.unsubscribe();
    this.liberarFirma();
    this.firmaEstado = 'cargando';
    this.firmaSub = this.lecturas.firmaPreinscripcion(referencia).subscribe({
      next: (firma) => {
        if (this.seleccion?.['referencia'] !== referencia) {
          return;
        }
        this.firmaUrl = URL.createObjectURL(firma);
        this.firmaEstado = 'lista';
        this.cdr.markForCheck();
      },
      error: () => {
        this.firmaEstado = 'error';
        this.cdr.markForCheck();
      },
    });
  }

  reintentarFirma(): void {
    const referencia = this.seleccion?.['referencia'];
    if (referencia) {
      this.cargarFirma(referencia);
    }
  }

  private liberarFirma(): void {
    if (this.firmaUrl) {
      URL.revokeObjectURL(this.firmaUrl);
    }
    this.firmaUrl = null;
    this.firmaEstado = null;
  }

  /** Las fichas compatibles del detalle sustituyen a las de la fila; el resto de la fila no cambia (como hoy). */
  cargarFichas(referencia: string): void {
    this.fichasSub?.unsubscribe();
    this.fichasEstado = 'cargando';
    this.fichasSub = this.lecturas.preinscripcion(referencia).subscribe({
      next: (detalle) => {
        if (this.seleccion?.['referencia'] === referencia) {
          this.seleccion = { ...this.seleccion, alumnosCoincidentes: detalle?.alumnosCoincidentes || [] };
        }
        this.fichasEstado = 'lista';
        this.cdr.markForCheck();
      },
      error: () => {
        this.fichasEstado = 'error';
        this.cdr.markForCheck();
      },
    });
  }

  reintentarFichas(): void {
    const referencia = this.seleccion?.['referencia'];
    if (referencia) {
      this.cargarFichas(referencia);
    }
  }

  private descartarFichas(): void {
    this.fichasSub?.unsubscribe();
    this.fichasEstado = null;
  }

  /** `GET …/documento-firmado` en blob y la misma descarga de hoy (`solicitud-original-{ref}.pdf`); nada se guarda. */
  descargarDocumento(): void {
    const referencia = this.seleccion?.['referencia'];
    if (!referencia || this.accion) {
      return;
    }
    this.accion = 'descargar';
    this.api.documentoFirmado(referencia).subscribe({
      next: (documento) => {
        this.accion = null;
        const url = URL.createObjectURL(documento);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `solicitud-original-${referencia}.pdf`;
        enlace.click();
        URL.revokeObjectURL(url);
        this.cdr.markForCheck();
      },
      error: () => {
        this.accion = null;
        this.avisos.error('No se pudo descargar la solicitud original firmada.');
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ «Cambiar la asignación»

  abrirEditor(): void {
    const s = this.seleccion;
    if (!s || !this.puedeCambiarAsignacion || this.editor) {
      return;
    }
    const referencia = s['referencia'];
    const editor: Editor = { cargando: true, turnos: [], ids: (s['turnosSolicitados'] || []).map((t: any) => t.id), guardando: false };
    this.editor = editor;
    this.turnosSub?.unsubscribe();
    this.turnosSub = this.api.turnos(s['deporte']).subscribe({
      next: (turnos) => {
        if (this.editor === editor && this.seleccion?.['referencia'] === referencia) {
          editor.turnos = ordenarTurnos(turnos || []);
          editor.cargando = false;
        }
        this.cdr.markForCheck();
      },
      error: () => {
        if (this.editor === editor) {
          this.editor = null;
        }
        this.avisos.error('No se pudieron cargar los turnos disponibles.');
        this.cdr.markForCheck();
      },
    });
  }

  cerrarEditor(): void {
    if (this.editor?.guardando) {
      return;
    }
    this.turnosSub?.unsubscribe();
    this.editor = null;
  }

  turnoElegido(turno: any): boolean {
    return !!this.editor?.ids.includes(turno.id);
  }

  alternarTurno(turno: any, marcado: boolean): void {
    if (!this.editor || this.editor.guardando) {
      return;
    }
    this.editor.ids = alternarTurno(this.editor.ids, turno, this.editor.turnos, marcado);
  }

  fueraDeEdad(turno: any): boolean {
    return !!this.seleccion && turnoFueraEdad(turno, this.seleccion);
  }

  plazaActual(turno: any): boolean {
    return !!this.seleccion && esPlazaActual(turno, this.seleccion);
  }

  esActual(turno: any): boolean {
    return !!this.seleccion && esAsignacionActual(turno, this.seleccion);
  }

  get turnosCambiados(): boolean {
    return !!this.seleccion && !!this.editor && turnosCambiados(this.seleccion, this.editor.ids);
  }

  /** `PUT …/turnos` con `{turnoIds}`, como hoy; con un turno nuevo sin plaza o fuera de edad, antes «Forzar asignación». */
  async guardarTurnos(): Promise<void> {
    const s = this.seleccion;
    const editor = this.editor;
    if (!s || !editor || !editor.ids.length || !this.turnosCambiados || editor.guardando) {
      return;
    }
    const referencia = s['referencia'];
    if (necesitaForzar(s, editor.turnos, editor.ids)) {
      const r = await this.confirmacion.confirmar({
        titulo: 'Forzar asignación de turnos',
        mensaje:
          'La selección contiene turnos nuevos fuera de la edad recomendada o sin plaza disponible. La solicitud quedará con plaza asignada aunque supere el aforo.',
        accion: 'Sí, forzar asignación',
        cancelar: 'Volver',
        destructivo: true,
        nombre: 'forzar-turnos',
      });
      if (!r.confirmado || this.editor !== editor || this.seleccion?.['referencia'] !== referencia) {
        return;
      }
    }
    const ids = [...editor.ids];
    editor.guardando = true;
    this.cdr.markForCheck();
    this.api.actualizarTurnos(referencia, ids).subscribe({
      next: () => {
        editor.guardando = false;
        this.avisos.exito('Turnos actualizados. El aviso al cliente se ha puesto en cola.');
        if (this.editor === editor) {
          this.editor = null;
        }
        if (!this.destruido) {
          this.cargar(true);
        }
        this.cdr.markForCheck();
      },
      error: (e) => {
        editor.guardando = false;
        this.avisos.error(mensajeError(e, 'No se pudieron actualizar los turnos.'));
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ reenvíos (sin confirmación, como hoy)

  reenviar(): void {
    const referencia = this.seleccion?.['referencia'];
    if (!referencia || this.accion) {
      return;
    }
    this.accion = 'reenviar';
    this.api.reenviar(referencia).subscribe({
      next: () => {
        this.accion = null;
        this.avisos.exito('Solicitud original reenviada correctamente.');
        this.cdr.markForCheck();
      },
      error: (e) => {
        this.accion = null;
        this.avisos.error(mensajeError(e, 'No se pudo reenviar la solicitud original.'));
        this.cdr.markForCheck();
      },
    });
  }

  reenviarCambio(): void {
    const referencia = this.seleccion?.['referencia'];
    if (!referencia || !this.puedeReenviarCambio || this.accion) {
      return;
    }
    this.accion = 'cambio';
    this.api.reenviarCambioTurnos(referencia).subscribe({
      next: () => {
        this.accion = null;
        this.avisos.exito('Reenvío del aviso de cambio solicitado.');
        if (!this.destruido) {
          this.cargar(true);
        }
        this.cdr.markForCheck();
      },
      error: (e) => {
        this.accion = null;
        this.avisos.error(mensajeError(e, 'No se pudo reenviar el aviso de cambio.'));
        this.cdr.markForCheck();
      },
    });
  }

  reenviarConfirmacion(): void {
    const referencia = this.seleccion?.['referencia'];
    if (!referencia || this.seleccion?.['estado'] !== 'FINALIZADA' || this.accion) {
      return;
    }
    this.accion = 'confirmacion';
    this.api.reenviarFinalizacion(referencia).subscribe({
      next: () => {
        this.accion = null;
        this.avisos.exito('Reenvío de confirmación solicitado.');
        if (!this.destruido) {
          this.cargar(true);
        }
        this.cdr.markForCheck();
      },
      error: (e) => {
        this.accion = null;
        this.avisos.error(mensajeError(e, 'No se pudo reenviar la confirmación.'));
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ cancelar y eliminar (menú «Más»)

  /**
   * El diálogo de hoy con su motivo opcional: se envía lo escrito tal cual (o vacío, y el backend aplica el de por
   * defecto). `comprobar` solo recoge el motivo para `ejecutar`, que lo envía con el diálogo ocupado.
   */
  async cancelar(): Promise<void> {
    const s = this.seleccion;
    if (!s || !this.opcionCancelar) {
      return;
    }
    this.cerrarMenu(true);
    const referencia = s['referencia'];
    const email = s['email'];
    let motivo = '';
    const r = await this.confirmacion.confirmar({
      titulo: '¿Cancelar preinscripción?',
      mensaje: `Se cancelará la solicitud ${referencia} y se avisará a la familia por correo.`,
      bloques: [
        {
          tipo: 'parrafo',
          partes: ['Si lo dejas vacío se enviará el motivo por defecto.' + (email ? ` El aviso se enviará a ${email}.` : '')],
        },
      ],
      campos: [
        {
          id: 'preins-motivo',
          tipo: 'textarea',
          etiqueta: 'Motivo (opcional)',
          placeholder: MOTIVO_CANCELACION_POR_DEFECTO,
          atributos: { maxlength: String(MOTIVO_CANCELACION_MAX) },
        },
      ],
      accion: 'Cancelar y avisar',
      accionOcupada: 'Cancelando…',
      cancelar: 'Volver',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'cancelar-preinscripcion',
      comprobar: (valores) => {
        motivo = String(valores['preins-motivo'] ?? '');
        return null;
      },
      ejecutar: () => this.api.cancelar(referencia, motivo),
    });
    if (r.error) {
      this.avisos.error(mensajeError(r.error, 'No se pudo cancelar la preinscripción.'));
      this.cdr.markForCheck();
      return;
    }
    if (!r.confirmado) {
      return;
    }
    this.avisos.exito('Preinscripción cancelada.');
    if (!this.destruido) {
      this.cargar(true);
    }
  }

  /**
   * Borrado definitivo, solo de canceladas y por Administración: hay que escribir la referencia (como hoy, sin
   * mayúsculas ni espacios de más). Sin acción por defecto: el foco empieza en «Volver», Intro no confirma (no hay
   * formulario) y la frase dice que no se puede deshacer.
   */
  async eliminar(): Promise<void> {
    const s = this.seleccion;
    if (!s || !this.puedeEliminar) {
      return;
    }
    this.cerrarMenu(true);
    const referencia: string = s['referencia'];
    const campo = 'preins-confirmar-referencia';
    const r = await this.confirmacion.confirmar({
      titulo: 'Eliminar definitivamente',
      mensaje: [
        'Se eliminarán la solicitud ',
        { fuerte: referencia },
        ', sus datos personales, la firma y el PDF. Esta acción no se puede deshacer.',
      ],
      campos: [{ id: campo, tipo: 'text', etiqueta: 'Escribe la referencia para confirmar', placeholder: referencia }],
      accion: 'Eliminar definitivamente',
      accionOcupada: 'Eliminando…',
      cancelar: 'Volver',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'eliminar-preinscripcion',
      comprobar: (valores) =>
        String(valores[campo] ?? '').trim().toUpperCase() === referencia ? null : { campo, mensaje: 'La referencia no coincide.' },
      ejecutar: () => this.api.eliminar(referencia),
    });
    if (r.error) {
      this.avisos.error(mensajeError(r.error, 'No se pudo eliminar la preinscripción.'));
      this.cdr.markForCheck();
      return;
    }
    if (!r.confirmado) {
      return;
    }
    this.avisos.exito('Preinscripción eliminada definitivamente.');
    if (this.seleccion?.['referencia'] === referencia) {
      this.cerrarDetalle();
    }
    if (!this.destruido) {
      this.cargar(true);
    }
    this.cdr.markForCheck();
  }

  // ------------------------------------------------------------------ «Revisar y finalizar alta»

  abrirAlta(): void {
    const s = this.seleccion;
    if (!s || !this.puedeFinalizar || this.alta) {
      return;
    }
    this.cerrarMenu(false);
    this.alta = new AltaPreinscripcion(s, this.catalogo);
  }

  cerrarAlta(): void {
    this.alta = null;
  }

  alFinalizar(): void {
    this.alta = null;
    this.avisos.exito('Preinscripción finalizada: tarifa, grupos y turnos actualizados.');
    if (!this.destruido) {
      this.cargar(true);
    }
    this.cdr.markForCheck();
  }

  // ------------------------------------------------------------------ menú «Más»

  private menu(): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>('#preins-menu-mas');
  }

  private botonMas(): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>('#preins-boton-mas');
  }

  private readonly alPulsarFuera = (evento: Event): void => {
    const destino = evento.target as Node | null;
    const zona = this.botonMas()?.closest('.preins__menu-zona');
    if (destino && zona?.contains(destino)) {
      return;
    }
    this.cerrarMenu(false);
    this.cdr.markForCheck();
  };

  private quitarEscucha(): void {
    if (this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = false;
    }
  }

  alternarMenu(evento?: KeyboardEvent): void {
    if (this.menuAbierto) {
      this.cerrarMenu(false);
      return;
    }
    this.menuAbierto = true;
    if (!this.escuchaFuera) {
      document.addEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = true;
    }
    this.cdr.detectChanges();
    enfocarOpcion(this.menu(), evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
  }

  teclaBotonMenu(evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && !this.menuAbierto) {
      evento.preventDefault();
      this.alternarMenu(evento);
    }
  }

  teclaMenu(evento: KeyboardEvent): void {
    const menu = this.menu();
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion === 'cerrar') {
      this.cerrarMenu(true);
    } else if (accion === 'salir') {
      this.cerrarMenu(false);
    }
  }

  cerrarMenu(devolverFoco: boolean): void {
    const estaba = this.menuAbierto;
    this.menuAbierto = false;
    this.quitarEscucha();
    if (devolverFoco && estaba) {
      this.cdr.detectChanges();
      this.botonMas()?.focus();
    }
  }

  alSalirDelMenu(evento: FocusEvent): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (zona && destino && !zona.contains(destino)) {
      this.cerrarMenu(false);
    }
  }
}

/** El `mensaje` del servidor si lo trae (el 409, como hoy); si no, el de la pantalla. */
function mensajeError(error: unknown, porDefecto: string): string {
  const mensaje = (error as { error?: { mensaje?: unknown } } | null)?.error?.mensaje;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje : porDefecto;
}
