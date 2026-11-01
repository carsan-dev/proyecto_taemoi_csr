import { LiveAnnouncer } from '@angular/cdk/a11y';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList } from '@angular/cdk/drag-drop';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, Subscription } from 'rxjs';

import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { DocumentoInforme, descargarDocumentoInforme } from '../../servicios/generales/informe-pdf.service';
import {
  OpcionesSubida,
  SUBIDA_DOCUMENTO,
  mensajeErrorSubida,
  prepararFicheroParaSubida,
  reiniciarSelectorArchivo,
} from '../../utils/subida-archivos.util';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { CONSULTA_HOJA, anchoDeHoja } from '../capas/capa-modal';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { moverEn } from '../eventos/eventos-vista';
import { fecha, plural } from '../formato/formato-es';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import { ConCambiosSinGuardar } from '../productos-alumno/salir-sin-guardar.guard';
import { VisorPdfComponent } from '../visor-pdf/visor-pdf.component';
import { ColaSubidas, SubidaVideo } from './cola-subidas';
import { MaterialEditarVideoComponent } from './material-editar-video.component';
import { MaterialExamenApiService } from './material-examen-api.service';
import {
  ACEPTA_DOCUMENTOS,
  ACEPTA_PDF,
  ACEPTA_VIDEOS,
  SUBIDA_TEMARIO,
  fraseGrados,
  fusionarOrden,
  mismoOrden,
  motivoDocumentoSustituto,
  peso,
  tipoFichero,
} from './material-examen-vista';
import { DetalleBloque, DocumentoDetalle, VideoDetalle } from './material-examen.models';
import { MaterialMenuComponent, OpcionMenu } from './material-menu.component';
import { MaterialSubidaFilaComponent } from './material-subida-fila.component';

/** Arrastre: inmediato con ratón y por pulsación larga en táctil, para que el dedo siga desplazando la página. */
const RETARDO_ARRASTRE = { touch: 350, mouse: 0 };

type Estado = 'cargando' | 'listo' | 'error';
/** El mando de orden con el foco: tras mover, el foco vuelve al mismo de la fila movida. */
type MandoOrden = 'asa' | 'subir' | 'bajar';

/**
 * Un bloque de material de examen (pieza R2; bocetos `Bloque`, `Editar`, `Primera` y `MovilBloque`): vídeos con cola de
 * subida y orden, temario y documentación. Tras cada escritura manda el detalle que devuelve la API. El orden local se
 * guarda aparte: lo que llega mientras hay un orden sin guardar respeta ese orden y añade lo nuevo al final.
 *
 * La API no versiona el detalle y las respuestas pueden cruzarse (un vídeo tarda minutos en subir): cada petición anota
 * el `sello` al empezar y, si al volver ha llegado otro detalle entretanto, no sabe cuál es más nuevo y relee el bloque.
 *
 * Los vídeos van tal cual (hasta 500 MB, no caben en memoria) y por la cola, con su progreso; el temario y los documentos
 * pasan por `prepararFicheroParaSubida`, por el fallo de lectura de Chrome en Android.
 */
