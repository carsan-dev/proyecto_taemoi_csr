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
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { TemaWeb } from '../../../web-plantilla/datos/modelos';
import type { PaginaEditor } from '../../../web-plantilla/editor/candados-linea-comarcal';
import {
  CANAL_EDITOR_WEB,
  AccionFoto,
  CasoVista,
  EstadoSubidaFoto,
  MensajeDelEditor,
  PasoPreinscripcion,
  enviarPorPuente,
  esMensajeDelEditor,
  escucharPuente,
} from '../../../web-plantilla/editor/puente-editor';
import { claveDescripcion, fotoOpcional } from '../../../web-plantilla/editor/fotos-sueltas';
import {
  type AccionBloque,
  TEXTOS_BLOQUES,
  type TipoBloque,
  esMovible,
  estaOculto,
  etiquetaAccion,
  nombreBloque,
  ordenCompleto,
} from '../../../web-plantilla/editor/bloques-editor';
import { defTexto, defectoDeLaPagina } from '../../../web-plantilla/editor/textos-editor';
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
  entradaResumen,
  erroresContenido,
  plural,
  ponerTexto,
  textoCambios,
  textoConflicto,
  tituloResumen,
  tituloSalir,
  valorTexto,
} from './web-contenido-guardar';
import {
  TEXTOS_FOTOS,
  comprobarArchivo,
  conFotosCambiadasDesde,
  describirFoto,
  estadoFotos,
  falloDeSubida,
  fotosCambiadas,
  ponerFoto,
  quitarFoto,
  volverALaAnterior,
} from './web-contenido-fotos';
import {
  alternarOculto,
  anadirBloque,
  cambiosBloques,
  confirmacionQuitar,
  esDeBloqueFuera,
  esQuitable,
  filasDelBloque,
  lineaHoja,
  moverBloque,
  ordenVisible,
  partesSinGuardar,
  quitarBloque,
  vozAnadido,
  vozMover,
  vozOculto,
  vozQuitado,
} from './web-contenido-bloques';
import { ListasVista, WebContenidoService } from './web-contenido.service';

