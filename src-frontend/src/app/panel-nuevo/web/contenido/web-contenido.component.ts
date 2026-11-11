import { DOCUMENT } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { TemaWeb } from '../../../web-plantilla/datos/modelos';
import type { PaginaEditor } from '../../../web-plantilla/editor/candados-linea-comarcal';
import {
  CANAL_EDITOR_WEB,
  CasoVista,
  MensajeDelEditor,
  PasoPreinscripcion,
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
  CASOS_PAGINA,
  CasoPagina,
  ORIGEN_EDITOR,
  OpcionSub,
  PAGINAS_CONTENIDO,
  PASOS_CONTENIDO,
  RUTA_DESTINO,
  TEXTOS_WEB_CONTENIDO,
  acentoDe,
  bloquesParaVistaPrevia,
  direccionEditor,
  escalaVista,
  esPaginaContenido,
  nombrePagina,
  nombrePublicada,
  notaPagina,
  opcionesEvento,
  opcionesSede,
  primeraOpcion,
  temaDeLaVista,
  textoMarco,
  tituloMenuCasos,
  vistaInicial,
  vozCaso,
  vozPagina,
} from './web-contenido-vista';
import { ListasVista, WebContenidoService } from './web-contenido.service';

/** La barra superior del panel (`navegacion/cabecera-panel`): 64 px y 56 en móvil; la franja del caso va debajo. */
const CABECERA_PANEL = '.pn-cab';

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
 *
 * W12c-bis: selector de página (d519, con su segundo selector en «Una sede» y «Un evento»; la página va en la dirección,
 * `?pagina=`) y «Ver el caso» (d528): el caso se manda por el puente y lo simula la capa dentro del iframe; ni la página
 * ni el caso llegan a la API.
 *
 * W12c-ter (d533): en Preinscripción, el segundo selector es «Paso» (boceto `SUBS.preinscripcion`); el paso va por el
 * puente (un número de la lista cerrada) sin recargar el iframe, y la capa enseña ese paso con datos de ejemplo. Sus
 * tres casos llevan cada uno a su paso; cambiar a otro paso quita el caso (como el boceto).
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
  private readonly ruta = inject(ActivatedRoute);

  readonly textos = TEXTOS_WEB_CONTENIDO;
  readonly textosVista = TEXTOS_WEB_TEMA;
  readonly vistas = VISTAS;
  readonly paginas = PAGINAS_CONTENIDO;

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

  // ------------------------------------------------------------------ página y caso (W12c-bis)
  readonly pagina = signal<PaginaEditor>(this.paginaDeLaDireccion());
  /** Sedes y eventos para «Una sede» y «Un evento»; undefined mientras se leen, null si fallan. */
  readonly listas = signal<ListasVista | null | undefined>(undefined);
  private readonly elegida = signal<Partial<Record<'sede' | 'evento', string>>>({});
  readonly caso = signal<CasoVista | null>(null);
  /** El paso de la preinscripción que se ve (boceto: empieza en el 1 y se queda al cambiar de página). */
  readonly paso = signal<PasoPreinscripcion>(1);
  readonly menuCasos = signal(false);

  readonly opcionesSub = computed<OpcionSub[]>(() => {
    const listas = this.listas();
    switch (this.pagina()) {
      case 'sede':
        return listas ? opcionesSede(listas.sedes) : [];
      case 'evento':
        return listas ? opcionesEvento(listas.eventos) : [];
      case 'preinscripcion':
        return [...PASOS_CONTENIDO];
      default:
        return [];
    }
  });
  readonly conSub = computed(() => this.pagina() === 'sede' || this.pagina() === 'evento' || this.pagina() === 'preinscripcion');
  readonly etiquetaSub = computed(() => {
    switch (this.pagina()) {
      case 'sede':
        return this.textos.etiquetaSede;
      case 'preinscripcion':
        return this.textos.etiquetaPaso;
      default:
        return this.textos.etiquetaEvento;
    }
  });
  /** La sede o el evento que se ve: el elegido o, si no, el primero (boceto: la primera sede y el primer evento). */
  readonly sub = computed<string | null>(() => {
    const pagina = this.pagina();
    if (pagina !== 'sede' && pagina !== 'evento') {
      return null;
    }
    const opciones = this.opcionesSub();
    const elegida = this.elegida()[pagina];
    return opciones.some((o) => o.valor === elegida && !o.desactivada) ? elegida! : primeraOpcion(opciones);
  });
  /** «Una sede» y «Un evento» no se eligen sin ninguna sede con página o ningún evento (como «Una sede» en «Web · Tema»). */
  readonly paginaDisponible = computed(() => {
    const listas = this.listas();
    return {
      sede: !!listas && primeraOpcion(opcionesSede(listas.sedes)) !== null,
      evento: !!listas && listas.eventos.length > 0,
    };
  });
  /** Lo elegido en el segundo selector: la sede, el evento o el paso (este no cambia la dirección del iframe). */
  readonly valorSub = computed(() => (this.pagina() === 'preinscripcion' ? String(this.paso()) : this.sub()));
  readonly rotuloPagina = computed(() => {
    const nombre = nombrePagina(this.pagina());
    const sub = this.opcionesSub().find((o) => o.valor === this.valorSub());
    return sub ? `${nombre} · ${sub.texto}` : nombre;
  });
  readonly nota = computed(() => {
    const sedes = this.listas()?.sedes ?? [];
    return notaPagina(
      this.pagina(),
      sedes.filter((s) => !s.slug).map((s) => s.nombre),
    );
  });
  readonly casos = computed<readonly CasoPagina[]>(() => CASOS_PAGINA[this.pagina()]);
  readonly casoActivo = computed(() => this.casos().find((c) => c.id === this.caso()) ?? null);
  readonly tituloMenu = computed(() => tituloMenuCasos(this.pagina()));

  readonly ancho = computed(() => ANCHO_VISTA[this.vista()]);
  readonly escala = computed(() => escalaVista(this.disponible(), this.ancho()));
  readonly anchoLienzo = computed(() => Math.floor(this.ancho() * this.escala()));
  readonly altoLienzo = computed(() => Math.ceil(this.alto() * this.escala()));
  readonly info = computed(() => textoMarco(this.vista(), this.escala()));
  readonly src = computed<SafeResourceUrl | null>(() => {
    const tema = this.tema();
    // Ruta relativa del mismo origen construida aquí; el slug y el id van codificados (`direccionEditor`).
    const direccion = tema === null ? null : direccionEditor(tema, this.pagina(), this.sub());
    return direccion === null ? null : this.sanitizer.bypassSecurityTrustResourceUrl(this.origen + direccion);
  });

  private readonly iframe = viewChild<ElementRef<HTMLIFrameElement>>('iframe');
  private readonly marcoVista = viewChild<ElementRef<HTMLElement>>('marcoVista');
  private readonly botonCasos = viewChild<ElementRef<HTMLButtonElement>>('botonCasos');
  private readonly menu = viewChild<ElementRef<HTMLElement>>('menu');
  private readonly casoActivoEl = viewChild<ElementRef<HTMLElement>>('casoActivoEl');
  /** Tras elegir un caso, se enseña su parte cuando la capa dice dónde está. */
  private mostrarCaso = false;
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

  cargar(anunciar = false): void {
    this.estado.set('cargando');
    this.servicio.obtener().subscribe({
      next: ({ estado, contenido }) => {
        this.publicada.set(nombrePublicada(estado));
        this.tema.set(temaDeLaVista(estado));
        this.leerListas(temaDeLaVista(estado));
        this.borrador.set(contenido.bloques);
        this.alto.set(ALTO_INICIAL);
        this.estado.set('listo');
        this.iniciarCarga();
        if (anunciar) {
          this.anuncio.set(this.textos.cargado);
        }
      },
      error: () => this.estado.set('error'),
    });
  }

  /** «Reintentar» del error de la pantalla: vuelve a leer, el foco vuelve al título y se anuncia «Contenido cargado.». */
  reintentar(): void {
    this.cargar(true);
    this.enfocarTitulo();
  }

  cambiarVista(vista: AnchoVista): void {
    // El iframe es el mismo: cambia de ancho sin recargar.
    this.vista.set(vista);
  }

  // ------------------------------------------------------------------ selector de página (d519)

  private paginaDeLaDireccion(): PaginaEditor {
    const pedida = this.ruta.snapshot.queryParamMap.get('pagina');
    return esPaginaContenido(pedida) ? pedida : 'inicio';
  }

  private leerListas(tema: TemaWeb): void {
    this.listas.set(undefined);
    this.servicio.listas(tema).subscribe((listas) => {
      this.listas.set(listas);
      // Llegó de la dirección a una página que no se puede ver: Inicio.
      const pagina = this.pagina();
      if ((pagina === 'sede' || pagina === 'evento') && !this.paginaDisponible()[pagina]) {
        this.ponerPagina('inicio');
      }
    });
  }

  /** Al cambiar de página: se quita la selección (el iframe se recarga) y el caso; se dice la página por voz. */
  cambiarPagina(evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    if (!esPaginaContenido(valor) || valor === this.pagina()) {
      return;
    }
    this.ponerPagina(valor);
    this.anuncio.set(vozPagina(valor));
  }

  cambiarSub(evento: Event): void {
    const pagina = this.pagina();
    const valor = (evento.target as HTMLSelectElement).value;
    if (pagina === 'preinscripcion') {
      this.cambiarPaso(valor);
      return;
    }
    if (pagina !== 'sede' && pagina !== 'evento') {
      return;
    }
    if (!this.opcionesSub().some((o) => o.valor === valor && !o.desactivada) || valor === this.sub()) {
      return;
    }
    this.elegida.update((e) => ({ ...e, [pagina]: valor }));
    this.recargarVista();
  }

  /** «Paso» (W12c-ter): va por el puente, sin recargar; si el caso que se ve es de otro paso, se quita (boceto). */
  private cambiarPaso(valor: string): void {
    const paso = PASOS_CONTENIDO.find((p) => p.valor === valor)?.paso;
    if (paso === undefined || paso === this.paso()) {
      return;
    }
    this.paso.set(paso);
    this.enviarPaso();
    const caso = this.casoActivo();
    if (caso?.paso !== undefined && caso.paso !== paso) {
      this.caso.set(null);
      this.mostrarCaso = false;
      this.enviarCaso();
    }
  }

  private ponerPagina(pagina: PaginaEditor): void {
    this.pagina.set(pagina);
    this.caso.set(null);
    this.menuCasos.set(false);
    this.recargarVista();
    // Como el boceto, la página va en la dirección (sin guardarse en la API).
    void this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: { pagina: pagina === 'inicio' ? null : pagina },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private recargarVista(): void {
    this.alto.set(ALTO_INICIAL);
    if (this.estado() === 'listo') {
      this.iniciarCarga();
    }
  }

  // ------------------------------------------------------------------ «Ver el caso» (d528)

  alternarMenuCasos(): void {
    if (this.menuCasos()) {
      this.cerrarMenuCasos(true);
    } else {
      this.menuCasos.set(true);
      afterNextRender(
        () => this.menu()?.nativeElement.querySelector<HTMLElement>('[aria-checked="true"]')?.focus({ preventScroll: true }),
        { injector: this.injector },
      );
    }
  }

  cerrarMenuCasos(devolverFoco: boolean): void {
    if (!this.menuCasos()) {
      return;
    }
    this.menuCasos.set(false);
    if (devolverFoco) {
      this.botonCasos()?.nativeElement.focus({ preventScroll: true });
    }
  }

  elegirCaso(id: CasoVista | null): void {
    this.cerrarMenuCasos(true);
    this.ponerCaso(id);
  }

  volverADatosReales(): void {
    this.ponerCaso(null);
    this.botonCasos()?.nativeElement.focus({ preventScroll: true });
  }

  private ponerCaso(id: CasoVista | null): void {
    const caso = this.casos().find((c) => c.id === id) ?? null;
    // Un caso de la preinscripción lleva a su paso (boceto `ponerCaso`).
    if (caso?.paso !== undefined && caso.paso !== this.paso()) {
      this.paso.set(caso.paso);
      this.enviarPaso();
    }
    this.caso.set(caso?.id ?? null);
    this.mostrarCaso = !!caso;
    this.enviarCaso();
    this.anuncio.set(caso ? vozCaso(caso) : this.textos.vozReales);
  }

  /** Teclado del menú (como el boceto): flechas, Inicio y Fin entre opciones; Escape y Tab lo cierran. */
  alTeclearMenu(evento: KeyboardEvent): void {
    if (evento.key === 'Escape' || evento.key === 'Tab') {
      evento.preventDefault();
      evento.stopPropagation();
      this.cerrarMenuCasos(true);
      return;
    }
    const opciones = Array.from(this.menu()?.nativeElement.querySelectorAll<HTMLElement>('[role="menuitemradio"]') ?? []);
    const i = opciones.indexOf(this.host.nativeElement.ownerDocument.activeElement as HTMLElement);
    if (i === -1) {
      return;
    }
    const destino: Record<string, number> = {
      ArrowDown: (i + 1) % opciones.length,
      ArrowUp: (i - 1 + opciones.length) % opciones.length,
      Home: 0,
      End: opciones.length - 1,
    };
    if (evento.key in destino) {
      evento.preventDefault();
      opciones[destino[evento.key]].focus();
    }
  }

  /** Pulsar fuera del menú (y fuera de su botón, que ya lo alterna) lo cierra sin mover el foco. */
  @HostListener('document:mousedown', ['$event'])
  alPulsarFuera(evento: MouseEvent): void {
    const t = evento.target as Element | null;
    if (this.menuCasos() && !t?.closest?.('.flot--casos, .ver-caso')) {
      this.cerrarMenuCasos(false);
    }
  }

  // ------------------------------------------------------------------ puente

  private alRecibir(mensaje: MensajeDelEditor): void {
    switch (mensaje.tipo) {
      case 'listo':
        this.vistaLista();
        this.enviarBorrador();
        if (this.pagina() === 'preinscripcion') {
          this.enviarPaso();
        }
        // Al recargar (otra sede u otro evento) el caso sigue: la capa nueva lo recibe.
        if (this.caso()) {
          this.enviarCaso();
        }
        break;
      case 'alto':
        this.alto.set(Math.max(1, Math.ceil(mensaje.alto)));
        break;
      case 'ir':
        void this.router.navigateByUrl(RUTA_DESTINO[mensaje.destino]);
        break;
      case 'mostrar':
        if (this.mostrarCaso) {
          this.mostrarCaso = false;
          this.ensenar(mensaje.arriba);
        }
        break;
    }
  }

  private enviarCaso(): void {
    if (this.ventana) {
      enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, {
        canal: CANAL_EDITOR_WEB,
        tipo: 'caso',
        caso: this.caso(),
      });
    }
  }

  private enviarPaso(): void {
    if (this.ventana) {
      enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, {
        canal: CANAL_EDITOR_WEB,
        tipo: 'paso',
        paso: this.paso(),
      });
    }
  }

  /** Lleva a la vista la parte del caso (boceto `ponerCaso`), bajo la barra del panel y la franja del caso. */
  private ensenar(arriba: number): void {
    const ventana = this.ventana;
    const iframe = this.iframe()?.nativeElement;
    if (!ventana || !iframe || typeof ventana.scrollTo !== 'function') {
      return;
    }
    const y = iframe.getBoundingClientRect().top + ventana.scrollY + arriba * this.escala();
    const tapa = this.altoCabeceraPanel() + (this.casoActivoEl()?.nativeElement.offsetHeight ?? 0) + 16;
    if (y < ventana.scrollY + tapa || y > ventana.scrollY + ventana.innerHeight * 0.6) {
      const reducir = ventana.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true;
      ventana.scrollTo({ top: Math.max(0, y - tapa), behavior: reducir ? 'auto' : 'smooth' });
    }
  }

  /** El alto real de la barra superior del panel, medido en el DOM (sin ella, el `top` fijo de la franja del caso). */
  private altoCabeceraPanel(): number {
    const cabecera = this.host.nativeElement.ownerDocument.querySelector<HTMLElement>(CABECERA_PANEL);
    if (cabecera) {
      return cabecera.getBoundingClientRect().height;
    }
    const franja = this.casoActivoEl()?.nativeElement;
    const top = franja ? parseFloat(getComputedStyle(franja).top) : NaN;
    return Number.isFinite(top) ? top : 0;
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

  /**
   * La vista previa está lista cuando la capa de edición lo dice (`listo`), no con el `load` del iframe: si la API no
   * confirma la vista previa de la gestión, la plantilla carga sin capa (sin candados) y, pasado el límite, cae en el
   * error de la vista previa con su «Reintentar».
   */
  private vistaLista(): void {
    if (this.carga() !== 'cargando') {
      return;
    }
    this.pararTemporizadores();
    if (this.esqueleto()) {
      this.anuncio.set('');
    }
    this.esqueleto.set(false);
    this.carga.set('listo');
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