@Component({
  selector: 'app-material-bloque',
  standalone: true,
  imports: [
    RouterLink,
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    AcentoEscuelaDirective,
    BloqueErrorComponent,
    BotonOcupadoComponent,
    EsqueletoComponent,
    PanelLateralComponent,
    VisorPdfComponent,
    MaterialEditarVideoComponent,
    MaterialMenuComponent,
    MaterialSubidaFilaComponent,
  ],
  providers: [ColaSubidas],
  templateUrl: './material-bloque.component.html',
  styleUrls: ['./material-bloque.component.scss'],
})
export class MaterialBloqueComponent implements OnInit, OnDestroy, ConCambiosSinGuardar {
  private readonly api = inject(MaterialExamenApiService);
  private readonly catalogo = inject(CatalogoService);
  private readonly autenticacion = inject(AuthenticationService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly anunciador = inject(LiveAnnouncer);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly cola = inject(ColaSubidas);

  @ViewChild('selectorSustituto') private selectorSustituto?: ElementRef<HTMLInputElement>;
  @ViewChild('selectorDocumentoSustituto') private selectorDocumentoSustituto?: ElementRef<HTMLInputElement>;

  readonly retardoArrastre = RETARDO_ARRASTRE;
  readonly aceptaVideos = ACEPTA_VIDEOS;
  readonly aceptaPdf = ACEPTA_PDF;
  readonly aceptaDocumentos = ACEPTA_DOCUMENTOS;
  readonly peso = peso;
  readonly fecha = fecha;
  readonly tipoFichero = tipoFichero;

  disciplina = '';
  bloqueId = '';
  estado: Estado = 'cargando';
  detalle: DetalleBloque | null = null;
  /** Los vídeos en el orden de la pantalla; `detalle.videos` guarda el de la API. */
  videos: VideoDetalle[] = [];
  movido: string | null = null;
  guardandoOrden = false;
  /** Temario o documento preparándose o en vuelo (van sin barra: son de hasta 50 MB). */
  enviandoTemario = false;
  enviandoDocumento = false;
  abriendoTemario = false;
  arrastrando = false;
  movil = anchoDeHoja();

  editando: string | null = null;
  guardandoTitulo = false;
  viendo: VideoDetalle | null = null;
  errorVideo = false;
  documentosPdf: DocumentoInforme[] = [];
  focoRetorno: HTMLElement | null = null;

  private ficheroASustituir: string | null = null;
  private documentoASustituir: string | null = null;
  private lectura?: Subscription;
  /** Lo que se corta al salir: «Salir y cortar» vale también para el temario y los documentos en vuelo. */
  private readonly suscripciones = new Subscription();
  private consulta: MediaQueryList | null = null;
  private destruido = false;
  /** Sube cada vez que cambia el detalle. */
  private sello = 0;
  private readonly alCambiarAncho = (e: MediaQueryListEvent): void => {
    this.movil = e.matches;
    this.cdr.detectChanges();
  };

  // ------------------------------------------------------------------ estado

  get ordenSinGuardar(): boolean {
    return !!this.detalle && !mismoOrden(this.videos, this.detalle.videos);
  }

  get subiendo(): boolean {
    return this.cola.enCurso() || this.enviandoTemario || this.enviandoDocumento;
  }

  get nombreDisciplina(): string {
    return this.catalogo.etiquetaDisciplina(this.disciplina);
  }

  get contexto(): string {
    return `${this.detalle?.nombre ?? ''} · ${this.nombreDisciplina}`;
  }

  get taekwondo(): boolean {
    return this.detalle?.disciplina === 'TAEKWONDO';
  }

  get descripcion(): string {
    const d = this.detalle;
    if (!d) {
      return '';
    }
    const conMaterial = !!d.temario || d.videos.length > 0 || d.documentos.length > 0;
    return conMaterial ? fraseGrados(d.grados) : 'Las familias aún no ven material en este bloque.';
  }

  get lineaVideos(): string {
    const bytes = this.videos.reduce((total, v) => total + v.bytes, 0);
    return this.videos.length
      ? `${plural(this.videos.length, 'vídeo', 'vídeos')} · ${peso(bytes)} · Hasta 500 MB por vídeo`
      : 'Hasta 500 MB por vídeo · MP4, WEBM, M4V o MOV';
  }

  get subidasEnCurso(): SubidaVideo[] {
    return this.cola.items().filter((s) => s.estado !== 'fallo');
  }

  get subidasFallidas(): SubidaVideo[] {
    return this.cola.items().filter((s) => s.estado === 'fallo');
  }

  get videoEditado(): VideoDetalle | null {
    return this.videos.find((v) => v.fichero === this.editando) ?? null;
  }

  get subidasDelEditado(): SubidaVideo[] {
    return this.cola.items().filter((s) => s.sustituye !== null && s.sustituye === this.editando);
  }

  get urlViendo(): string {
    return this.viendo ? this.api.urlVideo(this.disciplina, this.bloqueId, this.viendo.fichero) : '';
  }

  datoVideo(v: VideoDetalle): string {
    return `${tipoFichero(v.fichero)} · ${peso(v.bytes)}`;
  }

  datoDocumento(d: DocumentoDetalle): string {
    return `${tipoFichero(d.fichero)} · ${peso(d.bytes)}`;
  }

  opcionesVideo(indice: number, ultimo: boolean): OpcionMenu[] {
    return [
      { id: 'ver', texto: 'Ver vídeo', dialogo: true },
      { id: 'editar', texto: 'Editar título', dialogo: true },
      { id: 'sustituir', texto: 'Sustituir fichero' },
      ...(this.movil
        ? [
            { id: 'subir', texto: 'Subir', deshabilitada: indice === 0 },
            { id: 'bajar', texto: 'Bajar', deshabilitada: ultimo },
          ]
        : []),
      { id: 'eliminar', texto: 'Eliminar', peligro: true, dialogo: true },
    ];
  }

  opcionesDocumento(): OpcionMenu[] {
    return [
      { id: 'ver', texto: 'Ver' },
      { id: 'sustituir', texto: 'Sustituir' },
      { id: 'eliminar', texto: 'Eliminar', peligro: true, dialogo: true },
    ];
  }

  // ------------------------------------------------------------------ ciclo de vida

  ngOnInit(): void {
    const parametros = this.ruta.snapshot.paramMap;
    this.disciplina = parametros.get('disciplina') ?? '';
    this.bloqueId = parametros.get('bloque') ?? '';
    this.cola.iniciar(this.disciplina, this.bloqueId, () => this.sello);
    this.suscripciones.add(
      this.cola.terminada.subscribe(({ nombre, detalle, inicio }) => {
        this.recibir(detalle, inicio);
        this.anunciar(`Subida terminada: ${nombre}.`);
        this.cdr.markForCheck();
      })
    );
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.consulta = window.matchMedia(CONSULTA_HOJA);
      this.consulta.addEventListener?.('change', this.alCambiarAncho);
    }
    this.cargar();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.lectura?.unsubscribe();
    this.suscripciones.unsubscribe();
    this.consulta?.removeEventListener?.('change', this.alCambiarAncho);
  }

