import { DOCUMENT } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';

import { TemaWeb } from '../../../web-plantilla/datos/modelos';
import {
  CANAL_EDITOR_WEB,
  MensajeDelEditor,
  enviarPorPuente,
  esMensajeDelEditor,
  escucharPuente,
} from '../../../web-plantilla/editor/puente-editor';
import { AcentoEscuelaDirective } from '../../acento-escuela.directive';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { LIMITE_CARGA_PREVIA, RETRASO_ESQUELETO_PREVIA, TEXTOS_WEB_TEMA } from '../tema/web-tema-vista';
import {
  ALTO_INICIAL,
  ANCHO_VISTA,
  AnchoVista,
  BloqueContenido,
  ORIGEN_EDITOR,
  RUTA_DESTINO,
  TEXTOS_WEB_CONTENIDO,
  acentoDe,
  bloquesParaVistaPrevia,
  direccionEditor,
  escalaVista,
  nombrePublicada,
  temaDeLaVista,
  textoMarco,
  vistaInicial,
} from './web-contenido-vista';
import { WebContenidoService } from './web-contenido.service';

const VISTAS: readonly AnchoVista[] = ['movil', 'escritorio'];

/**
 * «Web · Contenido» (W12c; boceto `docs/multi-escuela/fase4-web/boceto-panel-contenido-web-comarcal.html`, plan
 * `fase4-w12-plan.md`): armazón de la pantalla y vista previa de Inicio en modo editor, con los candados de los datos
 * que se cambian en otras pantallas. Aún no edita ni guarda (W12d-W12f) y no tiene entrada de menú (W12d). Solo el
 * propietario y nunca Moiskimdo (`adminOnlyGuard`, `sinLegadoTaemoiGuard`).
 *
 * Puente con el iframe (`web-plantilla/editor/puente-editor.ts`): solo atiende mensajes del mismo origen, de su
 * iframe y con forma conocida; manda el borrador (lo leído de la API) cuando la capa avisa de que está lista, ajusta
 * el alto del marco y navega a la pantalla que pide un candado.
 */