/** Panel estrecho (boceto `estrecho()`): las acciones de un bloque salen en la hoja inferior. */
const CONSULTA_ESTRECHO = '(max-width: 767.98px)';
/** Lo que tiene una capa recién puesta: sin pastillas, sin quitables y ancha. */
const ESTADO_BLOQUES_INICIAL = JSON.stringify([[], [], false]);

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
 *
 * W12f-1: fotos sueltas (portada, presentación y «Texto con foto»; `web-contenido-fotos.ts`). La capa elige el fichero y
 * lo manda; aquí se comprueba, se sube a la API con su progreso («Cancelar subida» la corta) y la foto subida entra en el
 * borrador sin descripción, con el diálogo de describir abierto en la capa. Poner, cambiar, quitar, volver a la anterior
 * y describir cuentan como cambios. Mientras sube algo no se guarda.
 *
 * W12e: bloques de Inicio (`web-contenido-bloques.ts`). La capa pide subir, bajar, ocultar, mostrar, quitar (con
 * confirmación) y añadir; aquí se cambian las cabeceras del borrador, se manda a la vista previa, se dice por voz y se
 * pide el foco. Con el panel estrecho (< 768 px), las acciones van en la hoja inferior del panel.
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
  readonly textosFotos = TEXTOS_FOTOS;
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
  /** W12f-1: subidas en curso o fallidas, por foto (boceto `s.subidas`). */
  readonly subidas = signal<Record<string, EstadoSubidaFoto>>({});
  readonly subiendo = computed(() => Object.values(this.subidas()).some((s) => s.estado === 'subiendo'));
  readonly fotosCambiadas = computed(() => fotosCambiadas(this.guardado(), this.borrador()));
  /** W12e: orden, ocultos, añadidos y quitados (boceto `cambios`). */
  readonly cambiosBloques = computed(() => cambiosBloques(this.guardado(), this.borrador()));
  /** Los textos y fotos de un bloque añadido o quitado no cuentan aparte: cuenta el bloque (boceto `cambios`). */
  private readonly clavesQueCuentan = computed(() => {
    const fuera = this.cambiosBloques().fuera;
    return [...this.cambiadas(), ...this.fotosCambiadas()].filter((clave) => !esDeBloqueFuera(clave, fuera));
  });
  readonly nCambios = computed(() => this.clavesQueCuentan().length + this.cambiosBloques().n);
  /** Partes con la pastilla «Sin guardar». */
  readonly sinGuardar = computed(() => partesSinGuardar(this.cambiosBloques(), this.clavesQueCuentan()));
  readonly quitables = computed(() => ordenCompleto(this.borrador()).filter((id) => esQuitable(id, this.guardado())));
  /** Los bloques que la capa dice que hay en Inicio (las fijas sin datos no se pintan) y los vacíos. */
  readonly presentes = signal<ReadonlySet<string> | null>(null);
  readonly vacios = signal<ReadonlySet<string>>(new Set());
  readonly estrecho = signal(this.ventana?.matchMedia?.(CONSULTA_ESTRECHO).matches ?? false);
  /** El bloque de la hoja inferior abierta (panel estrecho), o null. */
  readonly hoja = signal<string | null>(null);
  readonly textosBloques = TEXTOS_BLOQUES;
  readonly etiquetaAccion = etiquetaAccion;
  /** Boceto `pintarHoja`. */
  readonly vistaHoja = computed(() => {
    const id = this.hoja();
    const filas = this.borrador();
    const orden = ordenVisible(filas, this.presentes());
    const puesto = id ? orden.indexOf(id) : -1;
    if (!id || puesto < 0) {
      return null;
    }
    const oculto = estaOculto(filas, id);
    return {
      id,
      nombre: nombreBloque(id, filas),
      linea: lineaHoja(puesto, orden.length, oculto, this.vacios().has(id)),
      primero: puesto === 0,
      ultimo: puesto === orden.length - 1,
      oculto,
      quitable: this.quitables().includes(id),
    };
  });
  readonly erroresVisibles = computed<Record<string, string>>(() =>
    this.mostrarErrores() ? { ...erroresContenido(this.borrador(), this.defectos()), ...this.erroresServidor() } : {},
  );
  /** El resumen «Faltan N datos para guardar»: cada dato con su nombre (o el mensaje de la API) y si lleva al sitio. */
  readonly resumen = computed(() =>
    Object.entries(this.erroresVisibles()).map(([clave, mensaje]) =>
      entradaResumen(clave, mensaje, this.borrador(), clave in this.erroresServidor()),
    ),
  );
  readonly barraVisible = computed(
    () => this.estado() === 'listo' && (this.nCambios() > 0 || this.subiendo() || this.conflicto()),
  );
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
  /** W12e: tras una acción de bloque, se enseña el bloque cuando la capa dice dónde está. */
  private mostrarBloque = false;
  /** Lo último que se mandó a la capa de `estado-bloques` (la capa empieza con el inicial). */
  private estadoBloquesEnviado = ESTADO_BLOQUES_INICIAL;
  /** El dato del resumen al que ir cuando la capa de la página nueva esté lista. */
  private irPendiente: string | null = null;
  /** El aviso de error con «Reintentar» que sigue abierto (se cierra al guardar bien o al descartar). */
  private avisoError: number | null = null;
  /** W12f-1: la petición de cada subida en curso (se corta al cancelar, descartar, recargar o salir). */
  private readonly peticiones = new Map<string, Subscription>();
  private observador: ResizeObserver | null = null;
  private temporizadorEsqueleto: ReturnType<typeof setTimeout> | null = null;
  private temporizadorLimite: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => this.observar(this.marcoVista()?.nativeElement ?? null));
    // Los errores que se ven en el resumen, también señalados en la página.
    effect(() => this.enviarErrores(this.erroresVisibles()));
    // W12f-1: «Nueva, sin guardar», «Volver a la anterior» y las subidas, en la capa.
    effect(() => this.enviarFotos());
    // W12e: «Sin guardar», lo que se puede quitar y si el panel es estrecho, en la capa.
    effect(() => this.enviarEstadoBloques());
    // La hoja no sigue abierta si el bloque ya no está o el panel deja de ser estrecho.
    effect(() => {
      if (this.hoja() !== null && (!this.estrecho() || this.vistaHoja() === null)) {
        this.hoja.set(null);
      }
    });
    const consulta = this.ventana?.matchMedia?.(CONSULTA_ESTRECHO);
    const alCambiarAncho = (e: MediaQueryListEvent) => this.estrecho.set(e.matches);
    consulta?.addEventListener?.('change', alCambiarAncho);
    const dejarDeEscuchar = this.ventana
      ? escucharPuente(this.ventana, () => this.iframe()?.nativeElement.contentWindow ?? null, esMensajeDelEditor, (m) =>
          this.alRecibir(m),
        )
      : () => undefined;
    inject(DestroyRef).onDestroy(() => {
      dejarDeEscuchar();
      consulta?.removeEventListener?.('change', alCambiarAncho);
      this.cancelarSubidas();
      this.pararTemporizadores();
      this.observador?.disconnect();
    });
  }

  ngOnInit(): void {
    this.cargar();
    afterNextRender(() => this.enfocarTitulo(), { injector: this.injector });
  }

  cargar(anunciar = false): void {
    this.cancelarSubidas();
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
    this.presentes.set(null);
    this.hoja.set(null);
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
        // La capa nueva empieza sin fotos cambiadas ni subidas: solo si hay algo.
        if (this.fotosCambiadas().length || Object.keys(this.subidas()).length) {
          this.enviarFotos();
        }
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
        // La capa nueva empieza sin pastillas, sin quitables y ancha: solo se manda si hay algo.
        this.estadoBloquesEnviado = ESTADO_BLOQUES_INICIAL;
        this.enviarEstadoBloques();
        break;
      case 'bloques':
        this.presentes.set(new Set(mensaje.orden));
        this.vacios.set(new Set(mensaje.vacios));
        break;
      case 'bloque':
        void this.accionBloque(mensaje.accion, mensaje.bloque, false);
        break;
      case 'anadir-bloque':
        this.anadir(mensaje.pos, mensaje.bloque);
        break;
      case 'hoja':
        this.abrirHoja(mensaje.bloque);
        break;
      case 'texto':
        this.alCambiarTexto(mensaje.clave, mensaje.texto);
        break;
      case 'defectos': {
        // Solo los que dependen de los datos y nunca el «Escribe aquí…» de un vacío (como la capa).
        const validos = Object.fromEntries(Object.entries(mensaje.textos).filter(([clave, texto]) => defectoDeLaPagina(clave, texto)));
        this.defectos.update((d) => ({ ...validos, ...d }));
        break;
      }
      case 'alto':
        this.alto.set(Math.max(1, Math.ceil(mensaje.alto)));
        break;
      case 'ir':
        void this.router.navigateByUrl(RUTA_DESTINO[mensaje.destino]);
        break;
      case 'mostrar':
        if (this.mostrarCaso || this.mostrarDato || this.mostrarBloque) {
          this.mostrarCaso = false;
          this.mostrarDato = false;
          this.mostrarBloque = false;
          this.ensenar(mensaje.arriba);
        }
        break;
      case 'subir-foto':
        this.subirFoto(mensaje.clave, mensaje.archivo);
        break;
      case 'foto':
        this.accionFoto(mensaje.clave, mensaje.accion);
        break;
      case 'descripcion':
        this.borrador.update((filas) => describirFoto(filas, mensaje.clave, mensaje.texto));
        this.limpiarErrores([mensaje.clave, claveDescripcion(mensaje.clave)]);
        break;
    }
  }

  // ------------------------------------------------------------------ textos y guardado (W12d)

  private alCambiarTexto(clave: string, texto: string): void {
    // Defensa: el panel no comprueba el máximo al guardar (el boceto no tiene ese error); la capa ya no deja pasarse.
    const max = defTexto(clave)?.max;
    const recortado = max !== undefined && texto.length > max ? texto.slice(0, max) : texto;
    this.borrador.update((filas) => ponerTexto(filas, clave, recortado));
    this.limpiarErrores([clave]);
  }

  /** Lo cambiado ya no lleva el error de la API; el resumen se va cuando ya no falta nada (boceto `refrescar`). */
  private limpiarErrores(claves: readonly string[]): void {
    if (claves.some((clave) => clave in this.erroresServidor())) {
      this.erroresServidor.update((errores) => {
        const resto = { ...errores };
        claves.forEach((clave) => delete resto[clave]);
        return resto;
      });
    }
    if (this.mostrarErrores() && !Object.keys(this.erroresVisibles()).length) {
      this.mostrarErrores.set(false);
    }
  }

  // ------------------------------------------------------------------ fotos sueltas (W12f-1)

  /** Boceto `simularSubida`: comprobar, subir con progreso y, al terminar, al borrador y a describirla. */
  private subirFoto(clave: string, archivo: File): void {
    this.cancelarPeticion(clave);
    const fallo = comprobarArchivo(archivo);
    if (fallo) {
      this.ponerSubida(clave, fallo);
      return;
    }
    this.ponerSubida(clave, { estado: 'subiendo', pct: 0, nombre: archivo.name });
    this.anuncio.set(this.textosFotos.vozSubiendo);
    const peticion = this.servicio.subirImagen(archivo).subscribe({
      next: (evento) => {
        if (evento.type === HttpEventType.UploadProgress) {
          const pct = evento.total ? Math.min(100, Math.round((100 * evento.loaded) / evento.total)) : 0;
          const actual = this.subidas()[clave];
          if (actual?.estado === 'subiendo' && actual.pct !== pct) {
            this.ponerSubida(clave, { ...actual, pct });
          }
        } else if (evento.type === HttpEventType.Response) {
          this.peticiones.delete(clave);
          const imagen = evento.body;
          if (!imagen) {
            this.ponerSubida(clave, falloDeSubida(archivo, null));
            return;
          }
          this.quitarSubida(clave);
          this.cambiarFotos([clave], (filas) => ponerFoto(filas, clave, imagen.imagenId, imagen.url));
          this.anuncio.set(this.textosFotos.vozSubida);
          if (this.ventana) {
            enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, {
              canal: CANAL_EDITOR_WEB,
              tipo: 'describir',
              clave,
            });
          }
        }
      },
      error: (error: unknown) => {
        this.peticiones.delete(clave);
        this.ponerSubida(clave, falloDeSubida(archivo, error));
      },
    });
    if (!peticion.closed) {
      this.peticiones.set(clave, peticion);
    }
  }

  private accionFoto(clave: string, accion: AccionFoto): void {
    switch (accion) {
      case 'cancelar-subida': {
        const estaba = this.subidas()[clave];
        this.cancelarPeticion(clave);
        this.quitarSubida(clave);
        if (estaba?.estado === 'subiendo') {
          this.anuncio.set(this.textosFotos.vozCancelada);
        }
        break;
      }
      case 'anterior':
        this.cambiarFotos([clave], (filas) => volverALaAnterior(filas, this.guardado(), clave));
        this.anuncio.set(this.textosFotos.vozAnterior);
        break;
      case 'quitar':
        // La foto de portada es obligatoria: no se quita (boceto `abrirMenuFoto`).
        if (fotoOpcional(clave)) {
          this.cambiarFotos([clave], (filas) => quitarFoto(filas, clave));
          this.anuncio.set(this.textosFotos.vozQuitada);
        }
        break;
    }
  }

  /** Un cambio de foto: al borrador y a la vista previa (el tema la pinta antes de guardar). */
  private cambiarFotos(claves: readonly string[], cambio: (filas: BloqueContenido[]) => BloqueContenido[]): void {
    this.borrador.update(cambio);
    this.limpiarErrores(claves.flatMap((clave) => [clave, claveDescripcion(clave)]));
    this.enviarBorrador();
  }

  private ponerSubida(clave: string, estado: EstadoSubidaFoto): void {
    this.subidas.update((s) => ({ ...s, [clave]: estado }));
  }

  private quitarSubida(clave: string): void {
    if (clave in this.subidas()) {
      this.subidas.update((s) => {
        const resto = { ...s };
        delete resto[clave];
        return resto;
      });
    }
  }

  private cancelarPeticion(clave: string): void {
    this.peticiones.get(clave)?.unsubscribe();
    this.peticiones.delete(clave);
  }

  private cancelarSubidas(): void {
    this.peticiones.forEach((p) => p.unsubscribe());
    this.peticiones.clear();
    this.subidas.set({});
  }

  private enviarFotos(): void {
    const { cambiadas, anteriores } = estadoFotos(this.guardado(), this.borrador());
    const subidas = this.subidas();
    if (this.ventana) {
      enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, {
        canal: CANAL_EDITOR_WEB,
        tipo: 'fotos',
        cambiadas,
        anteriores,
        subidas,
      });
    }
  }

  guardar(): void {
    // Boceto `pintarBarra`: mientras sube una foto, Guardar no se puede pulsar.
    if (this.guardando() || this.conflicto() || this.subiendo()) {
      return;
    }
    if (Object.keys(erroresContenido(this.borrador(), this.defectos())).length) {
      this.erroresServidor.set({});
      this.mostrarErrores.set(true);
      this.enfocarDespues('.resumen');
      return;
    }
    this.guardando.set(true);
    const enviado = this.borrador();
    this.servicio.guardar(cuerpoGuardar(this.version(), enviado, this.defectos())).subscribe({
      next: (contenido) => {
        this.guardando.set(false);
        this.cerrarAvisoError();
        // Lo tecleado durante «Guardando…» se conserva (boceto: `s.guardado = copia(s.datos)`): sobre lo guardado se
        // vuelven a poner los textos que cambiaron desde que salió el PUT.
        const actual = this.borrador();
        const tecleado = clavesCambiadas(enviado, actual, this.defectos()).map(
          (clave) => [clave, valorTexto(actual, clave, this.defectos()) ?? ''] as const,
        );
        this.ponerContenido(contenido.version, contenido.bloques);
        if (tecleado.length) {
          this.borrador.update((filas) => tecleado.reduce((f, [clave, texto]) => ponerTexto(f, clave, texto), filas));
        }
        // W12f-1: igual con las fotos subidas, quitadas o descritas mientras se guardaba.
        this.borrador.update((filas) => conFotosCambiadasDesde(filas, enviado, actual));
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
          this.cerrarAvisoError();
          this.avisoError = this.avisos.error(this.textosGuardar.errorGuardar, {
            texto: this.textosGuardar.reintentar,
            alPulsar: () => {
              this.avisoError = null;
              this.guardar();
            },
          });
        }
      },
    });
  }

  descartar(): void {
    if (this.guardando()) {
      return;
    }
    this.cancelarSubidas();
    this.hoja.set(null);
    this.borrador.set(this.guardado());
    this.cerrarAvisoError();
    this.mostrarErrores.set(false);
    this.erroresServidor.set({});
    this.enviarBorrador();
    this.enfocarTitulo();
    this.anuncio.set(this.textosGuardar.descartados);
  }

  private cerrarAvisoError(): void {
    if (this.avisoError !== null) {
      this.avisos.cerrar(this.avisoError);
      this.avisoError = null;
    }
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

  // ------------------------------------------------------------------ bloques de Inicio (W12e)

  /** Las acciones solo valen en Inicio, con la pantalla lista y sin un guardado en curso. */
  private puedeTocarBloques(): boolean {
    return this.pagina() === 'inicio' && this.estado() === 'listo' && !this.guardando();
  }

  /** Boceto `mover`, `alternarOculto` y `quitarBloque` (desde la barra de la capa o desde la hoja). */
  async accionBloque(accion: AccionBloque, id: string, desdeHoja: boolean): Promise<void> {
    if (!this.puedeTocarBloques() || !esMovible(id)) {
      return;
    }
    switch (accion) {
      case 'subir':
      case 'bajar': {
        const paso = accion === 'subir' ? -1 : 1;
        const r = moverBloque(this.borrador(), id, paso, this.presentes());
        if (!r) {
          return;
        }
        this.borrador.set(r.filas);
        this.enviarBorrador();
        // Boceto: el foco sigue en el mismo botón, salvo si el bloque queda en el extremo (entonces, en el otro).
        const enExtremo = (paso < 0 && r.puesto === 0) || (paso > 0 && r.puesto === r.total - 1);
        const queda = paso < 0 !== enExtremo ? 'subir' : 'bajar';
        this.enfocarBloque(desdeHoja ? `h-${queda}` : `${queda}:${id}`, desdeHoja);
        this.anuncio.set(vozMover(nombreBloque(id, r.filas), r.puesto, r.total));
        break;
      }
      case 'ocultar':
      case 'mostrar': {
        if ((accion === 'ocultar') === estaOculto(this.borrador(), id)) {
          return;
        }
        this.borrador.update((filas) => alternarOculto(filas, id));
        this.enviarBorrador();
        this.enfocarBloque(desdeHoja ? 'h-vis' : `vis:${id}`, desdeHoja);
        this.anuncio.set(vozOculto(nombreBloque(id, this.borrador()), accion === 'ocultar'));
        break;
      }
      case 'quitar':
        await this.quitar(id, desdeHoja);
        break;
    }
  }

  /** Boceto `quitarBloque`: con confirmación; se van la cabecera y todas sus filas (y sus subidas). */
  private async quitar(id: string, desdeHoja: boolean): Promise<void> {
    if (!this.quitables().includes(id)) {
      return;
    }
    const filas = this.borrador();
    const nombre = nombreBloque(id, filas);
    const r = await this.confirmacion.confirmar({ ...confirmacionQuitar(id, filas), destructivo: true, nombre: 'quitar-bloque' });
    if (!r.confirmado) {
      // El foco vuelve al botón que la abrió (el de la hoja ya lo devuelve el diálogo).
      if (!desdeHoja) {
        this.enviarFocoBloque(`quitar:${id}`);
      }
      return;
    }
    if (!this.puedeTocarBloques() || !this.quitables().includes(id)) {
      return;
    }
    const puesto = ordenVisible(this.borrador(), this.presentes()).indexOf(id);
    const claves = filasDelBloque(this.borrador(), id);
    for (const clave of claves) {
      if (clave in this.subidas()) {
        this.cancelarPeticion(clave);
        this.quitarSubida(clave);
      }
    }
    this.borrador.update((f) => quitarBloque(f, id));
    this.limpiarErrores(claves.flatMap((clave) => [clave, claveDescripcion(clave)]));
    this.hoja.set(null);
    this.enviarBorrador();
    this.enfocarBloque(`a:${Math.max(0, puesto)}`, false);
    this.anuncio.set(vozQuitado(nombre));
  }

  /** Boceto `anadirBloque`: en el puesto del hueco; el foco, a su primer texto vacío. */
  private anadir(pos: number, tipo: TipoBloque): void {
    if (!this.puedeTocarBloques()) {
      return;
    }
    const r = anadirBloque(this.borrador(), this.guardado(), pos, tipo, this.presentes());
    if (!r) {
      return;
    }
    this.borrador.set(r.filas);
    this.enviarBorrador();
    this.enfocarBloque(`editar:${r.id}`, false);
    this.anuncio.set(vozAnadido(tipo, pos));
  }

  /** Boceto `abrirHoja` (panel estrecho): el foco, al primer botón que se puede pulsar. */
  abrirHoja(id: string): void {
    if (!this.estrecho() || !esMovible(id) || this.pagina() !== 'inicio') {
      return;
    }
    this.hoja.set(id);
    if (this.vistaHoja() === null) {
      this.hoja.set(null);
      return;
    }
    this.enfocarDespues('.hoja button:not([aria-disabled="true"])');
  }

  /** Boceto `cerrarHoja`: el foco vuelve al nombre del bloque, en la vista previa. */
  cerrarHoja(devolverFoco: boolean): void {
    const id = this.hoja();
    if (id === null) {
      return;
    }
    this.hoja.set(null);
    if (devolverFoco) {
      this.enviarFocoBloque(`sel:${id}`);
    }
  }

  /** Un botón de la hoja: la misma acción que en la barra; la hoja sigue abierta (salvo al quitar). */
  pulsarHoja(evento: Event, accion: AccionBloque): void {
    const id = this.hoja();
    if (!id || (evento.currentTarget as HTMLElement | null)?.getAttribute('aria-disabled') === 'true') {
      return;
    }
    void this.accionBloque(accion, id, true);
  }

  /** Escape cierra la hoja (si no hay un diálogo abierto encima). */
  @HostListener('document:keydown.escape')
  alEscaparHoja(): void {
    if (this.hoja() !== null && !this.host.nativeElement.ownerDocument.querySelector('dialog[open]')) {
      this.cerrarHoja(true);
    }
  }

  private enfocarBloque(foco: string, enLaHoja: boolean): void {
    if (enLaHoja) {
      this.enfocarDespues(`.hoja [data-h="${foco}"]`);
      return;
    }
    this.mostrarBloque = true;
    this.enviarFocoBloque(foco);
  }

  private enviarFocoBloque(foco: string): void {
    const iframe = this.iframe()?.nativeElement;
    if (!this.ventana || !iframe) {
      return;
    }
    // El foco pasa a la vista previa (el panel lo tenía en un botón suyo o en el diálogo).
    iframe.focus({ preventScroll: true });
    enviarPorPuente(this.ventana, iframe.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'foco-bloque', foco });
  }

  /** Solo si cambia respecto a lo que ya tiene la capa. */
  private enviarEstadoBloques(): void {
    const sinGuardar = this.sinGuardar();
    const quitables = this.quitables();
    const estrecho = this.estrecho();
    const firma = JSON.stringify([sinGuardar, quitables, estrecho]);
    if (!this.ventana || firma === this.estadoBloquesEnviado) {
      return;
    }
    const enviado = enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, {
      canal: CANAL_EDITOR_WEB,
      tipo: 'estado-bloques',
      sinGuardar,
      quitables,
      estrecho,
    });
    if (enviado) {
      this.estadoBloquesEnviado = firma;
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