  /** `alLeer` corre con el bloque ya en pantalla. Si una escritura se adelanta a la lectura, esta se repite. */
  cargar(alLeer?: () => void): void {
    if (this.destruido) {
      return;
    }
    this.lectura?.unsubscribe();
    this.estado = 'cargando';
    const inicio = this.sello;
    this.lectura = this.api.detalle(this.disciplina, this.bloqueId).subscribe({
      next: (detalle) => {
        if (inicio !== this.sello) {
          this.cargar(alLeer);
          return;
        }
        this.aplicar(detalle);
        this.estado = 'listo';
        alLeer?.();
        this.cdr.markForCheck();
      },
      error: () => {
        this.estado = 'error';
        this.cdr.markForCheck();
      },
    });
  }

  /** El detalle que devuelve cada escritura. Si hay un orden sin guardar, se conserva. */
  private aplicar(detalle: DetalleBloque): void {
    this.poner(detalle, this.ordenSinGuardar ? fusionarOrden(this.videos, detalle.videos) : detalle.videos);
    if (!this.ordenSinGuardar) {
      this.movido = null;
    }
  }

  private poner(detalle: DetalleBloque, videos: VideoDetalle[]): void {
    this.sello++;
    this.detalle = detalle;
    this.videos = videos;
  }

  /** La respuesta de una escritura que empezó con el sello `inicio`: si otro detalle llegó entretanto, se relee. */
  private recibir(detalle: DetalleBloque, inicio: number, alAcabar?: () => void): void {
    if (inicio === this.sello) {
      this.aplicar(detalle);
      alAcabar?.();
    } else {
      this.cargar(alAcabar);
    }
  }