@Component({
  selector: 'pn-web-contenido',
  standalone: true,
  imports: [AcentoEscuelaDirective, BloqueErrorComponent, EsqueletoComponent, RouterLink],
  providers: [WebContenidoService],
  templateUrl: './web-contenido.component.html',
  styleUrl: './web-contenido.component.scss',
})
export class WebContenidoComponent implements OnInit {
  private readonly servicio = inject(WebContenidoService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly router = inject(Router);
  private readonly origen = inject(ORIGEN_EDITOR);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly ventana = inject(DOCUMENT).defaultView;

  readonly textos = TEXTOS_WEB_CONTENIDO;
  readonly textosVista = TEXTOS_WEB_TEMA;
  readonly vistas = VISTAS;

  readonly estado = signal<'cargando' | 'error' | 'listo'>('cargando');
  /** «Publicada con «tema»» o null si la web aún no está publicada (estado «nueva»). */
  readonly publicada = signal<string | null>(null);
  readonly tema = signal<TemaWeb | null>(null);
  /** El borrador: por ahora, lo leído de `GET /api/escuela/web/contenido`. */
  readonly borrador = signal<BloqueContenido[]>([]);
  readonly vista = signal<AnchoVista>(vistaInicial(this.ventana));
  readonly carga = signal<'cargando' | 'listo' | 'error'>('cargando');
  readonly esqueleto = signal(false);
  readonly anuncio = signal('');
  readonly disponible = signal(0);
  readonly alto = signal(ALTO_INICIAL);

  readonly ancho = computed(() => ANCHO_VISTA[this.vista()]);
  readonly escala = computed(() => escalaVista(this.disponible(), this.ancho()));
  readonly anchoLienzo = computed(() => Math.floor(this.ancho() * this.escala()));
  readonly altoLienzo = computed(() => Math.ceil(this.alto() * this.escala()));
  readonly info = computed(() => textoMarco(this.vista(), this.escala()));
  readonly src = computed<SafeResourceUrl | null>(() => {
    const tema = this.tema();
    // Ruta relativa del mismo origen construida aquí, nunca con datos del usuario sin codificar.
    return tema === null ? null : this.sanitizer.bypassSecurityTrustResourceUrl(this.origen + direccionEditor(tema));
  });

  private readonly iframe = viewChild<ElementRef<HTMLIFrameElement>>('iframe');
  private readonly marcoVista = viewChild<ElementRef<HTMLElement>>('marcoVista');
  private observador: ResizeObserver | null = null;
  private temporizadorEsqueleto: ReturnType<typeof setTimeout> | null = null;
  private temporizadorLimite: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => this.observar(this.marcoVista()?.nativeElement ?? null));
    const dejarDeEscuchar = this.ventana
      ? escucharPuente(this.ventana, () => this.iframe()?.nativeElement.contentWindow ?? null, esMensajeDelEditor, (m) =>
          this.alRecibir(m),
        )
      : () => undefined;
    inject(DestroyRef).onDestroy(() => {
      dejarDeEscuchar();
      this.pararTemporizadores();
      this.observador?.disconnect();
    });
  }

  ngOnInit(): void {
    this.cargar();
    afterNextRender(() => this.enfocarTitulo(), { injector: this.injector });
  }

  cargar(): void {
    this.estado.set('cargando');
    this.servicio.obtener().subscribe({
      next: ({ estado, contenido }) => {
        this.publicada.set(nombrePublicada(estado));
        this.tema.set(temaDeLaVista(estado));
        this.borrador.set(contenido.bloques);
        this.alto.set(ALTO_INICIAL);
        this.estado.set('listo');
        this.iniciarCarga();
      },
      error: () => this.estado.set('error'),
    });
  }

  /** «Reintentar» del error de la pantalla: vuelve a leer y el foco vuelve al título. */
  reintentar(): void {
    this.cargar();
    this.enfocarTitulo();
  }

  cambiarVista(vista: AnchoVista): void {
    // El iframe es el mismo: cambia de ancho sin recargar.
    this.vista.set(vista);
  }

  // ------------------------------------------------------------------ puente

  private alRecibir(mensaje: MensajeDelEditor): void {
    switch (mensaje.tipo) {
      case 'listo':
        this.enviarBorrador();
        break;
      case 'alto':
        this.alto.set(Math.max(1, Math.ceil(mensaje.alto)));
        break;
      case 'ir':
        void this.router.navigateByUrl(RUTA_DESTINO[mensaje.destino]);
        break;
    }
  }

  private enviarBorrador(): void {
    if (!this.ventana) {
      return;
    }
    const acento = acentoDe(getComputedStyle(this.host.nativeElement.querySelector('.web-contenido') ?? this.host.nativeElement));
    enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, {
      canal: CANAL_EDITOR_WEB,
      tipo: 'borrador',
      bloques: bloquesParaVistaPrevia(this.borrador()),
      ...(acento ? { acento } : {}),
    });
  }

  // ------------------------------------------------------------------ vista previa (como «Web · Tema»)

  private iniciarCarga(): void {
    this.pararTemporizadores();
    this.carga.set('cargando');
    this.esqueleto.set(false);
    this.temporizadorEsqueleto = setTimeout(() => {
      this.temporizadorEsqueleto = null;
      if (this.carga() === 'cargando') {
        this.esqueleto.set(true);
        this.anuncio.set(this.textosVista.cargandoVista);
      }
    }, RETRASO_ESQUELETO_PREVIA);
    this.temporizadorLimite = setTimeout(() => {
      this.temporizadorLimite = null;
      if (this.carga() === 'cargando') {
        this.pararTemporizadores();
        this.esqueleto.set(false);
        this.carga.set('error');
        this.anuncio.set('');
      }
    }, LIMITE_CARGA_PREVIA);
  }

  /** `load` del iframe; el del `about:blank` inicial (sin `src`) no cuenta. */
  alCargar(evento: Event): void {
    const iframe = evento.target as HTMLIFrameElement | null;
    if (this.carga() !== 'cargando' || !iframe?.getAttribute('src')) {
      return;
    }
    this.pararTemporizadores();
    this.esqueleto.set(false);
    this.carga.set('listo');
    this.anuncio.set('');
  }

  reintentarVista(): void {
    this.alto.set(ALTO_INICIAL);
    this.iniciarCarga();
    this.marcoVista()?.nativeElement.focus();
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

  private enfocarTitulo(): void {
    this.host.nativeElement.querySelector<HTMLElement>('h1')?.focus();
  }
}
