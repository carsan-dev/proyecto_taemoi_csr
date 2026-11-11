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
import { HttpErrorResponse } from '@angular/common/http';
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
import { AvisosService } from '../../avisos/avisos.service';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { ConCambiosSinGuardar } from '../../productos-alumno/salir-sin-guardar.guard';
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
  pasosContenido,
  primeraOpcion,
  temaDeLaVista,
  textoMarco,
  tituloMenuCasos,
  vistaInicial,
  vozCaso,
  vozPagina,
} from './web-contenido-vista';
import {
  Defectos,
  TEXTOS_GUARDAR,
  barraConflicto,
  clavesCambiadas,
  cuerpoGuardar,
  erroresContenido,
  nombreError,
  plural,
  ponerTexto,
  textoCambios,
  textoConflicto,
  tituloResumen,
  tituloSalir,
} from './web-contenido-guardar';
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
 *
 * W12d: textos en el sitio (la capa manda cada cambio) y guardado (`web-contenido-guardar.ts`): barra «N cambios sin
 * guardar» con Descartar y Guardar, resumen «Faltan N datos para guardar» con enlaces al dato (también los errores 400
 * de la API), avisos de guardado y de error con «Reintentar», conflicto (409, d522 y d530) con «Recargar la página» y
 * diálogo al salir con cambios (`salirSinGuardarGuard`, también desde un candado) y `beforeunload`.
 */
@Component({
  selector: 'pn-web-contenido',
  standalone: true,
  imports: [AcentoEscuelaDirective, BloqueErrorComponent, BotonOcupadoComponent, EsqueletoComponent, RouterLink],
  providers: [WebContenidoService],
  templateUrl: './web-contenido.component.html',
  styleUrl: './web-contenido.component.scss',
})
export class WebContenidoComponent implements OnInit, ConCambiosSinGuardar {
  private readonly servicio = inject(WebContenidoService);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly router = inject(Router);
  private readonly origen = inject(ORIGEN_EDITOR);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly ventana = inject(DOCUMENT).defaultView;
  private readonly ruta = inject(ActivatedRoute);

  readonly textos = TEXTOS_WEB_CONTENIDO;
  readonly textosGuardar = TEXTOS_GUARDAR;
  readonly textoCambios = textoCambios;
  readonly tituloResumen = tituloResumen;
  readonly textoConflicto = textoConflicto;
  readonly barraConflicto = barraConflicto;
  readonly textosVista = TEXTOS_WEB_TEMA;
  readonly vistas = VISTAS;
  readonly paginas = PAGINAS_CONTENIDO;

  readonly estado = signal<'cargando' | 'error' | 'listo'>('cargando');
  /** «Publicada con «tema»» o null si la web aún no está publicada (estado «nueva»). */
  readonly publicada = signal<string | null>(null);
  readonly tema = signal<TemaWeb | null>(null);
  /** El borrador: lo leído de `GET /api/escuela/web/contenido` con los textos cambiados en el sitio (W12d). */
  readonly borrador = signal<BloqueContenido[]>([]);
  /** Lo guardado (el último GET o PUT) y su versión. */
  readonly guardado = signal<BloqueContenido[]>([]);
  readonly version = signal(0);
  /** Lo que pinta el tema en las claves sin fila cuyo texto depende de los datos (lo dice la capa). */
  readonly defectos = signal<Defectos>({});
  /** Tras pulsar Guardar con datos que faltan (boceto `s.errores`). */
  readonly mostrarErrores = signal(false);
  readonly erroresServidor = signal<Record<string, string>>({});
  readonly guardando = signal(false);
  /** 409: otra persona guardó antes (d522). */
  readonly conflicto = signal(false);

  readonly cambiadas = computed(() => clavesCambiadas(this.guardado(), this.borrador(), this.defectos()));
  readonly nCambios = computed(() => this.cambiadas().length);
  readonly erroresVisibles = computed<Record<string, string>>(() =>
    this.mostrarErrores() ? { ...erroresContenido(this.borrador(), this.defectos()), ...this.erroresServidor() } : {},
  );
  /** El resumen «Faltan N datos para guardar»: cada dato con su nombre (o el mensaje de la API) y si lleva al sitio. */
  readonly resumen = computed(() =>
    Object.entries(this.erroresVisibles()).map(([clave, mensaje]) => {
      const nombre = nombreError(clave, this.borrador());
      return { clave, texto: nombre ?? mensaje, enlace: nombre !== null };
    }),
  );
  readonly barraVisible = computed(() => this.estado() === 'listo' && (this.nCambios() > 0 || this.conflicto()));
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

