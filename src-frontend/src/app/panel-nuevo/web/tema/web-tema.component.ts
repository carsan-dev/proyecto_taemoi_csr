import {
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  InjectionToken,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { TemaWeb } from '../../../web-plantilla/datos/modelos';
import { AcentoEscuelaDirective } from '../../acento-escuela.directive';
import { AvisosService } from '../../avisos/avisos.service';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { MarcaEscuelaPanelService } from '../../marca-escuela-panel.service';
import { textoSalirSinGuardar } from '../../productos-alumno/productos-alumno-vista';
import { ConCambiosSinGuardar } from '../../productos-alumno/salir-sin-guardar.guard';
import {
  Dispositivo,
  EstadoWebEscuela,
  FichaTema,
  IdPaginaPrevia,
  LIMITE_CARGA_PREVIA,
  MARCO,
  PAGINAS_PREVIA,
  RETRASO_ESQUELETO_PREVIA,
  TEXTOS_WEB_TEMA,
  VIEWPORT,
  direccionVistaPrevia,
  entreComillas,
  escala,
  hayCambiosSinPublicar,
  modoBarra,
  nombreTema,
  temaInicial,
  temaPublicado,
  temasVisibles,
  textoPublicada,
  textoVistaLista,
  textosPublicar,
  tituloVistaPrevia,
} from './web-tema-vista';
import { WebTemaService } from './web-tema.service';

/**
 * Lo que va delante de la ruta de la plantilla en el `src` del iframe: vacío (mismo origen). Las pruebas lo cambian
 * para que el iframe no cargue la página de Karma.
 */
export const ORIGEN_VISTA_PREVIA = new InjectionToken<string>('ORIGEN_VISTA_PREVIA', { factory: () => '' });

const DISPOSITIVOS: readonly Dispositivo[] = ['movil', 'escritorio'];

/**
 * «Web · Tema» (W7; boceto `docs/multi-escuela/fase4-web/boceto-panel-tema-web.html`, fase4-web-plan.md §3.3): el
 * propietario elige el tema de la web por plantilla, la ve en un iframe del mismo origen (`/<página>?vista-previa=`)
 * en móvil o escritorio y la publica con un diálogo. Solo el propietario y nunca Moiskimdo (`adminOnlyGuard` y
 * `sinLegadoTaemoiGuard`; la API da 403 y 409). El tema elegido y no publicado vive solo en la pantalla (§9 n.º 2).
 */
@Component({
  selector: 'pn-web-tema',
  standalone: true,
  imports: [AcentoEscuelaDirective, BloqueErrorComponent, EsqueletoComponent],
  providers: [WebTemaService],
  templateUrl: './web-tema.component.html',
  styleUrl: './web-tema.component.scss',
})
export class WebTemaComponent implements OnInit, ConCambiosSinGuardar {
  private readonly servicio = inject(WebTemaService);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly marcaServicio = inject(MarcaEscuelaPanelService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly origen = inject(ORIGEN_VISTA_PREVIA);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly textos = TEXTOS_WEB_TEMA;
  readonly paginas = PAGINAS_PREVIA;
  readonly dispositivos = DISPOSITIVOS;

  readonly estado = signal<'cargando' | 'error' | 'listo'>('cargando');
  readonly publicado = signal<TemaWeb | null>(null);
  readonly elegido = signal<TemaWeb>(temaInicial(null));
  readonly dispositivo = signal<Dispositivo>(dispositivoInicial());
  readonly pagina = signal<IdPaginaPrevia>('portada');
  /** Slug de la primera sede con página; null mientras no llega, sin sedes o con error («Una sede» deshabilitada). */
  readonly slug = signal<string | null>(null);
  readonly vista = signal<'cargando' | 'listo' | 'error'>('cargando');
  /** El esqueleto de la vista previa: solo si tarda más de 300 ms. */
  readonly esqueleto = signal(false);
  /** Región viva de la vista previa (`role=status`). */
  readonly anuncio = signal('');
  readonly publicando = signal(false);
  /** Ancho útil del escenario (sin su relleno), para escalar la vista previa. */
  readonly disponible = signal(0);
  readonly logo = signal<string | null>(null);
  readonly nombreEscuela = signal('la escuela');
  readonly color = signal<string | null>(null);

  readonly temas = computed<FichaTema[]>(() => temasVisibles(this.publicado()));
  readonly modo = computed(() => modoBarra(this.publicado(), this.elegido()));
  readonly hayCambios = computed(() => hayCambiosSinPublicar(this.publicado(), this.elegido()));
  readonly movil = computed(() => this.dispositivo() === 'movil');
  readonly direccion = computed(() => direccionVistaPrevia(this.pagina(), this.elegido(), this.slug()));
  readonly src = computed<SafeResourceUrl | null>(() => {
    const direccion = this.direccion();
    // Ruta relativa del mismo origen construida aquí, nunca con datos del usuario sin codificar.
    return direccion === null ? null : this.sanitizer.bypassSecurityTrustResourceUrl(this.origen + direccion);
  });
  readonly titulo = computed(() => tituloVistaPrevia(this.elegido(), this.pagina(), this.dispositivo()));
  readonly ventana = computed(() => VIEWPORT[this.dispositivo()]);
  readonly anchoMarco = computed(() => this.ventana().ancho + MARCO[this.dispositivo()]);
  readonly altoMarco = computed(() => this.ventana().alto + MARCO[this.dispositivo()]);
  readonly factor = computed(() => escala(this.disponible(), this.anchoMarco()));

  private readonly escenario = viewChild<ElementRef<HTMLElement>>('escenario');
  private observador: ResizeObserver | null = null;
  private temporizadorEsqueleto: ReturnType<typeof setTimeout> | null = null;
  private temporizadorLimite: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => this.observar(this.escenario()?.nativeElement ?? null));
    inject(DestroyRef).onDestroy(() => {
      this.pararTemporizadores();
      this.observador?.disconnect();
    });
  }

  ngOnInit(): void {
    this.cargar();
    this.marcaServicio.marca().subscribe({
      next: (marca) => {
        this.logo.set(this.marcaServicio.urlLogo(marca));
        this.color.set(marca.tema.primario);
        this.nombreEscuela.set(marca.nombre);
      },
      error: () => undefined,
    });
  }

  cargar(): void {
    this.estado.set('cargando');
    this.servicio.obtener().subscribe({
      next: (estado) => {
        this.aplicar(estado);
        this.estado.set('listo');
        this.iniciarCarga();
        this.servicio.slugPrimeraSede(this.elegido()).subscribe((slug) => this.slug.set(slug));
      },
      error: () => this.estado.set('error'),
    });
  }

  private aplicar(estado: EstadoWebEscuela): void {
    const publicado = temaPublicado(estado);
    this.publicado.set(publicado);
    this.elegido.set(temaInicial(publicado));
  }

  nombre(tema: TemaWeb): string {
    return nombreTema(tema);
  }

  comillas(tema: TemaWeb): string {
    return entreComillas(nombreTema(tema));
  }

  // ------------------------------------------------------------------ temas y conmutadores (grupos de radio)

  elegir(tema: TemaWeb, foco = false): void {
    if (tema !== this.elegido()) {
      this.elegido.set(tema);
      this.iniciarCarga();
    }
    if (foco) {
      this.enfocar(`[data-tema="${tema}"]`);
    }
  }

  cambiarDispositivo(dispositivo: Dispositivo, foco = false): void {
    // El iframe es el mismo: cambia de tamaño sin recargar.
    this.dispositivo.set(dispositivo);
    if (foco) {
      this.enfocar(`[data-dispositivo="${dispositivo}"]`);
    }
  }

  cambiarPagina(evento: Event): void {
    const pagina = (evento.target as HTMLSelectElement).value as IdPaginaPrevia;
    if (pagina !== this.pagina()) {
      this.pagina.set(pagina);
      this.iniciarCarga();
    }
  }

  /** Flechas, Inicio y Fin mueven la elección; Espacio e Intro eligen el que tiene el foco (grupo `role=radiogroup`). */
  teclaTema(evento: KeyboardEvent, tema: TemaWeb): void {
    const ids = this.temas().map((t) => t.id);
    const destino = siguiente(evento, ids, tema);
    if (destino !== null) {
      this.elegir(destino, true);
    }
  }

  teclaDispositivo(evento: KeyboardEvent, dispositivo: Dispositivo): void {
    const destino = siguiente(evento, DISPOSITIVOS, dispositivo);
    if (destino !== null) {
      this.cambiarDispositivo(destino, true);
    }
  }

  descartar(): void {
    const publicado = this.publicado();
    if (publicado === null || this.publicando()) {
      return;
    }
    this.elegir(publicado, true);
  }

  // ------------------------------------------------------------------ vista previa

  private iniciarCarga(): void {
    this.pararTemporizadores();
    this.vista.set('cargando');
    this.esqueleto.set(false);
    this.temporizadorEsqueleto = setTimeout(() => {
      this.temporizadorEsqueleto = null;
      if (this.vista() === 'cargando') {
        this.esqueleto.set(true);
        this.anuncio.set(this.textos.cargandoVista);
      }
    }, RETRASO_ESQUELETO_PREVIA);
    this.temporizadorLimite = setTimeout(() => {
      this.temporizadorLimite = null;
      if (this.vista() === 'cargando') {
        this.pararTemporizadores();
        this.esqueleto.set(false);
        this.vista.set('error');
        this.anuncio.set('');
      }
    }, LIMITE_CARGA_PREVIA);
  }

  /** `load` del iframe; el del `about:blank` inicial (sin `src`) no cuenta. */
  alCargar(evento: Event): void {
    const iframe = evento.target as HTMLIFrameElement | null;
    if (this.vista() !== 'cargando' || !iframe?.getAttribute('src')) {
      return;
    }
    this.pararTemporizadores();
    this.esqueleto.set(false);
    this.vista.set('listo');
    this.anuncio.set(textoVistaLista(this.elegido(), this.pagina()));
  }

  /** «Reintentar»: el iframe se vuelve a crear (y a pedir) y el foco va al escenario. */
  reintentarVista(): void {
    this.iniciarCarga();
    this.escenario()?.nativeElement.focus();
  }

  private pararTemporizadores(): void {
    if (this.temporizadorEsqueleto !== null) {
      clearTimeout(this.temporizadorEsqueleto);
      this.temporizadorEsqueleto = null;
    }
    if (this.temporizadorLimite !== null) {
      clearTimeout(this.temporizadorLimite);
      this.temporizadorLimite = null;
    }
  }

  private observar(elemento: HTMLElement | null): void {
    this.observador?.disconnect();
    this.observador = null;
    if (!elemento || typeof ResizeObserver === 'undefined') {
      return;
    }
    const medir = () => {
      const estilo = getComputedStyle(elemento);
      this.disponible.set(elemento.clientWidth - parseFloat(estilo.paddingLeft) - parseFloat(estilo.paddingRight));
    };
    this.observador = new ResizeObserver(medir);
    this.observador.observe(elemento);
    medir();
  }

  // ------------------------------------------------------------------ publicar

  async publicar(): Promise<void> {
    if (this.publicando() || this.modo() === 'oculta') {
      return;
    }
    const tema = this.elegido();
    const textos = textosPublicar(tema, this.publicado());
    this.publicando.set(true);
    const resultado = await this.confirmacion.confirmar<EstadoWebEscuela>({
      titulo: textos.titulo,
      mensaje: textos.mensaje,
      bloques: textos.bloques,
      accion: 'Publicar',
      accionOcupada: 'Publicando…',
      cancelar: 'Cancelar',
      nombre: 'publicar-web',
      ejecutar: () => this.servicio.publicar(tema),
    });
    this.publicando.set(false);
    if (!resultado.confirmado) {
      return;
    }
    if (resultado.error !== undefined || !resultado.respuesta) {
      this.avisos.error(`${this.textos.errorPublicar} ${mensajeDeError(resultado.error) ?? this.textos.errorPublicarDetalle}`);
      if ((resultado.error as { status?: number } | null | undefined)?.status === 409) {
        // Publicación simultánea: se vuelve a leer lo publicado para que la pantalla lo refleje (la elección se mantiene).
        this.servicio.obtener().subscribe({ next: (estado) => this.publicado.set(temaPublicado(estado)), error: () => undefined });
      }
      return;
    }
    const publicado = temaPublicado(resultado.respuesta) ?? tema;
    this.publicado.set(publicado);
    if (this.elegido() !== publicado) {
      this.elegir(publicado);
    }
    this.avisos.exito(textoPublicada(publicado));
    this.enfocar(`[data-tema="${publicado}"]`);
  }

  private enfocar(selector: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
  }

  // ------------------------------------------------------------------ salir sin publicar

  /** `canDeactivate` (`salirSinGuardarGuard`): con otro tema elegido y la web publicada, «¿Salir sin guardar?». */
  async puedeSalir(): Promise<boolean> {
    if (!this.hayCambios()) {
      return true;
    }
    const r = await this.confirmacion.confirmar({
      titulo: '¿Salir sin guardar?',
      mensaje: textoSalirSinGuardar(1),
      accion: 'Salir sin guardar',
      cancelar: 'Seguir editando',
      destructivo: true,
      nombre: 'salir-sin-guardar',
    });
    return r.confirmado;
  }

  @HostListener('window:beforeunload', ['$event'])
  antesDeDescargar(evento: BeforeUnloadEvent): void {
    if (this.hayCambios()) {
      evento.preventDefault();
      evento.returnValue = '';
    }
  }
}

/** Escritorio si la ventana mide al menos 1024 px; si no, móvil. */
function dispositivoInicial(): Dispositivo {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function' && window.matchMedia('(min-width: 1024px)').matches
    ? 'escritorio'
    : 'movil';
}

/** El destino de una tecla en un grupo de radio; null si la tecla no es suya (y entonces no se cancela). */
function siguiente<T>(evento: KeyboardEvent, valores: readonly T[], actual: T): T | null {
  const i = valores.indexOf(actual);
  let n: number | null = null;
  switch (evento.key) {
    case 'ArrowDown':
    case 'ArrowRight':
      n = (i + 1) % valores.length;
      break;
    case 'ArrowUp':
    case 'ArrowLeft':
      n = (i - 1 + valores.length) % valores.length;
      break;
    case 'Home':
      n = 0;
      break;
    case 'End':
      n = valores.length - 1;
      break;
    case ' ':
    case 'Enter':
      n = i;
      break;
  }
  if (n === null) {
    return null;
  }
  evento.preventDefault();
  return valores[n];
}