  async puedeSalir(): Promise<boolean> {
    // Sin sesión no hay nada que conservar: quedarse aquí solo cancelaría la redirección al login.
    if (!this.autenticacion.comprobarLogueado()) {
      return true;
    }
    const subiendo = this.subiendo;
    const orden = this.ordenSinGuardar;
    if (!subiendo && !orden) {
      return true;
    }
    const r = await this.confirmacion.confirmar({
      titulo: subiendo ? '¿Salir y cortar la subida?' : '¿Salir sin guardar el orden?',
      mensaje: subiendo
        ? `Lo que se esté subiendo se perderá${orden ? ', y también el orden sin guardar' : ''}.`
        : 'Los vídeos volverán al orden de antes.',
      cancelar: 'Seguir aquí',
      accion: 'Salir',
      destructivo: true,
      nombre: 'salir-material',
    });
    return r.confirmado;
  }

  @HostListener('window:beforeunload', ['$event'])
  antesDeSalir(evento: BeforeUnloadEvent): void {
    if (this.subiendo || this.ordenSinGuardar) {
      evento.preventDefault();
      evento.returnValue = '';
    }
  }

  private anunciar(texto: string): void {
    void this.anunciador.announce(texto);
  }

  private enfocar(selector: string): void {
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
  }

  // ------------------------------------------------------------------ añadir vídeos

  async elegirVideos(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivos = Array.from(entrada.files ?? []);
    reiniciarSelectorArchivo(entrada);
    await this.cola.agregar(archivos);
    this.cdr.markForCheck();
  }

  alArrastrar(evento: DragEvent, encima: boolean): void {
    if (!evento.dataTransfer?.types?.includes('Files')) {
      return;
    }
    evento.preventDefault();
    const seccion = evento.currentTarget as HTMLElement | null;
    if (!encima && evento.relatedTarget instanceof Node && seccion?.contains(evento.relatedTarget)) {
      return;
    }
    this.arrastrando = encima;
  }

  async alSoltar(evento: DragEvent): Promise<void> {
    if (!evento.dataTransfer?.types?.includes('Files')) {
      return;
    }
    evento.preventDefault();
    this.arrastrando = false;
    await this.cola.agregar(Array.from(evento.dataTransfer.files ?? []));
    this.cdr.markForCheck();
  }

  cancelarSubida(clave: number): void {
    this.cola.cancelar(clave);
    this.enfocar('.material__anadir-videos');
  }

  reintentarSubida(clave: number): void {
    this.cola.reintentar(clave);
  }

  // ------------------------------------------------------------------ orden

  mover(indice: number, delta: number, mando?: MandoOrden): void {
    const v = this.videos[indice];
    if (!v || this.guardandoOrden) {
      return;
    }
    const destino = indice + delta;
    if (destino < 0 || destino >= this.videos.length) {
      this.anunciar(`«${v.titulo}» ya es el ${destino < 0 ? 'primero' : 'último'}.`);
      return;
    }
    this.aplicarOrden(indice, destino);
    if (mando) {
      this.cdr.detectChanges();
      this.enfocarMando(v.fichero, mando);
    }
  }

  teclaOrden(evento: KeyboardEvent, indice: number, mando: MandoOrden): void {
    const delta = ({ ArrowUp: -1, ArrowDown: 1 } as Record<string, number>)[evento.key];
    if (!delta) {
      return;
    }
    evento.preventDefault();
    this.mover(indice, delta, mando);
  }

  soltar(evento: CdkDragDrop<VideoDetalle[]>): void {
    if (evento.previousIndex !== evento.currentIndex && !this.guardandoOrden) {
      this.aplicarOrden(evento.previousIndex, evento.currentIndex);
    }
  }

  private aplicarOrden(desde: number, hasta: number): void {
    const v = this.videos[desde];
    this.videos = moverEn(this.videos, desde, hasta);
    this.movido = this.ordenSinGuardar ? v.fichero : null;
    this.anunciar(`«${v.titulo}» movido a la posición ${hasta + 1} de ${this.videos.length}.`);
    this.cdr.markForCheck();
  }

  private enfocarMando(fichero: string, mando: MandoOrden): void {
    const fila = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.vid')).find((f) => f.dataset['fichero'] === fichero);
    const objetivo = fila?.querySelector<HTMLButtonElement>(`.vid__${mando}`);
    if (objetivo && !objetivo.disabled) {
      objetivo.focus();
    } else {
      // En el extremo, el botón queda deshabilitado: el foco pasa al asa de la misma fila.
      fila?.querySelector<HTMLElement>('.vid__asa')?.focus();
    }
  }

