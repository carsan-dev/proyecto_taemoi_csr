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
  type MensajeHojaDescribir,
  type MensajeHojaMenu,
  PasoPreinscripcion,
  enviarPorPuente,
  esMensajeDelEditor,
  escucharPuente,
} from '../../../web-plantilla/editor/puente-editor';
import { MAX_DESCRIPCION_FOTO, claveDescripcion, fotoOpcional } from '../../../web-plantilla/editor/fotos-sueltas';
import { GALERIA_NUEVA, MAX_GALERIA, esFotoGaleria, ordenGaleria, vozMoverFoto } from '../../../web-plantilla/editor/fotos-galeria';
import {
  TEXTOS_PROFESORES,
  clavePermiso,
  esFotoProfesor,
  numeroPermiso,
  numeroProfesor,
  tituloQuitarProfesor,
  tituloSedesProfesor,
  vozQuitadoProfesor,
  vozSedesProfesor,
} from '../../../web-plantilla/editor/fotos-profesores';
import {
  type AccionBloque,
  TEXTOS_BLOQUES,
  type TipoBloque,
  esFilaDelBloque,
  esMovible,
  estaOculto,
  etiquetaAccion,
  nombreBloque,
  ordenCompleto,
} from '../../../web-plantilla/editor/bloques-editor';
import { casoDeTexto, defTexto, defectoDeLaPagina, esTextoEditable, paginaDeTexto, pasoDeTexto } from '../../../web-plantilla/editor/textos-editor';
import { textosQuitarDato } from '../../../web-plantilla/editor/huecos-editor';
import { AcentoEscuelaDirective } from '../../acento-escuela.directive';
import { AvisosService } from '../../avisos/avisos.service';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { AsaHojaDirective } from '../../hoja/asa-hoja.directive';
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
  VISOR_VISUAL,
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
  cambiosPorPagina,
  clavesCambiadas,
  cuerpoGuardar,
  entradaResumen,
  erroresContenido,
  plural,
  ponerTexto,
  textoCambios,
  textoCambiosCorto,
  textoConflicto,
  tituloDescartar,
  tituloResumen,
  tituloSalir,
  valorTexto,
} from './web-contenido-guardar';
import {
  TEXTOS_FOTOS,
  comprobarArchivo,
  contadorDescripcion,
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
import { anadirFotoGaleria, moverFotoGaleria, quitarFotoGaleria } from './web-contenido-galeria';
import {
  anadirProfesor,
  cambiosProfesores,
  conCabeceras,
  elegirSedes,
  erroresProfesores,
  esDeProfesorFuera,
  estadoProfesores,
  filasDeProfesor,
  marcasVigentes,
  numerosProfesores,
  permisosParaEnviar,
  quitarProfesor,
  sedesDelProfesor,
  sedesElegibles,
  stripJava,
  vecinoProfesor,
} from './web-contenido-profesores';
import { ListasVista, WebContenidoService } from './web-contenido.service';

/** Lo que tiene la capa al estar lista: ni profesores ni sedes (firma de `enviarProfesores`). */
const PROFESORES_INICIAL = JSON.stringify({ lista: [], sedes: [] });

/** Panel estrecho (boceto `estrecho()`): las acciones de un bloque salen en la hoja inferior. */
const CONSULTA_ESTRECHO = '(max-width: 767.98px)';
/**
 * d551: lo que el visor (`visualViewport`) tiene que quedar tapado por abajo para que sea el teclado del móvil y no la
 * barra de direcciones del navegador que se esconde (unos 56 px en Chrome de Android).
 */
const UMBRAL_TECLADO = 120;
/** d551: con el teclado abierto, lo que se deja entre el campo que se enseña y el borde de la barra (una línea de texto). */
const MARGEN_CAMPO = 56;
/**
 * d551: por encima de esta escala del visor hay zoom de pellizco: el visor mide `innerHeight / scale` sin teclado, así
 * que se ignora del todo (como antes de la pieza).
 */
const ESCALA_ZOOM = 1.01;
/** d551: lo que el alto del teclado tiene que quedarse quieto (animación de apertura) antes de pedir el texto en edición. */
const ESPERA_TECLADO_MS = 150;
/** Lo que tiene una capa recién puesta: sin pastillas, sin quitables y ancha. */
const ESTADO_BLOQUES_INICIAL = JSON.stringify([[], [], false]);

/** La barra superior del panel (`navegacion/cabecera-panel`): 64 px y 56 en móvil; la franja del caso va debajo. */
const CABECERA_PANEL = '.pn-cab';
/** Lo que el panel pone fijo sobre la vista previa: arriba, su cabecera (la franja del caso se busca en la pantalla)... */
const TAPAS_ARRIBA = [CABECERA_PANEL];
/** ...y abajo, la barra de navegación del móvil (la barra de cambios se busca en la pantalla). */
const TAPAS_ABAJO = ['.pn-barra'];

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
 * W12f-2a: la galería (`web-contenido-galeria.ts`). Una foto más llega como `galeria.nueva`; al terminar la subida entra
 * la última con su número (`numeroGaleriaNuevo`) y se describe. Mover y quitar cambian los puestos; todo cuenta como
 * cambio y la descripción es obligatoria.
 *
 * W12f-2b: profesores (`web-contenido-profesores.ts`). La capa pide añadir (número nuevo, nunca uno ya usado: d531),
 * quitar (con confirmación; se van todas sus filas, también sus sedes, y su subida) y marcar la casilla del permiso; aquí
 * se guarda la casilla de cada número (solo en esta pantalla) y el `PUT` manda `permiso: true` solo en el nombre de los
 * que la tienen marcada y la necesitan. La constancia del GET va a la capa en texto y nunca se reenvía.
 *
 * W12f-3: sedes de cada profesor. La capa pide el conjunto (`profesor`, acción `sedes`); aquí se filtra y ordena con las
 * sedes elegibles (las de `listas`) y se escribe `profesor.<n>.sedes` (`elegirSedes`). La capa recibe con `profesores`
 * las elegibles y las marcadas de cada uno.
 *
 * W12e: bloques de Inicio (`web-contenido-bloques.ts`). La capa pide subir, bajar, ocultar, mostrar, quitar (con
 * confirmación) y añadir; aquí se cambian las cabeceras del borrador, se manda a la vista previa, se dice por voz y se
 * pide el foco. Con el panel estrecho (< 768 px), las acciones van en la hoja inferior del panel.
 */
@Component({
  selector: 'pn-web-contenido',
  standalone: true,
  imports: [AcentoEscuelaDirective, AsaHojaDirective, BloqueErrorComponent, BotonOcupadoComponent, EsqueletoComponent, RouterLink],
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
  /** d551: la parte de la ventana que se ve (encoge con el teclado del móvil); null si el navegador no la da. */
  private readonly visor = inject(VISOR_VISUAL);
  private readonly ruta = inject(ActivatedRoute);

  readonly textos = TEXTOS_WEB_CONTENIDO;
  readonly textosGuardar = TEXTOS_GUARDAR;
  readonly textosFotos = TEXTOS_FOTOS;
  readonly textoCambios = textoCambios;
  readonly textoCambiosCorto = textoCambiosCorto;
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
  /**
   * W12f-2b: casillas del permiso marcadas en esta pantalla (no se guardan en ningún sitio): por número de profesor, el
   * nombre (`stripJava`) con el que se marcó. Decisión del propietario (12/11): si el nombre cambia, la marca se va.
   */
  readonly permisosMarcados = signal<ReadonlyMap<number, string>>(new Map());
  /** Las marcas que cuentan: las de los profesores cuyo nombre sigue siendo el de cuando se marcaron. */
  readonly permisosVigentes = computed(() => marcasVigentes(this.borrador(), this.permisosMarcados()));
  /** W12f-2b: los profesores cuyo permiso pidió la API en el último intento (400 `profesor.<n>.permiso`). */
  readonly permisosPedidos = signal<ReadonlySet<number>>(new Set());
  /** W12f-2b: profesores añadidos y quitados, fotos, sedes y casillas (boceto `cambios`). */
  readonly cambiosProfesores = computed(() =>
    cambiosProfesores(this.guardado(), this.borrador(), this.permisosVigentes(), this.permisosPedidos()),
  );
  /** Los textos y fotos de un bloque (o un profesor) añadido o quitado no cuentan aparte: cuenta él (boceto `cambios`). */
  private readonly clavesQueCuentan = computed(() => {
    const fuera = this.cambiosBloques().fuera;
    const profesores = this.cambiosProfesores();
    return [...this.cambiadas(), ...this.fotosCambiadas(), ...profesores.claves].filter(
      (clave) =>
        !esDeBloqueFuera(clave, fuera) &&
        (/^profesor\.\d+\.(anadido|quitado)$/.test(clave) || !esDeProfesorFuera(clave, profesores.fuera)),
    );
  });
  readonly nCambios = computed(() => this.clavesQueCuentan().length + this.cambiosBloques().n);
  /** Partes con la pastilla «Sin guardar». */
  readonly sinGuardar = computed(() => partesSinGuardar(this.cambiosBloques(), this.clavesQueCuentan()));
  readonly quitables = computed(() => ordenCompleto(this.borrador()).filter((id) => esQuitable(id)));
  /** Los bloques que la capa dice que hay en Inicio (las fijas sin datos no se pintan) y los vacíos. */
  readonly presentes = signal<ReadonlySet<string> | null>(null);
  readonly vacios = signal<ReadonlySet<string>>(new Set());
  readonly estrecho = signal(this.ventana?.matchMedia?.(CONSULTA_ESTRECHO).matches ?? false);
  /**
   * d551: lo que el visor deja tapado por abajo (`innerHeight − (offsetTop + height)`), en px de la ventana; 0 sin visor
   * o con zoom. Da el `bottom` de la barra con el teclado abierto.
   */
  readonly tapadoAbajo = signal(0);
  /**
   * d551: el alto del teclado del móvil (`innerHeight − visor.height`, sin offsetTop); 0 sin visor o con zoom. No depende
   * de dónde esté el visor: Chrome lo desplaza para enseñar el campo enfocado y lo tapado puede quedar en 0.
   */
  readonly altoTeclado = signal(0);
  /**
   * El borde de abajo de lo que se ve (`visor.offsetTop + visor.height`), en px desde arriba del visor de maquetación; 0
   * sin visor o con zoom. La hoja de describir se ancla por arriba a él con el teclado abierto: no depende de
   * `innerHeight`, que con la barra de URL de Chrome visible puede no medir lo mismo que el bloque de los `fixed`.
   */
  readonly bordeVisible = signal(0);
  /** El alto de lo que se ve (`visor.height`); 0 sin visor o con zoom. Tope de la hoja de describir con el teclado. */
  readonly altoVisible = signal(0);
  /** px que `innerHeight` pasa de `100svh` con la hoja de describir abierta tras subir (la barra de URL sin contar); 0 si no. */
  readonly barraSinContar = signal(0);
  /** d551: teclado del móvil abierto: panel estrecho y el alto del teclado por encima del umbral. */
  readonly teclado = computed(() => this.estrecho() && this.altoTeclado() > UMBRAL_TECLADO);
  /** El bloque de la hoja inferior abierta (panel estrecho), o null. */
  readonly hoja = signal<string | null>(null);
  /** d541: el menú de la vista previa (foto, «Añadir bloque») abierto como hoja inferior, o null. */
  readonly hojaMenu = signal<MensajeHojaMenu | null>(null);
  /** «Describir la foto» de la vista previa como hoja inferior (panel estrecho), como lo pidió la capa, o null. */
  readonly hojaDescribir = signal<MensajeHojaDescribir | null>(null);
  /** Lo que lleva escrito la hoja de describir (para su contador). */
  readonly textoDescribir = signal('');
  readonly maxDescripcion = MAX_DESCRIPCION_FOTO;
  /** La hoja de describir, si su foto sigue en el borrador (con imagen); null si no. */
  readonly vistaHojaDescribir = computed(() => {
    const h = this.hojaDescribir();
    return h && this.borrador().some((f) => f.clave === h.clave && f.imagenId !== null) ? h : null;
  });
  readonly contadorDescribir = computed(() => contadorDescripcion(this.textoDescribir().length));
  readonly topeDescribir = computed(() => this.textoDescribir().length >= MAX_DESCRIPCION_FOTO);
  /** W12f-3: la hoja de sedes de un profesor (panel estrecho) que pidió la capa, con su número de apertura, o null. */
  readonly hojaSedes = signal<{ n: number; id: number } | null>(null);
  readonly textosProfesores = TEXTOS_PROFESORES;
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
  /** Lo que falta para guardar (boceto `errores()`), con los profesores (W12f-2b: permiso obligatorio). */
  readonly errores = computed<Record<string, string>>(() => ({
    ...erroresContenido(this.borrador(), this.defectos()),
    ...erroresProfesores(this.guardado(), this.borrador(), this.permisosVigentes(), this.permisosPedidos()),
  }));
  readonly erroresVisibles = computed<Record<string, string>>(() => {
    if (!this.mostrarErrores()) {
      return {};
    }
    // Lo que dice la API va en su orden; lo de aquí que repite una clave suya (el permiso que pidió), con su mensaje.
    const servidor = this.erroresServidor();
    const propios = Object.entries(this.errores()).filter(([clave]) => !(clave in servidor));
    return { ...Object.fromEntries(propios), ...servidor };
  });
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

  /** W12f-3: las sedes que se pueden elegir para un profesor (vacía mientras se leen o si fallan: no se cambia ninguna). */
  readonly sedesElegibles = computed(() => sedesElegibles(this.listas()?.sedes ?? []));
  /**
   * W12f-3: la hoja de sedes (boceto `abrirSedesProfe` en estrecho): su título y una casilla por sede elegible, marcada
   * si está en el borrador. Null si no hay hoja, el profesor ya no está o no hay sedes que elegir.
   */
  readonly vistaHojaSedes = computed(() => {
    const h = this.hojaSedes();
    const filas = this.borrador();
    const sedes = this.sedesElegibles();
    if (!h || !sedes.length || !numerosProfesores(filas).includes(h.n)) {
      return null;
    }
    const marcadas = sedesDelProfesor(filas, h.n, sedes.map((s) => s.id));
    return {
      n: h.n,
      titulo: tituloSedesProfesor(filas.find((f) => f.clave === `profesor.${h.n}.nombre`)?.texto),
      sedes: sedes.map((s) => ({ ...s, marcada: marcadas.includes(s.id) })),
    };
  });

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
  /**
   * Las opciones del selector «Página», con «· N cambios» (boceto `pintarSelector`): cada texto en su página (W12d-bis);
   * las fotos y los bloques, en Inicio.
   */
  readonly opcionesPagina = computed(() => {
    const porPagina = cambiosPorPagina(this.clavesQueCuentan(), this.cambiosBloques().n);
    return PAGINAS_CONTENIDO.map((p) => {
      const n = porPagina[p.id] ?? 0;
      return { ...p, texto: n ? `${p.opcion} · ${plural(n, 'cambio', 'cambios')}` : p.opcion };
    });
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
  /** W12f-2a: números de foto de la galería ya dados en esta pantalla (una foto nueva no repite ninguno). */
  private readonly numerosGaleria = new Set<number>();
  /**
   * W12f-2b (d531): números de profesor vistos o dados en esta pantalla (guardados en algún momento y añadidos). Un
   * profesor nuevo nunca toma uno: ni el de uno quitado y guardado, ni el de uno quitado y vuelto a añadir.
   */
  private readonly numerosProfesor = new Set<number>();
  /**
   * W12f-3: números de profesor cuya fila `profesor.<n>.sedes` rechazó la API (400). Mientras lo guardado sea el mismo,
   * esa fila guardada es la que falla: elegir sus sedes la reescribe siempre con solo elegibles (`forzar`), también
   * después de limpiarse el error. Se vacía con lo guardado nuevo (cargar, recargar, guardar bien); descartar no lo vacía,
   * porque vuelve justo a lo guardado que falló.
   */
  private readonly sedesRechazadas = new Set<number>();
  /** Lo último que se mandó a la capa de `profesores`. */
  private profesoresEnviado = '';
  /** Lo último que se mandó a la capa de `zona-visible` («top:alto»; vacío con la capa nueva). */
  private zonaEnviada = '';
  /** El fotograma en que se medirá la zona (como mucho un envío por fotograma). */
  private zonaPedida: number | null = null;
  /** El fotograma en que se volverá a medir el visor tras un resize/scroll (como mucho una vez por fotograma). */
  private remedidaPedida: number | null = null;
  /** d551: el teclado se abrió o cambió de alto y ya se asentó: la próxima zona pide a la capa el texto en edición. */
  private campoPedido = false;
  /** d551: la espera a que el teclado deje de cambiar de alto antes de pedir el campo. */
  private temporizadorTeclado: ReturnType<typeof setTimeout> | null = null;
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
    // W12f-2b: cada profesor con su casilla o su constancia, en la capa.
    effect(() => this.enviarProfesores());
    // La hoja no sigue abierta si el bloque ya no está o el panel deja de ser estrecho.
    effect(() => {
      if (this.hoja() !== null && (!this.estrecho() || this.vistaHoja() === null)) {
        this.hoja.set(null);
      }
    });
    // Ni la hoja de un menú de la vista previa: si el panel se ensancha, la capa vuelve a su menú flotante.
    effect(() => {
      if (this.hojaMenu() !== null && !this.estrecho()) {
        this.cerrarHojaMenu(null, false);
      }
    });
    // Ni la de describir: tampoco si su foto ya no está en el borrador (quitada, descartada, recargada).
    effect(() => {
      if (this.hojaDescribir() !== null && (!this.estrecho() || this.vistaHojaDescribir() === null)) {
        this.cerrarHojaDescribir(false);
      }
    });
    // W12f-3: ni la de sedes, si el panel se ensancha, el profesor se va o ya no hay sedes que elegir.
    effect(() => {
      if (this.hojaSedes() !== null && (!this.estrecho() || this.vistaHojaSedes() === null)) {
        this.cerrarHojaSedes(false);
      }
    });
    // La parte de la vista previa que se ve, para que la capa ponga sus flotantes dentro: cambia con el alto del
    // documento, la escala, la barra de cambios, la franja del caso, la hoja, el desplazamiento y el tamaño de la ventana.
    effect(() => {
      this.alto();
      this.escala();
      this.barraVisible();
      this.casoActivo();
      this.vistaHoja();
      this.estrecho();
      this.tapadoAbajo();
      this.altoTeclado();
      this.pedirZona();
    });
    const alMoverVentana = () => this.pedirZona();
    this.ventana?.addEventListener('scroll', alMoverVentana, { passive: true });
    // Al cambiar de alto la ventana (p. ej. sale o se va la barra de URL de Chrome) cambia `innerHeight` sin que el visor
    // avise siempre: se vuelve a medir también, y otra vez en el siguiente fotograma, cuando el navegador ya se asentó.
    const alCambiarVentana = () => {
      this.medirVisor();
      this.remedirVisor();
    };
    this.ventana?.addEventListener('resize', alCambiarVentana);
    // d551: el visor encoge con el teclado del móvil (sin `interactive-widget`, la ventana no cambia de alto).
    const alMoverVisor = () => {
      this.medirVisor();
      this.remedirVisor();
    };
    this.visor?.addEventListener('resize', alMoverVisor);
    this.visor?.addEventListener('scroll', alMoverVisor, { passive: true });
    this.medirVisor();
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
      this.ventana?.removeEventListener('scroll', alMoverVentana);
      this.ventana?.removeEventListener('resize', alCambiarVentana);
      this.visor?.removeEventListener('resize', alMoverVisor);
      this.visor?.removeEventListener('scroll', alMoverVisor);
      if (this.zonaPedida !== null) {
        this.ventana?.cancelAnimationFrame?.(this.zonaPedida);
        this.zonaPedida = null;
      }
      if (this.remedidaPedida !== null) {
        this.ventana?.cancelAnimationFrame?.(this.remedidaPedida);
        this.remedidaPedida = null;
      }
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
        this.permisosMarcados.set(new Map());
        this.permisosPedidos.set(new Set());
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
    numerosProfesores(bloques).forEach((n) => this.numerosProfesor.add(n));
    this.version.set(version);
    this.guardado.set(bloques);
    this.borrador.set(bloques);
    this.mostrarErrores.set(false);
    this.erroresServidor.set({});
    // Lo guardado es otro: lo que rechazó la API ya no vale para esto.
    this.sedesRechazadas.clear();
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
    this.hojaMenu.set(null);
    this.hojaDescribir.set(null);
    this.hojaSedes.set(null);
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
    if (this.hojaMenu() !== null && !t?.closest?.('.flot--capa')) {
      this.cerrarHojaMenu(null, false);
    }
    if (this.hojaSedes() !== null && !t?.closest?.('.flot--capa')) {
      this.cerrarHojaSedes(false);
    }
    if (this.hojaDescribir() !== null && !t?.closest?.('.flot--capa')) {
      this.cerrarHojaDescribir(false);
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
        // La capa nueva empieza sin profesores ni sedes: solo se manda si hay algo.
        this.profesoresEnviado = PROFESORES_INICIAL;
        this.enviarProfesores();
        // Ni sabe qué parte se ve: se le dice ya.
        this.zonaEnviada = '';
        this.enviarZona();
        break;
      case 'profesor':
        if (mensaje.accion === 'anadir') {
          this.anadirProfesor();
        } else if (mensaje.accion === 'quitar') {
          void this.quitarProfesor(mensaje.n);
        } else if (mensaje.accion === 'sedes') {
          this.elegirSedes(mensaje.n, mensaje.sedes);
        } else if (mensaje.accion === 'anunciar-sedes') {
          this.anunciarSedes(mensaje.n);
        } else {
          this.marcarPermiso(mensaje.n, mensaje.marcada);
        }
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
      case 'hoja-menu':
        this.abrirHojaMenu(mensaje);
        break;
      case 'hoja-sedes':
        this.abrirHojaSedes(mensaje.n, mensaje.id);
        break;
      case 'cerrar-hoja-sedes':
        // La capa cerró sola su diálogo: solo se quita la hoja si es esa; sin responder ni mover el foco.
        if (this.hojaSedes()?.id === mensaje.id) {
          this.hojaSedes.set(null);
        }
        break;
      case 'hoja-describir':
        this.abrirHojaDescribir(mensaje);
        break;
      case 'cerrar-hoja-describir':
        // La capa cerró sola la descripción (la foto ya no está, otro menú): solo si es esa; sin responder ni foco.
        if (this.hojaDescribir()?.id === mensaje.id) {
          this.hojaDescribir.set(null);
        }
        break;
      case 'cerrar-hoja-menu': {
        // La capa cerró sola su menú: solo se quita la hoja si es esa; sin responder ni mover el foco.
        const abierta = this.hojaMenu();
        if (abierta && abierta.menu === mensaje.menu && abierta.id === mensaje.id) {
          this.hojaMenu.set(null);
        }
        break;
      }
      case 'texto':
        this.alCambiarTexto(mensaje.clave, mensaje.texto);
        break;
      case 'pedir-quitar-dato':
        void this.confirmarQuitarDato(mensaje.clave, mensaje.huecos, mensaje.antes, mensaje.despues);
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
        if (mensaje.campo) {
          // d551: la capa responde a la zona que lo pidió: el texto en edición quedaba tapado por el teclado o la barra.
          if (this.teclado()) {
            this.ensenar(mensaje.arriba, true);
          }
          break;
        }
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
    const n = numeroProfesor(clave);
    // W12f-2b: una fila nueva de profesor (su cargo) no mueve la sección: antes, cada bloque con su cabecera.
    const nueva = n !== null && !this.borrador().some((f) => f.clave === clave);
    this.borrador.update((filas) => ponerTexto(nueva ? conCabeceras(filas) : filas, clave, recortado));
    // Decisión del propietario (12/11): el permiso es del nombre con que se marcó. Si cambia (`stripJava`, como la API),
    // también al volver al guardado, la casilla se desmarca sola y se dice una vez (con ella ya fuera, no se repite).
    const marcadoCon = n !== null && clave.endsWith('.nombre') ? this.permisosMarcados().get(n) : undefined;
    if (n !== null && marcadoCon !== undefined && stripJava(this.borrador().find((f) => f.clave === clave)?.texto) !== marcadoCon) {
      this.ponerMarca(n, false);
      this.anuncio.set(TEXTOS_PROFESORES.vozDesmarcado);
    }
    this.limpiarErrores([clave]);
  }

  // ------------------------------------------------------------------ profesores (W12f-2b)

  /** Boceto `anadirProfe`: la tarjeta al final, con su número nuevo; el foco, a escribir su nombre. */
  private anadirProfesor(): void {
    if (!this.puedeTocarBloques()) {
      return;
    }
    const r = anadirProfesor(this.borrador(), this.guardado(), this.numerosProfesor);
    if (!r) {
      // Sin número libre (los 99 ya usados en esta pantalla): no se añade.
      return;
    }
    this.numerosProfesor.add(r.n);
    this.ponerMarca(r.n, false);
    this.borrador.set(r.filas);
    this.enviarBorrador();
    this.enviarFocoProfesor(`editar:${r.n}`);
    this.anuncio.set(TEXTOS_PROFESORES.vozAnadido);
  }

  /** Boceto `quitarProfe`: con confirmación; se van todas sus filas (nombre, cargo, foto y sedes) y su subida. */
  private async quitarProfesor(n: number): Promise<void> {
    if (!this.puedeTocarBloques() || !numerosProfesores(this.borrador()).includes(n)) {
      return;
    }
    const nombre = this.borrador().find((f) => f.clave === `profesor.${n}.nombre`)?.texto ?? '';
    const r = await this.confirmacion.confirmar({
      titulo: tituloQuitarProfesor(nombre),
      mensaje: TEXTOS_PROFESORES.confirmarTexto,
      accion: TEXTOS_PROFESORES.confirmarAccion,
      cancelar: 'Cancelar',
      destructivo: true,
      nombre: 'quitar-profesor',
    });
    if (!r.confirmado || !this.puedeTocarBloques() || !numerosProfesores(this.borrador()).includes(n)) {
      this.enviarFocoProfesor(`quitar:${n}`);
      return;
    }
    const vecino = vecinoProfesor(this.borrador(), n);
    const claves = filasDeProfesor(this.borrador(), n);
    const foto = `profesor.${n}.foto`;
    if (foto in this.subidas()) {
      this.cancelarPeticion(foto);
      this.quitarSubida(foto);
    }
    this.ponerMarca(n, false);
    if (this.permisosPedidos().has(n)) {
      this.permisosPedidos.update((s) => new Set([...s].filter((x) => x !== n)));
    }
    this.borrador.update((filas) => quitarProfesor(filas, n));
    this.sedesRechazadas.delete(n);
    this.limpiarErrores([...claves, claveDescripcion(foto), clavePermiso(n), `profesor.${n}.nombre`]);
    this.enviarBorrador();
    this.enviarFocoProfesor(vecino === null ? 'anadir' : `quitar:${vecino}`);
    this.anuncio.set(vozQuitadoProfesor(nombre));
  }

  /** La casilla «Tengo su permiso…» de un profesor del borrador. */
  private marcarPermiso(n: number, marcada: boolean): void {
    if (!this.borrador().some((f) => f.clave === `profesor.${n}.nombre`)) {
      return;
    }
    this.ponerMarca(n, marcada);
    this.limpiarErrores([clavePermiso(n)]);
    this.anuncio.set(marcada ? TEXTOS_PROFESORES.vozMarcado : TEXTOS_PROFESORES.vozDesmarcado);
  }

  /**
   * W12f-3: las sedes que pide la capa para un profesor. Solo en Inicio y sin un guardado en curso (lo cambiado durante
   * «Guardando…» se perdería al llegar lo guardado). La capa recibe siempre el estado que vale, también si no cambia nada.
   */
  private elegirSedes(n: number, pedidas: readonly number[]): void {
    const clave = `profesor.${n}.sedes`;
    const filas = this.puedeTocarBloques()
      ? elegirSedes(this.guardado(), this.borrador(), n, pedidas, this.sedesElegibles().map((s) => s.id), this.sedesRechazadas.has(n))
      : null;
    if (filas) {
      this.borrador.set(filas);
      this.limpiarErrores([clave]);
      // El tema pinta las sedes de cada tarjeta.
      this.enviarBorrador();
    }
    this.profesoresEnviado = '';
    this.enviarProfesores();
  }

  /** W12f-3 (boceto `listo-sedes`): «Sedes: Casariche, Estepa.» o «Sin sedes.», con las del borrador en orden de lista. */
  private anunciarSedes(n: number): void {
    const filas = this.borrador();
    if (!numerosProfesores(filas).includes(n)) {
      return;
    }
    const sedes = this.sedesElegibles();
    const marcadas = sedesDelProfesor(filas, n, sedes.map((s) => s.id));
    this.anuncio.set(vozSedesProfesor(sedes.filter((s) => marcadas.includes(s.id)).map((s) => s.nombre)));
  }

  /** Marcada, con el nombre que tiene ahora (`stripJava`): la marca vale para ese nombre y solo para él. */
  private ponerMarca(n: number, marcada: boolean): void {
    const actual = this.permisosMarcados();
    const nombre = stripJava(this.borrador().find((f) => f.clave === `profesor.${n}.nombre`)?.texto);
    if (marcada ? actual.get(n) === nombre : !actual.has(n)) {
      return;
    }
    const nuevo = new Map(actual);
    if (marcada) {
      nuevo.set(n, nombre);
    } else {
      nuevo.delete(n);
    }
    this.permisosMarcados.set(nuevo);
  }

  /**
   * Solo si cambia respecto a lo que ya tiene la capa. d521: la constancia (quién y cuándo) solo sale hacia el iframe
   * con la capa de edición confirmada (`listo`, que pone la vista previa en `listo`); al llegarlo, se manda entera.
   */
  private enviarProfesores(): void {
    const sedes = this.sedesElegibles();
    const ids = sedes.map((s) => s.id);
    const lista = estadoProfesores(this.guardado(), this.borrador(), this.permisosVigentes(), this.permisosPedidos(), ids);
    const firma = JSON.stringify({ lista, sedes });
    if (!this.ventana || this.carga() !== 'listo' || firma === this.profesoresEnviado) {
      return;
    }
    if (enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'profesores', lista, sedes })) {
      this.profesoresEnviado = firma;
    }
  }

  private enviarFocoProfesor(foco: string): void {
    const iframe = this.iframe()?.nativeElement;
    if (!this.ventana || !iframe) {
      return;
    }
    // El foco pasa a la vista previa (el panel lo tenía en el diálogo).
    iframe.focus({ preventScroll: true });
    this.mostrarBloque = true;
    enviarPorPuente(this.ventana, iframe.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'foco-profesor', foco });
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
    // W12f-2a: con 6 fotos la capa no ofrece «Añadir foto» (boceto `galeriaHtml`); defensa por si llega.
    if (clave === GALERIA_NUEVA && ordenGaleria(this.borrador()).length >= MAX_GALERIA) {
      this.quitarSubida(clave);
      return;
    }
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
          const puesta = this.ponerFotoSubida(clave, imagen.imagenId, imagen.url);
          if (!puesta) {
            return;
          }
          this.anuncio.set(this.textosFotos.vozSubida);
          if (this.ventana) {
            enviarPorPuente(this.ventana, this.iframe()?.nativeElement.contentWindow ?? null, {
              canal: CANAL_EDITOR_WEB,
              tipo: 'describir',
              clave: puesta,
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

  /**
   * La foto subida, al borrador: en su sitio o, si es `galeria.nueva`, la última de la galería con su número (W12f-2a).
   * Devuelve la clave de la foto puesta, o null si ya no cabe (la galería se llenó, o la foto se quitó mientras subía).
   */
  private ponerFotoSubida(clave: string, imagenId: number, url: string): string | null {
    if (clave === GALERIA_NUEVA) {
      const r = anadirFotoGaleria(this.borrador(), this.guardado(), this.numerosGaleria, imagenId, url);
      if (!r) {
        return null;
      }
      this.numerosGaleria.add(Number(r.clave.slice('galeria.'.length)));
      this.cambiarFotos([r.clave], () => r.filas);
      return r.clave;
    }
    if (esFotoGaleria(clave) && !this.borrador().some((f) => f.clave === clave)) {
      return null;
    }
    if (esFotoProfesor(clave)) {
      // W12f-2b: el profesor se quitó mientras subía: su foto no entra (sería una fila sin profesor).
      if (!this.borrador().some((f) => f.clave === clave.replace(/\.foto$/, '.nombre'))) {
        return null;
      }
      this.cambiarFotos([clave], (filas) => ponerFoto(conCabeceras(filas), clave, imagenId, url));
      return clave;
    }
    this.cambiarFotos([clave], (filas) => ponerFoto(filas, clave, imagenId, url));
    return clave;
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
          this.cambiarFotos([clave], (filas) => (esFotoGaleria(clave) ? quitarFotoGaleria(filas, clave) : quitarFoto(filas, clave)));
          this.anuncio.set(this.textosFotos.vozQuitada);
        }
        break;
      case 'antes':
      case 'despues': {
        // W12f-2a: cambia el puesto con la vecina; la capa deja el foco en la foto movida.
        const r = esFotoGaleria(clave) ? moverFotoGaleria(this.borrador(), clave, accion === 'antes' ? -1 : 1) : null;
        if (r) {
          this.cambiarFotos([], () => r.filas);
          this.anuncio.set(vozMoverFoto(r.puesto, r.total));
        }
        break;
      }
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
    if (Object.keys(this.errores()).length) {
      this.erroresServidor.set({});
      this.mostrarErrores.set(true);
      this.enfocarDespues('.resumen');
      return;
    }
    this.guardando.set(true);
    const enviado = this.borrador();
    // W12f-2b: `permiso: true` solo en los que lo necesitan y tienen la casilla marcada en esta pantalla.
    // Decisión del propietario (12/11): solo las marcas cuyo nombre es el que se envía (el de cuando se marcaron).
    const permisos = permisosParaEnviar(this.guardado(), enviado, marcasVigentes(enviado, this.permisosMarcados()), this.permisosPedidos());
    this.servicio.guardar(cuerpoGuardar(this.version(), enviado, this.defectos(), permisos)).subscribe({
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
        // W12f-2b: lo guardado ya trae la constancia; solo siguen las casillas de los añadidos durante «Guardando…».
        this.permisosMarcados.update((s) => new Map([...s].filter(([n]) => !enviado.some((f) => numeroProfesor(f.clave) === n))));
        this.permisosPedidos.set(new Set());
        if (tecleado.length) {
          this.borrador.update((filas) => tecleado.reduce((f, [clave, texto]) => ponerTexto(f, clave, texto), filas));
        }
        // W12f-1: igual con las fotos subidas, quitadas o descritas mientras se guardaba.
        this.borrador.update((filas) => conFotosCambiadasDesde(filas, enviado, actual));
        this.enviarBorrador();
        // Sin desplazar: la página se queda donde estaba (en el móvil, el foco al título la subía arriba del todo).
        this.enfocarTitulo(false);
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
          // W12f-2b: el permiso que pide la API (PERMISO_FALTA o PERMISO_NOMBRE_CAMBIADO) va a la casilla de ese profesor.
          const pedidos = Object.keys(errores).map(numeroPermiso).filter((n): n is number => n !== null);
          this.permisosPedidos.set(new Set(pedidos));
          // W12f-3: lo guardado no cambia con un 400; las filas de sedes rechazadas se suman a las de antes.
          Object.keys(errores).forEach((clave) => {
            const n = /^profesor\.([0-9]+)\.sedes$/.exec(clave)?.[1];
            if (n !== undefined) {
              this.sedesRechazadas.add(Number(n));
            }
          });
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

  /**
   * d551: con confirmación siempre (móvil y escritorio): «¿Descartar los N cambios?». Cancelar no cambia nada y el foco
   * vuelve al botón (`ConfirmacionService`). Mientras guarda, nada (tampoco si empezó a guardar con el diálogo abierto).
   */
  async descartar(): Promise<void> {
    if (this.guardando()) {
      return;
    }
    // Lo que pasa con el diálogo abierto: guardar, quedarse sin cambios o un conflicto (409) dejan sin nada que descartar.
    const r = await this.confirmacion.confirmar({
      titulo: tituloDescartar(this.nCambios()),
      mensaje: this.textosGuardar.descartarTexto,
      accion: this.textosGuardar.descartar,
      cancelar: this.textosGuardar.seguir,
      destructivo: true,
      nombre: 'descartar-cambios',
    });
    if (!r.confirmado || this.guardando() || this.nCambios() === 0 || this.conflicto()) {
      return;
    }
    this.cancelarSubidas();
    this.hoja.set(null);
    this.borrador.set(this.guardado());
    // W12f-2b: las casillas vuelven a como estaban; los números ya dados siguen dados (d531).
    this.permisosMarcados.set(new Map());
    this.permisosPedidos.set(new Set());
    this.cerrarAvisoError();
    this.mostrarErrores.set(false);
    this.erroresServidor.set({});
    this.enviarBorrador();
    // Sin desplazar, como al guardar: se ve cómo vuelve lo que se estaba mirando.
    this.enfocarTitulo(false);
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

  /**
   * Un enlace del resumen: al dato, en su página, su caso y su paso (boceto: `[data-ir]`; W12d-bis). Las fotos, en
   * Inicio. Con otra página, el iframe se recarga y el dato va cuando la capa nueva está lista (con el caso y el paso).
   */
  irAlDato(evento: Event, clave: string): void {
    evento.preventDefault();
    this.mostrarDato = true;
    const texto = esTextoEditable(clave);
    const pagina = texto ? paginaDeTexto(clave) : 'inicio';
    const caso = texto ? this.casoDe(pagina, clave) : null;
    const paso = texto ? this.pasoPara(clave, caso) : null;
    if (this.pagina() !== pagina) {
      this.irPendiente = clave;
      this.ponerPagina(pagina);
      // La capa nueva recibe el caso y el paso al estar lista (`listo`), antes que el dato.
      this.caso.set(caso?.id ?? null);
      if (paso !== null) {
        this.paso.set(paso);
      }
      return;
    }
    if ((caso?.id ?? null) !== this.caso()) {
      this.ponerCaso(caso?.id ?? null);
    }
    if (paso !== null && paso !== this.paso()) {
      this.paso.set(paso);
      this.enviarPaso();
    }
    this.enviarIrTexto(clave);
  }

  /** El caso de la página en el que sale el texto (boceto `CASO_DE`), o null (los datos reales). */
  private casoDe(pagina: PaginaEditor, clave: string): CasoPagina | null {
    const id = casoDeTexto(clave);
    return CASOS_PAGINA[pagina].find((c) => c.id === id) ?? null;
  }

  /** El paso de la preinscripción donde sale el texto; null si no cambia (no es de la preinscripción o ya se ve). */
  private pasoPara(clave: string, caso: CasoPagina | null): PasoPreinscripcion | null {
    if (caso?.paso !== undefined) {
      return caso.paso;
    }
    const paso = pasoDeTexto(clave);
    const primero: PasoPreinscripcion = this.unaSede() ? 2 : 1;
    if (paso === null) {
      return null;
    }
    if (paso === 'flujo') {
      return this.paso() === 4 ? primero : null;
    }
    // Con una sede no hay paso «Pueblo».
    return paso === 1 && this.unaSede() ? null : paso;
  }

  /**
   * W12d-bis: borrar la ficha de un dato dentro de un texto pide confirmación (decisión del propietario, 12/11). El
   * diálogo del panel (hoja inferior en el móvil); la respuesta vuelve a la capa, que quita el dato o lo deja todo como
   * estaba. El foco vuelve a la vista previa y la capa lo pone en el texto.
   */
  private async confirmarQuitarDato(clave: string, huecos: string[], antes: string, despues: string): Promise<void> {
    const textos = textosQuitarDato(clave, huecos, antes, despues);
    const r = textos ? await this.confirmacion.confirmar({ ...textos, destructivo: true, nombre: 'quitar-dato' }) : { confirmado: false };
    const iframe = this.iframe()?.nativeElement;
    if (!this.ventana || !iframe) {
      return;
    }
    iframe.focus({ preventScroll: true });
    enviarPorPuente(this.ventana, iframe.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'quitar-dato', quitar: r.confirmado });
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
    // También las subidas de fotos que aún no tienen fila (una foto nueva en curso): al acabar crearían una fila huérfana.
    const subiendo = Object.keys(this.subidas()).filter((clave) => esFilaDelBloque(clave, id));
    for (const clave of new Set([...claves, ...subiendo])) {
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
    // Una sola hoja a la vez: la del menú de la vista previa se cierra y la capa lo sabe.
    this.cerrarHojaMenu(null, false);
    this.cerrarHojaDescribir(false);
    this.cerrarHojaSedes(false);
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
    if (this.host.nativeElement.ownerDocument.querySelector('dialog[open]')) {
      return;
    }
    if (this.hojaDescribir() !== null) {
      this.cerrarHojaDescribir(true);
    } else if (this.hojaSedes() !== null) {
      this.cerrarHojaSedes(true);
    } else if (this.hojaMenu() !== null) {
      this.cerrarHojaMenu(null, true);
    } else if (this.hoja() !== null) {
      this.cerrarHoja(true);
    }
  }

  // ------------------------------------------------------------------ menús de la vista previa como hoja (d541)

  /**
   * La capa (panel estrecho) pide su menú de foto o de «Añadir bloque» como hoja inferior: la vista previa ocupa toda su
   * altura y un menú dentro de ella no queda pegado al borde de la pantalla. Aquí se elige y se devuelve la opción.
   */
  private abrirHojaMenu(menu: MensajeHojaMenu): void {
    if (!this.estrecho()) {
      // La capa creía el panel estrecho y ya no lo es: que vuelva a su menú flotante.
      this.responderHojaMenu(menu.menu, menu.id, null, false);
      return;
    }
    const abierta = this.hojaMenu();
    if (abierta) {
      this.responderHojaMenu(abierta.menu, abierta.id, null, false);
    }
    this.hoja.set(null);
    this.cerrarHojaDescribir(false);
    this.cerrarHojaSedes(false);
    this.cerrarMenuCasos(false);
    this.hojaMenu.set(menu);
    this.enfocarDespues('.flot--capa [role="menuitem"]:not([aria-disabled="true"])');
  }

  /** Una opción de la hoja: la capa hace lo mismo que con su menú flotante. Las desactivadas no hacen nada. */
  elegirHojaMenu(op: string, desactivada: boolean): void {
    if (!desactivada) {
      this.cerrarHojaMenu(op, false);
    }
  }

  /** Cierra la hoja y se lo dice a la capa; `op` null: sin elegir. El foco va a la vista previa si se elige o se pide. */
  cerrarHojaMenu(op: string | null, devolverFoco: boolean): void {
    const abierta = this.hojaMenu();
    if (abierta === null) {
      return;
    }
    this.hojaMenu.set(null);
    this.responderHojaMenu(abierta.menu, abierta.id, op, devolverFoco);
  }

  private responderHojaMenu(menu: MensajeHojaMenu['menu'], id: number, op: string | null, devolverFoco: boolean): void {
    const iframe = this.iframe()?.nativeElement;
    if (!this.ventana || !iframe) {
      return;
    }
    if (op !== null || devolverFoco) {
      // El foco pasa a la vista previa (el panel lo tenía en la hoja que se cierra).
      iframe.focus({ preventScroll: true });
    }
    enviarPorPuente(this.ventana, iframe.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-menu', menu, id, op, devolverFoco });
  }

  /** Teclado de la hoja (como el boceto): flechas, Inicio y Fin entre opciones; Escape y Tab la cierran devolviendo el foco. */
  alTeclearHojaMenu(evento: KeyboardEvent): void {
    if (evento.key === 'Escape' || evento.key === 'Tab') {
      evento.preventDefault();
      evento.stopPropagation();
      this.cerrarHojaMenu(null, true);
      return;
    }
    const opciones = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.flot--capa [role="menuitem"]'));
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

  /** Pulsar la vista previa (el foco pasa al iframe) cierra la hoja sin mover el foco, como pulsar fuera del menú. */
  @HostListener('window:blur')
  alPerderElFoco(): void {
    const doc = this.host.nativeElement.ownerDocument;
    if (this.hojaMenu() !== null && doc.activeElement === this.iframe()?.nativeElement) {
      this.cerrarHojaMenu(null, false);
    }
    if (this.hojaSedes() !== null && doc.activeElement === this.iframe()?.nativeElement) {
      this.cerrarHojaSedes(false);
    }
    if (this.hojaDescribir() !== null && doc.activeElement === this.iframe()?.nativeElement) {
      this.cerrarHojaDescribir(false);
    }
  }

  // ------------------------------------------------------------------ «Describir la foto» como hoja

  /**
   * La capa (panel estrecho) pide «Describir la foto» como hoja inferior (boceto `abrirDescribir`; en estrecho, todo
   * flotante es hoja). Cada pulsación va a la capa (`texto-hoja-describir`), que la aplica como su diálogo y devuelve
   * `descripcion`: el borrador cambia por un solo camino. Si no se puede abrir (panel ancho, foto que no está), se
   * contesta ya.
   */
  private abrirHojaDescribir(m: MensajeHojaDescribir): void {
    const abierta = this.hojaDescribir();
    if (abierta) {
      this.hojaDescribir.set(null);
      this.responderHojaDescribir(abierta.id, false);
    }
    if (!this.estrecho() || !this.borrador().some((f) => f.clave === m.clave && f.imagenId !== null)) {
      this.responderHojaDescribir(m.id, false);
      return;
    }
    this.hoja.set(null);
    this.cerrarHojaMenu(null, false);
    this.cerrarHojaSedes(false);
    this.cerrarMenuCasos(false);
    this.textoDescribir.set(m.texto);
    this.hojaDescribir.set(m);
    this.medirBarraSinContar();
    // Boceto `primero.focus`: el foco, al texto.
    this.enfocarDespues('.flot--describir textarea');
  }

  /** Una pulsación en la hoja: a la capa, con el número de la apertura. */
  escribirDescribir(evento: Event): void {
    const abierta = this.hojaDescribir();
    const area = evento.target as HTMLTextAreaElement | null;
    const iframe = this.iframe()?.nativeElement;
    if (!abierta || !area || !this.ventana || !iframe) {
      return;
    }
    const texto = area.value.slice(0, MAX_DESCRIPCION_FOTO);
    this.textoDescribir.set(texto);
    enviarPorPuente(this.ventana, iframe.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'texto-hoja-describir', id: abierta.id, texto });
  }

  /**
   * Cierra la hoja de describir y se lo dice a la capa. Con «Listo», Escape o el asa, el foco vuelve a la foto; al
   * pulsar fuera, en la vista previa o al ensancharse el panel, no se mueve (como la hoja del menú).
   */
  cerrarHojaDescribir(devolverFoco: boolean): void {
    const abierta = this.hojaDescribir();
    if (abierta === null) {
      return;
    }
    this.hojaDescribir.set(null);
    this.responderHojaDescribir(abierta.id, devolverFoco);
  }

  private responderHojaDescribir(id: number, devolverFoco: boolean): void {
    const iframe = this.iframe()?.nativeElement;
    if (!this.ventana || !iframe) {
      return;
    }
    if (devolverFoco) {
      // El foco pasa a la vista previa (el panel lo tenía en la hoja que se cierra); la capa lo pone en la foto.
      iframe.focus({ preventScroll: true });
    }
    enviarPorPuente(this.ventana, iframe.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-describir', id, devolverFoco });
  }

  // ------------------------------------------------------------------ sedes de un profesor como hoja (W12f-3)

  /**
   * La capa (panel estrecho) pide la hoja de sedes de un profesor: la vista previa ocupa toda la altura y un diálogo
   * dentro de ella no queda a mano. La hoja se pinta con los datos del panel; si no se puede abrir, se contesta ya.
   */
  private abrirHojaSedes(n: number, id: number): void {
    const abierta = this.hojaSedes();
    if (abierta) {
      this.hojaSedes.set(null);
      this.responderHojaSedes(abierta.id, false);
    }
    if (!this.estrecho() || this.pagina() !== 'inicio' || !this.sedesElegibles().length || !numerosProfesores(this.borrador()).includes(n)) {
      this.responderHojaSedes(id, false);
      return;
    }
    this.hoja.set(null);
    this.cerrarHojaMenu(null, false);
    this.cerrarHojaDescribir(false);
    this.cerrarMenuCasos(false);
    this.hojaSedes.set({ n, id });
    this.enfocarDespues('.flot--sedes input[type="checkbox"]');
  }

  /**
   * Una casilla de la hoja: el mismo camino que la acción `sedes` de la capa (el conjunto entero). La casilla queda como
   * diga el borrador: si no se aceptó (guardando, por ejemplo), vuelve a su valor.
   */
  cambiarSedeHoja(evento: Event, id: number): void {
    const vista = this.vistaHojaSedes();
    const input = evento.target as HTMLInputElement | null;
    if (!vista || !input) {
      return;
    }
    const marcadas = vista.sedes.filter((s) => (s.id === id ? input.checked : s.marcada)).map((s) => s.id);
    this.elegirSedes(vista.n, marcadas);
    input.checked = this.vistaHojaSedes()?.sedes.find((s) => s.id === id)?.marcada ?? false;
  }

  /**
   * Cierra la hoja de sedes y se lo dice a la capa. Con «Listo» o Escape, el foco vuelve al botón; solo con «Listo»
   * (`anunciar`, boceto `listo-sedes`) se dicen las sedes: Escape solo cierra.
   */
  cerrarHojaSedes(devolverFoco: boolean, anunciar = false): void {
    const abierta = this.hojaSedes();
    if (abierta === null) {
      return;
    }
    this.hojaSedes.set(null);
    this.responderHojaSedes(abierta.id, devolverFoco);
    if (anunciar) {
      this.anunciarSedes(abierta.n);
    }
  }

  private responderHojaSedes(id: number, devolverFoco: boolean): void {
    const iframe = this.iframe()?.nativeElement;
    if (!this.ventana || !iframe) {
      return;
    }
    if (devolverFoco) {
      // El foco pasa a la vista previa (el panel lo tenía en la hoja que se cierra); la capa lo pone en el botón.
      iframe.focus({ preventScroll: true });
    }
    enviarPorPuente(this.ventana, iframe.contentWindow ?? null, { canal: CANAL_EDITOR_WEB, tipo: 'respuesta-hoja-sedes', id, devolverFoco });
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

  /** Mide y manda la zona en el siguiente fotograma (varios avisos en el mismo fotograma, un solo envío). */
  private pedirZona(): void {
    const ventana = this.ventana;
    if (!ventana || this.zonaPedida !== null) {
      return;
    }
    if (typeof ventana.requestAnimationFrame !== 'function') {
      this.enviarZona();
      return;
    }
    this.zonaPedida = ventana.requestAnimationFrame(() => {
      this.zonaPedida = null;
      this.enviarZona();
    });
  }

  /** Manda a la capa la parte de la vista previa que se ve, si cambió desde la última vez. */
  private enviarZona(): void {
    const ventana = this.ventana;
    const iframe = this.iframe()?.nativeElement;
    const destino = iframe?.contentWindow ?? null;
    const zona = ventana && iframe ? this.zonaVisible(ventana, iframe) : null;
    if (!ventana || !destino || !zona) {
      return;
    }
    const firma = `${zona.top}:${zona.alto}`;
    const campo = this.campoPedido && this.teclado();
    if (firma === this.zonaEnviada && !campo) {
      return;
    }
    this.zonaEnviada = firma;
    this.campoPedido = false;
    enviarPorPuente(ventana, destino, {
      canal: CANAL_EDITOR_WEB,
      tipo: 'zona-visible',
      top: zona.top,
      alto: zona.alto,
      ...(campo ? { campo: true as const } : {}),
    });
  }

  /**
   * d551: el visor que cuenta, o null si no hay o si hay zoom de pellizco (entonces todo es como antes de la pieza).
   * Un visor sin `scale` cuenta como sin zoom.
   */
  private visorSinZoom(): VisualViewport | null {
    const visor = this.visor;
    return visor && !((visor.scale ?? 1) > ESCALA_ZOOM) ? visor : null;
  }

  /**
   * d551: lo que el visor deja tapado por abajo (`innerHeight − (offsetTop + height)`). Con el teclado abierto, cuando su
   * alto (sin offsetTop) cambia, al abrirse o después, se espera a que se asiente y entonces se pide a la capa el texto en
   * edición con la zona; nunca por mover el visor o desplazar la ventana. La zona sin más se manda al momento.
   */
  private medirVisor(): void {
    this.medirBarraSinContar();
    const ventana = this.ventana;
    if (!ventana || !this.visor) {
      return;
    }
    const visor = this.visorSinZoom();
    const tapado = visor ? Math.max(0, Math.round(ventana.innerHeight - (visor.offsetTop + visor.height))) : 0;
    this.tapadoAbajo.set(tapado);
    this.bordeVisible.set(visor ? Math.round(visor.offsetTop + visor.height) : 0);
    this.altoVisible.set(visor ? Math.round(visor.height) : 0);
    const altoAntes = this.altoTeclado();
    this.altoTeclado.set(visor ? Math.max(0, Math.round(ventana.innerHeight - visor.height)) : 0);
    if (!this.teclado()) {
      this.campoPedido = false;
      this.pararEsperaTeclado();
    } else if (this.altoTeclado() !== altoAntes) {
      this.pararEsperaTeclado();
      this.temporizadorTeclado = setTimeout(() => {
        this.temporizadorTeclado = null;
        this.campoPedido = true;
        this.pedirZona();
      }, ESPERA_TECLADO_MS);
    }
    this.pedirZona();
  }

  /**
   * Lo que `innerHeight` pasa del alto pequeño (`100svh`): la barra de URL que Chrome de Android enseña al volver del selector
   * de archivos sin que la página lo sepa (0 si ya está contada, como siempre en escritorio). Solo se mide con la hoja de
   * describir abierta tras subir una foto; en el resto de casos queda en 0 y no se crea la sonda.
   */
  private medirBarraSinContar(): void {
    let valor = 0;
    if (this.ventana && this.hojaDescribir()?.trasSubir === true) {
      const svh = this.altoSvh();
      valor = svh > 0 ? Math.min(120, Math.max(0, Math.round(this.ventana.innerHeight - svh))) : 0;
    }
    if (valor !== this.barraSinContar()) {
      this.barraSinContar.set(valor);
    }
  }

  /** El alto de `100svh` en px, medido con una sonda fija e invisible que se retira al momento; 0 si no se entiende `svh`. */
  private altoSvh(): number {
    const doc = this.ventana?.document;
    if (!doc?.body) {
      return 0;
    }
    const sonda = doc.createElement('div');
    sonda.setAttribute('aria-hidden', 'true');
    sonda.style.cssText = 'position:fixed;top:0;left:0;width:0;height:100svh;visibility:hidden;pointer-events:none;';
    doc.body.appendChild(sonda);
    const alto = sonda.getBoundingClientRect().height;
    sonda.remove();
    return alto;
  }

  /**
   * Vuelve a medir el visor en el siguiente fotograma (varios avisos en el mismo fotograma, una sola medida): Chrome puede
   * avisar antes de dar las medidas finales. Si el alto del teclado no cambia, no reinicia la espera de d551.
   */
  private remedirVisor(): void {
    const ventana = this.ventana;
    if (!ventana || this.remedidaPedida !== null || typeof ventana.requestAnimationFrame !== 'function') {
      return;
    }
    this.remedidaPedida = ventana.requestAnimationFrame(() => {
      this.remedidaPedida = null;
      this.medirVisor();
    });
  }

  private pararEsperaTeclado(): void {
    if (this.temporizadorTeclado !== null) {
      clearTimeout(this.temporizadorTeclado);
      this.temporizadorTeclado = null;
    }
  }

  /**
   * La parte de la ventana que se ve, en px de la ventana: el visor (sin él, la ventana entera) menos lo que el panel
   * pone fijo encima (cabecera y franja del caso arriba; barra de cambios y navegación del móvil abajo).
   */
  private limitesVisibles(ventana: Window): { arriba: number; abajo: number } {
    const doc = this.host.nativeElement.ownerDocument;
    const visor = this.visorSinZoom();
    const visorArriba = visor ? visor.offsetTop : 0;
    const visorAbajo = visor ? visor.offsetTop + visor.height : ventana.innerHeight;
    const mitad = (visorArriba + visorAbajo) / 2;
    const tapa = (el: Element | null): DOMRect | null => {
      if (!el) {
        return null;
      }
      const r = el.getBoundingClientRect();
      return r.height > 0 && r.width > 0 && getComputedStyle(el).visibility !== 'hidden' ? r : null;
    };
    let arriba = visorArriba;
    let abajo = visorAbajo;
    [...TAPAS_ARRIBA.map((s) => doc.querySelector(s)), this.casoActivoEl()?.nativeElement ?? null].forEach((el) => {
      const r = tapa(el);
      if (r && r.top < mitad) {
        arriba = Math.max(arriba, r.bottom);
      }
    });
    [...TAPAS_ABAJO.map((s) => doc.querySelector(s)), this.host.nativeElement.querySelector('.barra')].forEach((el) => {
      const r = tapa(el);
      if (r && r.bottom > mitad) {
        abajo = Math.min(abajo, r.top);
      }
    });
    return { arriba, abajo };
  }

  /**
   * La parte del iframe que se ve, en píxeles CSS de su documento: el marco en la ventana menos lo que el panel pone
   * fijo encima (cabecera y franja del caso arriba; barra de cambios y navegación del móvil abajo), deshecha la escala.
   * Quien se desplaza es la ventana del panel (el iframe mide todo el documento). Null si no se ve nada.
   */
  private zonaVisible(ventana: Window, iframe: HTMLIFrameElement): { top: number; alto: number } | null {
    const marco = iframe.getBoundingClientRect();
    const escala = iframe.offsetHeight > 0 ? marco.height / iframe.offsetHeight : 0;
    if (!(escala > 0)) {
      return null;
    }
    // d551: con el visor (el teclado del móvil lo encoge) en lugar de 0 e `innerHeight`.
    const limites = this.limitesVisibles(ventana);
    const arriba = Math.max(marco.top, limites.arriba);
    const abajo = Math.min(marco.bottom, limites.abajo);
    const top = Math.round((arriba - marco.top) / escala);
    const alto = Math.round((abajo - arriba) / escala);
    return alto >= 1 ? { top: Math.max(0, top), alto } : null;
  }

  /**
   * Lleva a la vista la parte del caso (boceto `ponerCaso`), bajo la barra del panel y la franja del caso. d551: con el
   * teclado del móvil abierto, el límite de abajo es el borde de lo que se ve (la barra compacta o el teclado), no el 60 %
   * de la ventana; `forzar`: el texto en edición que la capa ya vio tapado.
   */
  private ensenar(arriba: number, forzar = false): void {
    const ventana = this.ventana;
    const iframe = this.iframe()?.nativeElement;
    if (!ventana || !iframe || typeof ventana.scrollTo !== 'function') {
      return;
    }
    const y = iframe.getBoundingClientRect().top + ventana.scrollY + arriba * this.escala();
    let tapa = this.altoCabeceraPanel() + (this.casoActivoEl()?.nativeElement.offsetHeight ?? 0) + 16;
    let limite = ventana.innerHeight * 0.6;
    if (this.teclado()) {
      const visible = this.limitesVisibles(ventana);
      tapa = Math.max(tapa, visible.arriba + 16);
      limite = visible.abajo - MARGEN_CAMPO;
    }
    if (forzar || y < ventana.scrollY + tapa || y > ventana.scrollY + limite) {
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
    this.pararEsperaTeclado();
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

  private enfocarTitulo(desplazar = true): void {
    this.host.nativeElement.querySelector<HTMLElement>('h1')?.focus({ preventScroll: !desplazar });
  }
}