  /** Con una sola sede, el paso «Pueblo» no existe (como en la web real). */
  readonly unaSede = computed(() => this.listas()?.sedes.length === 1);

  readonly opcionesSub = computed<OpcionSub[]>(() => {
    const listas = this.listas();
    switch (this.pagina()) {
      case 'sede':
        return listas ? opcionesSede(listas.sedes) : [];
      case 'evento':
        return listas ? opcionesEvento(listas.eventos) : [];
      case 'preinscripcion':
        return [...pasosContenido(this.unaSede())];
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
  /** Las opciones del selector «Página», con «· N cambios» (boceto `pintarSelector`): los textos de W12d son de Inicio. */
  readonly opcionesPagina = computed(() =>
    PAGINAS_CONTENIDO.map((p) => {
      const n = p.id === 'inicio' ? this.nCambios() : 0;
      return { ...p, texto: n ? `${p.opcion} · ${plural(n, 'cambio', 'cambios')}` : p.opcion };
    }),
  );
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
  /** Tras un enlace del resumen, se enseña el dato cuando la capa dice dónde está. */
  private mostrarDato = false;
  /** El dato del resumen al que ir cuando la capa de la página nueva esté lista. */
  private irPendiente: string | null = null;
  private observador: ResizeObserver | null = null;
  private temporizadorEsqueleto: ReturnType<typeof setTimeout> | null = null;
  private temporizadorLimite: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => this.observar(this.marcoVista()?.nativeElement ?? null));
    // Los errores que se ven en el resumen, también señalados en la página.
    effect(() => this.enviarErrores(this.erroresVisibles()));
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
        this.ponerContenido(contenido.version, contenido.bloques);
        this.defectos.set({});
        this.conflicto.set(false);
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

  private ponerContenido(version: number, bloques: BloqueContenido[]): void {
    this.version.set(version);
    this.guardado.set(bloques);
    this.borrador.set(bloques);
    this.mostrarErrores.set(false);
    this.erroresServidor.set({});
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
      // Con una sola sede no hay paso «Pueblo»: se empieza en «1 · Grupo».
      if (this.unaSede() && this.paso() === 1) {
        this.paso.set(2);
        if (this.pagina() === 'preinscripcion') {
          this.enviarPaso();
        }
      }
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
    const paso = pasosContenido(this.unaSede()).find((p) => p.valor === valor)?.paso;
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
        if (Object.keys(this.erroresVisibles()).length) {
          this.enviarErrores(this.erroresVisibles());
        }
        if (this.pagina() === 'preinscripcion') {
          this.enviarPaso();
        }
        // Al recargar (otra sede u otro evento) el caso sigue: la capa nueva lo recibe.
        if (this.caso()) {
          this.enviarCaso();
        }
        if (this.irPendiente) {
          this.enviarIrTexto(this.irPendiente);
          this.irPendiente = null;
        }
        break;
      case 'texto':
        this.alCambiarTexto(mensaje.clave, mensaje.texto);
        break;
      case 'defectos':
        this.defectos.update((d) => ({ ...mensaje.textos, ...d }));
        break;
      case 'alto':
        this.alto.set(Math.max(1, Math.ceil(mensaje.alto)));
        break;
      case 'ir':
        void this.router.navigateByUrl(RUTA_DESTINO[mensaje.destino]);
        break;
      case 'mostrar':
        if (this.mostrarCaso || this.mostrarDato) {
          this.mostrarCaso = false;
          this.mostrarDato = false;
          this.ensenar(mensaje.arriba);
        }
        break;
    }
  }

  // ------------------------------------------------------------------ textos y guardado (W12d)

  private alCambiarTexto(clave: string, texto: string): void {
    this.borrador.update((filas) => ponerTexto(filas, clave, texto));
    if (clave in this.erroresServidor()) {
      this.erroresServidor.update((errores) => {
        const resto = { ...errores };
        delete resto[clave];
        return resto;
      });
    }
    // Boceto `refrescar`: el resumen se va cuando ya no falta nada.
    if (this.mostrarErrores() && !Object.keys(this.erroresVisibles()).length) {
      this.mostrarErrores.set(false);
    }
  }

  guardar(): void {
    if (this.guardando() || this.conflicto()) {
      return;
    }
    if (Object.keys(erroresContenido(this.borrador(), this.defectos())).length) {
      this.erroresServidor.set({});
      this.mostrarErrores.set(true);
      this.enfocarDespues('.resumen');
      return;
    }
    this.guardando.set(true);
    this.servicio.guardar(cuerpoGuardar(this.version(), this.borrador(), this.defectos())).subscribe({
      next: (contenido) => {
        this.guardando.set(false);
        this.ponerContenido(contenido.version, contenido.bloques);
        this.enviarBorrador();
        this.enfocarTitulo();
        this.avisos.exito(this.publicada() ? this.textosGuardar.guardadoPublicada : this.textosGuardar.guardadoNueva);
      },
      error: (error: unknown) => {
        this.guardando.set(false);
        const http = error instanceof HttpErrorResponse ? error : null;
        const errores = http?.status === 400 ? (http.error as { errores?: Record<string, string> } | null)?.errores : undefined;
        if (http?.status === 409) {
          this.conflicto.set(true);
          this.enfocarDespues('.conflicto');
        } else if (errores && typeof errores === 'object' && Object.keys(errores).length) {
          this.erroresServidor.set(errores);
          this.mostrarErrores.set(true);
          this.enfocarDespues('.resumen');
        } else {
          this.avisos.error(this.textosGuardar.errorGuardar, { texto: this.textosGuardar.reintentar, alPulsar: () => this.guardar() });
        }
      },
    });
  }

  descartar(): void {
    if (this.guardando()) {
      return;
    }
    this.borrador.set(this.guardado());
    this.mostrarErrores.set(false);
    this.erroresServidor.set({});
    this.enviarBorrador();
    this.enfocarTitulo();
    this.anuncio.set(this.textosGuardar.descartados);
  }

  /** Conflicto (409): se vuelve a leer todo; lo que no se guardó se pierde (lo decía el aviso del conflicto). */
  recargar(): void {
    this.cargar();
    this.avisos.info(this.textosGuardar.recargada);
    this.enfocarTitulo();
  }

  /** Un enlace del resumen: al dato, en su página (los de W12d son de Inicio, con la cabecera y el pie). */
  irAlDato(evento: Event, clave: string): void {
    evento.preventDefault();
    this.mostrarDato = true;
    if (this.pagina() !== 'inicio') {
      this.irPendiente = clave;
      this.ponerPagina('inicio');
      return;
    }
    if (this.caso()) {
      this.ponerCaso(null);
    }
    this.enviarIrTexto(clave);
  }

  private enviarIrTexto(clave: string): void {
    if (this.ventana) {
      enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'ir-texto', clave });
    }
  }

  private enviarErrores(errores: Record<string, string>): void {
    if (this.ventana) {
      enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'errores', errores });
    }
  }

  /** `canDeactivate` (`salirSinGuardarGuard`): con cambios, «Tienes N cambios sin guardar» (boceto `salir()`). */
  async puedeSalir(): Promise<boolean> {
    const n = this.nCambios();
    if (n === 0) {
      return true;
    }
    const r = await this.confirmacion.confirmar({
      titulo: tituloSalir(n),
      mensaje: this.textosGuardar.salirTexto,
      accion: this.textosGuardar.salir,
      cancelar: this.textosGuardar.seguir,
      destructivo: true,
      nombre: 'salir-sin-guardar',
    });
    return r.confirmado;
  }

  @HostListener('window:beforeunload', ['$event'])
  antesDeDescargar(evento: BeforeUnloadEvent): void {
    if (this.nCambios() > 0) {
      evento.preventDefault();
      evento.returnValue = '';
    }
  }

  private enfocarDespues(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), { injector: this.injector });
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