  descartarOrden(): void {
    if (this.detalle) {
      this.videos = this.detalle.videos;
      this.movido = null;
      this.anunciar('Orden descartado.');
    }
  }

  guardarOrden(): void {
    if (this.guardandoOrden) {
      return;
    }
    this.guardandoOrden = true;
    const inicio = this.sello;
    this.api.ordenarVideos(this.disciplina, this.bloqueId, this.videos.map((v) => v.fichero)).subscribe({
      next: (detalle) => {
        this.guardandoOrden = false;
        if (inicio === this.sello) {
          this.poner(detalle, detalle.videos);
        } else {
          // Llegó otro detalle mientras se guardaba: de esta respuesta vale el orden, y de aquel, qué vídeos hay.
          const ultimo = this.detalle ?? detalle;
          const videos = fusionarOrden(detalle.videos, ultimo.videos);
          this.poner({ ...ultimo, videos }, videos);
          this.cargar();
        }
        this.movido = null;
        this.avisos.exito('Orden guardado.');
        this.cdr.markForCheck();
      },
      error: (error: unknown) => {
        this.guardandoOrden = false;
        this.avisos.error(mensajeErrorSubida(error, 'No se pudo guardar el orden. Inténtalo de nuevo.'));
        // La lista cambió (otra persona o una subida de esta pantalla): el mensaje de la API lo dice, y al releer el orden
        // local se conserva, con lo nuevo al final, para volver a guardarlo.
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.cargar();
        }
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ menú del vídeo

  accionVideo(v: VideoDetalle, indice: number, accion: string): void {
    switch (accion) {
      case 'ver':
        this.errorVideo = false;
        this.viendo = v;
        break;
      case 'editar':
        this.editando = v.fichero;
        break;
      case 'sustituir':
        this.ficheroASustituir = v.fichero;
        this.selectorSustituto?.nativeElement.click();
        break;
      case 'subir':
        this.mover(indice, -1);
        break;
      case 'bajar':
        this.mover(indice, 1);
        break;
      case 'eliminar':
        void this.eliminarVideo(v, indice);
        break;
    }
  }

  async elegirSustituto(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    const fichero = this.ficheroASustituir;
    reiniciarSelectorArchivo(entrada);
    this.ficheroASustituir = null;
    if (archivo && fichero) {
      await this.cola.agregar([archivo], fichero);
      this.cdr.markForCheck();
    }
  }

  async sustituirDesdePanel(archivo: File): Promise<void> {
    if (this.editando) {
      await this.cola.agregar([archivo], this.editando);
      this.cdr.markForCheck();
    }
  }

  guardarTitulo(titulo: string): void {
    const v = this.videoEditado;
    if (!v || this.guardandoTitulo) {
      return;
    }
    if (titulo === v.titulo) {
      this.editando = null;
      return;
    }
    this.guardandoTitulo = true;
    const inicio = this.sello;
    this.api.cambiarTitulo(this.disciplina, this.bloqueId, v.fichero, titulo).subscribe({
      next: (detalle) => {
        this.guardandoTitulo = false;
        this.recibir(detalle, inicio);
        this.editando = null;
        this.avisos.exito('Título guardado.');
        this.cdr.markForCheck();
      },
      error: (error: unknown) => {
        this.guardandoTitulo = false;
        this.avisos.error(mensajeErrorSubida(error, 'No se pudo guardar el título. Inténtalo de nuevo.'));
        this.cdr.markForCheck();
      },
    });
  }

  private async eliminarVideo(v: VideoDetalle, indice: number): Promise<void> {
    const r = await this.confirmacion.confirmar({
      titulo: `¿Eliminar «${v.titulo}»?`,
      mensaje: 'Las familias dejarán de verlo en todos los grados. No se puede deshacer.',
      accion: 'Sí, eliminarlo',
      cancelar: 'Cancelar',
      destructivo: true,
      nombre: 'eliminar-video',
    });
    if (!r.confirmado || this.destruido) {
      return;
    }
    this.escribir(this.api.eliminarVideo(this.disciplina, this.bloqueId, v.fichero), 'No se pudo eliminar el vídeo.', () => {
      this.avisos.exito('Vídeo eliminado.');
      this.enfocarTrasEliminar(indice);
    });
  }

  private enfocarTrasEliminar(indice: number): void {
    this.cdr.detectChanges();
    const filas = this.host.nativeElement.querySelectorAll<HTMLElement>('.vid');
    const fila = filas[Math.min(indice, filas.length - 1)];
    (fila?.querySelector<HTMLElement>('.menu__boton') ?? this.host.nativeElement.querySelector<HTMLElement>('.material__anadir-videos'))?.focus();
  }

  /** Una escritura que devuelve el detalle: lo aplica, o enseña el mensaje de la API. */
  private escribir(peticion: Observable<DetalleBloque>, porDefecto: string, alAcabar?: () => void): void {
    const inicio = this.sello;
    peticion.subscribe({
      next: (detalle) => {
        this.recibir(detalle, inicio, alAcabar);
        this.cdr.markForCheck();
      },
      error: (error: unknown) => {
        this.avisos.error(mensajeErrorSubida(error, porDefecto));
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ temario

  private async preparar(entrada: HTMLInputElement, opciones: OpcionesSubida): Promise<File | null> {
    const archivo = entrada.files?.[0];
    if (!archivo) {
      return null;
    }
    const preparado = await prepararFicheroParaSubida(archivo, opciones);
    reiniciarSelectorArchivo(entrada);
    if (!preparado.ok) {
      this.avisos.error(preparado.motivo);
      return null;
    }
    return preparado.file;
  }

  /** Con un envío ya en marcha, desde la selección, la nueva elección se ignora: no hay dos subidas a la vez. */
  private sePuedeElegir(entrada: HTMLInputElement, ocupado: boolean): boolean {
    if (!entrada.files?.length) {
      return false;
    }
    if (ocupado) {
      reiniciarSelectorArchivo(entrada);
      return false;
    }
    return true;
  }

  async elegirTemario(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    if (!this.sePuedeElegir(entrada, this.enviandoTemario)) {
      return;
    }
    this.enviandoTemario = true;
    this.cdr.markForCheck();
    const archivo = await this.preparar(entrada, SUBIDA_TEMARIO);
    if (!archivo || this.destruido) {
      this.enviandoTemario = false;
      this.cdr.markForCheck();
      return;
    }
    const inicio = this.sello;
    this.suscripciones.add(
      this.api.subirTemario(this.disciplina, this.bloqueId, archivo).subscribe({
        next: (detalle) => {
          this.enviandoTemario = false;
          this.recibir(detalle, inicio);
          this.avisos.exito('Temario guardado.');
          this.cdr.markForCheck();
        },
        error: (error: unknown) => {
          this.enviandoTemario = false;
          this.avisos.error(mensajeErrorSubida(error, 'No se pudo subir el temario. Inténtalo de nuevo.'));
          this.cdr.markForCheck();
        },
      })
    );
  }

  verTemario(evento: Event): void {
    const temario = this.detalle?.temario;
    if (!temario || this.abriendoTemario) {
      return;
    }
    this.focoRetorno = evento.currentTarget instanceof HTMLElement ? evento.currentTarget : null;
    this.abriendoTemario = true;
    this.api.temario(this.disciplina, this.bloqueId).subscribe({
      next: (blob) => {
        this.abriendoTemario = false;
        this.documentosPdf = [{ blob, nombreArchivo: temario.nombre, titulo: `Temario · ${this.detalle?.nombre ?? ''}` }];
        this.cdr.markForCheck();
      },
      error: () => {
        this.abriendoTemario = false;
        this.avisos.error('No se pudo abrir el temario. Inténtalo de nuevo.');
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ documentación

  async elegirDocumento(evento: Event): Promise<void> {
    const archivo = await this.prepararDocumento(evento.target as HTMLInputElement);
    if (archivo) {
      this.enviarDocumento(this.api.subirDocumento(this.disciplina, this.bloqueId, archivo), (r) => r.bloque, 'Documento añadido.');
    }
  }

  async elegirDocumentoSustituto(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const guardado = this.documentoASustituir;
    this.documentoASustituir = null;
    const elegido = entrada.files?.[0];
    if (!guardado || !elegido) {
      reiniciarSelectorArchivo(entrada);
      return;
    }
    const motivo = motivoDocumentoSustituto(guardado, elegido.name);
    if (motivo) {
      reiniciarSelectorArchivo(entrada);
      this.avisos.error(motivo);
      return;
    }
    const archivo = await this.prepararDocumento(entrada);
    if (archivo) {
      this.enviarDocumento(this.api.sustituirDocumento(this.disciplina, this.bloqueId, guardado, archivo), (d) => d, 'Documento sustituido.');
    }
  }

  /** Prepara con el bloqueo puesto desde la selección; solo sigue puesto si hay fichero que enviar. */
  private async prepararDocumento(entrada: HTMLInputElement): Promise<File | null> {
    if (!this.sePuedeElegir(entrada, this.enviandoDocumento)) {
      return null;
    }
    this.enviandoDocumento = true;
    this.cdr.markForCheck();
    const archivo = await this.preparar(entrada, SUBIDA_DOCUMENTO);
    if (archivo && !this.destruido) {
      return archivo;
    }
    this.enviandoDocumento = false;
    this.cdr.markForCheck();
    return null;
  }

  private enviarDocumento<T>(peticion: Observable<T>, aDetalle: (respuesta: T) => DetalleBloque, exito: string): void {
    const inicio = this.sello;
    this.suscripciones.add(
      peticion.subscribe({
        next: (respuesta) => {
          this.enviandoDocumento = false;
          this.recibir(aDetalle(respuesta), inicio);
          this.avisos.exito(exito);
          this.cdr.markForCheck();
        },
        error: (error: unknown) => {
          this.enviandoDocumento = false;
          this.avisos.error(mensajeErrorSubida(error, 'No se pudo subir el documento. Inténtalo de nuevo.'));
          this.cdr.markForCheck();
        },
      })
    );
  }

  accionDocumento(d: DocumentoDetalle, accion: string): void {
    if (accion === 'ver') {
      this.verDocumento(d);
    } else if (accion === 'sustituir') {
      this.documentoASustituir = d.fichero;
      this.selectorDocumentoSustituto?.nativeElement.click();
    } else if (accion === 'eliminar') {
      void this.eliminarDocumento(d);
    }
  }

  /** La API solo abre el PDF en el navegador; el resto se descarga. */
  private verDocumento(d: DocumentoDetalle): void {
    const esPdf = (d.mime ?? '').toLowerCase().startsWith('application/pdf') || tipoFichero(d.fichero) === 'PDF';
    this.api.documento(this.disciplina, this.bloqueId, d.fichero).subscribe({
      next: (blob) => {
        const documento: DocumentoInforme = { blob, nombreArchivo: d.fichero, titulo: d.titulo };
        if (esPdf) {
          this.focoRetorno = null;
          this.documentosPdf = [documento];
        } else {
          descargarDocumentoInforme(documento);
        }
        this.cdr.markForCheck();
      },
      error: () => {
        this.avisos.error('No se pudo abrir el documento. Inténtalo de nuevo.');
        this.cdr.markForCheck();
      },
    });
  }

  private async eliminarDocumento(d: DocumentoDetalle): Promise<void> {
    const r = await this.confirmacion.confirmar({
      titulo: `¿Eliminar «${d.titulo}»?`,
      mensaje: 'Las familias dejarán de verlo. No se puede deshacer.',
      accion: 'Sí, eliminarlo',
      cancelar: 'Cancelar',
      destructivo: true,
      nombre: 'eliminar-documento',
    });
    if (r.confirmado && !this.destruido) {
      this.escribir(this.api.eliminarDocumento(this.disciplina, this.bloqueId, d.fichero), 'No se pudo eliminar el documento.', () =>
        this.avisos.exito('Documento eliminado.')
      );
    }
  }
}
