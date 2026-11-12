import type { BloqueWeb } from '../datos/modelos';

/**
 * Protocolo del puente `postMessage` entre «Web · Contenido» (el panel, W12c) y la capa de edición que corre dentro
 * del iframe de la vista previa (`/?vista-previa=<tema>&editor=1`). Lo comparten los dos lados: solo tipos y
 * comprobaciones puras, sin dependencias de Angular.
 *
 * Reglas de los dos lados:
 * - el destino de `postMessage` es siempre el origen propio (`window.origin`), nunca `'*'`;
 * - un mensaje se atiende solo si llega del mismo origen, de la ventana esperada (el iframe o su padre) y con una
 *   forma conocida; lo demás se ignora en silencio;
 * - el iframe nunca da URL ni rutas: pide un destino de una lista cerrada y el panel decide adónde navega.
 *
 * W12c-bis: «Ver el caso» (d528) manda al iframe uno de los casos de una lista cerrada (`caso`); la capa lo simula en
 * memoria sobre los datos de la vista previa y nunca toca la API. La capa pide al panel que enseñe la parte del caso
 * (`mostrar`, solo un número).
 *
 * W12c-ter: el selector «Paso» de la preinscripción manda al iframe el paso que se ve (`paso`, un entero de la lista
 * cerrada 1-4); la capa enseña ese paso con datos de ejemplo en memoria, sin escribir en el navegador ni enviar nada.
 *
 * W12d: los textos se cambian en el sitio. La capa manda cada cambio (`texto`: clave del catálogo y texto) y los textos
 * por defecto que pinta el tema para las claves que no están guardadas (`defectos`); el panel guarda el borrador, valida
 * y guarda en la API. El panel manda los errores que hay que señalar en la página (`errores`) y pide llevar el foco a un
 * texto desde el resumen de errores (`ir-texto`). Cada tipo nuevo se añade a su unión y a su comprobación.
 *
 * W12f-1: fotos sueltas (portada, presentación y «Texto con foto»). La capa elige el fichero y lo pasa al panel
 * (`subir-foto`, un `File`), que lo sube a la API y le devuelve el estado de cada subida (`fotos`); la capa nunca llama a
 * la API. La descripción (`descripcion`), «Volver a la anterior», «Quitar foto» y «Cancelar subida» (`foto`) también los
 * guarda el panel en el borrador. Al terminar una subida, el panel pide a la capa abrir el diálogo de describir
 * (`describir`).
 *
 * W12f-2a: las fotos de la galería (`galeria.<n>`) van por los mismos mensajes. Una foto más se sube con la clave
 * `galeria.nueva`; al terminar, el panel le da su número y pide describirla con él. «Mover antes» y «Mover después» son
 * acciones de `foto`.
 *
 * W12f-2b: profesores. Sus fotos (`profesor.<n>.foto`) van por los mismos mensajes que las demás. La capa pide añadir y
 * quitar un profesor y marcar o desmarcar su casilla del permiso (`profesor`); el panel (que confirma, numera y guarda)
 * le manda cada profesor con su casilla o su constancia ya en texto (`profesores`, solo para el editor: el tema nunca la
 * recibe, d521) y a dónde va el foco (`foco-profesor`). Nunca viaja el objeto del permiso.
 *
 * W12f-3: sedes de cada profesor. `profesores` lleva además las sedes que se pueden elegir (`sedes`, en su orden) y, por
 * profesor, las marcadas. La capa pide el conjunto nuevo (`profesor`, acción `sedes`); el panel lo filtra, lo ordena, lo
 * escribe en `profesor.<n>.sedes` y devuelve el estado. Al cerrar su diálogo con «Listo» o Escape, la capa pide al panel
 * que diga las sedes (`profesor`, acción `anunciar-sedes`). Con el panel estrecho el diálogo es una hoja del panel: la
 * capa la pide (`hoja-sedes`, con el número de apertura), el panel la pinta con sus datos y avisa al cerrarla
 * (`respuesta-hoja-sedes`); si la capa la cierra sola, se lo dice (`cerrar-hoja-sedes`). Un enlace del resumen lleva al
 * botón de sedes de la tarjeta (`ir-texto` con `profesor.<n>.sedes`).
 *
 * W12e: bloques de Inicio. La capa dice qué bloques hay en la página y cuáles están vacíos (`bloques`) y pide las
 * acciones de la barra (`bloque`), añadir un bloque en un puesto (`anadir-bloque`) y, con el panel estrecho, la hoja de
 * acciones del bloque (`hoja`), que vive en el panel. El panel cambia el borrador, manda a la capa qué partes tienen
 * cambios sin guardar, qué bloques se pueden quitar y si el panel es estrecho (`estado-bloques`) y a dónde va el foco
 * tras cada acción (`foco-bloque`).
 *
 * W12d-bis: un dato dentro de un texto (ficha de un hueco `{x}`) no se borra sin confirmar. La capa pide al panel el
 * diálogo (`pedir-quitar-dato`: el texto, sus huecos y cómo se ve la frase antes y después) y el panel contesta si se
 * quita (`quitar-dato`).
 */
import {
  ACCIONES_BLOQUE,
  type AccionBloque,
  MAX_BLOQUES,
  type TipoBloque,
  esFocoBloque,
  esIdBloque,
  esParteInicio,
  esTipoBloque,
} from './bloques-editor';
import { GALERIA_NUEVA, esFotoGaleria } from './fotos-galeria';
import {
  type EstadoProfesor,
  MAX_SEDES_PROFESOR,
  type SedeElegible,
  esIdSede,
  esNumeroProfesor,
  numeroPermiso,
  numeroSedes,
} from './fotos-profesores';
import { MAX_DESCRIPCION_FOTO, esClaveSubida, esFotoEditable, fotoDeDescripcion } from './fotos-sueltas';
import { huecoDe } from './huecos-editor';
import { esTextoEditable } from './textos-editor';

/** Marca de los mensajes de este puente: los de otras librerías o extensiones no la llevan. */
export const CANAL_EDITOR_WEB = 'taemoi-editor-web';

/** Pantallas del panel a las que lleva un candado (d517). El panel traduce cada una a su ruta. */
export const DESTINOS_PANEL = ['sedes', 'grupos', 'eventos', 'productos', 'datos-escuela', 'plantillas'] as const;
export type DestinoPanel = (typeof DESTINOS_PANEL)[number];

/** Una fila del borrador tal como la pinta el tema (`BloqueWeb`); sin la constancia del permiso de los profesores. */
export type BloqueBorrador = Pick<BloqueWeb, 'clave' | 'texto' | 'imagenUrl' | 'orden' | 'oculto'>;

// ------------------------------------------------------------------ del panel al iframe

/** El acento de la escuela en el panel (`--pn-acento`, `-borde`, `-suave`), para la capa de edición. Solo hex. */
export interface AcentoEditor {
  acento: string;
  borde: string;
  suave: string;
}

/** El borrador entero: el iframe pinta con sus datos públicos y estos bloques. */
export interface MensajeBorrador {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'borrador';
  bloques: BloqueBorrador[];
  /** Sin él, la capa usa el acento por defecto del panel. */
  acento?: AcentoEditor;
}

/**
 * Casos poco frecuentes que «Ver el caso» simula en la vista previa (d528; `CASOS` del boceto). Lista cerrada: el
 * panel no manda datos, solo el nombre del caso.
 */
export const CASOS_VISTA = [
  'lleno',
  'espera',
  'sin-tarifas',
  'sin-proximos',
  'sin-eventos',
  'un-evento',
  'sin-anteriores',
  'una-foto',
  // W12c-ter: «Sin clases esta semana» de una sede y los de la preinscripción («Grupo completo» es `espera`, como el boceto).
  'sin-clases',
  'sin-grupo',
  'fin-espera',
] as const;
export type CasoVista = (typeof CASOS_VISTA)[number];

/** El caso que se ve (null: los datos reales). No es un cambio y no se guarda. */
export interface MensajeCaso {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'caso';
  caso: CasoVista | null;
}

/** Los pasos de la preinscripción del selector «Paso» (boceto `SUBS.preinscripcion`): 1 Pueblo … 4 Enviada. */
export const PASOS_PREINSCRIPCION = [1, 2, 3, 4] as const;
export type PasoPreinscripcion = (typeof PASOS_PREINSCRIPCION)[number];

/** El paso de la preinscripción que se ve en la vista previa (W12c-ter). No es un cambio y no se guarda. */
export interface MensajePaso {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'paso';
  paso: PasoPreinscripcion;
}

/** W12d: los errores de validación por clave (vacío: ninguno). La capa los señala en el primer sitio de cada texto. */
export interface MensajeErrores {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'errores';
  errores: Record<string, string>;
}

/** W12d: llevar el foco a un texto (enlace del resumen de errores). `portada.foto`: a la portada. */
export interface MensajeIrTexto {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'ir-texto';
  clave: string;
}

/** W12f-1: una subida en curso o fallida (boceto `s.subidas`). */
export type EstadoSubidaFoto =
  | { estado: 'subiendo'; pct: number; nombre: string }
  | { estado: 'error'; titulo: string; msg: string };

/**
 * W12f-1: el estado de las fotos sueltas que no está en el borrador: las cambiadas respecto a lo guardado («Nueva, sin
 * guardar»), las que tienen una anterior a la que volver y las subidas.
 */
export interface MensajeFotos {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'fotos';
  cambiadas: string[];
  anteriores: string[];
  subidas: Record<string, EstadoSubidaFoto>;
}

/** W12f-1: la subida terminó; la capa abre «Foto subida: describe la foto…». */
export interface MensajeDescribir {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'describir';
  clave: string;
}

/** W12e: el estado de los bloques que no está en el borrador. */
export interface MensajeEstadoBloques {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'estado-bloques';
  /** Bloques y partes con cambios sin guardar (pastilla «Sin guardar»). */
  sinGuardar: string[];
  /** Bloques que se pueden quitar: cada «Texto con foto» y los añadidos sin guardar. */
  quitables: string[];
  /** El panel mide menos de 768 px: las acciones van en la hoja inferior del panel. */
  estrecho: boolean;
}

/** W12e: tras una acción, el foco a un botón de la barra, al nombre del bloque o a un hueco «Añadir bloque». */
export interface MensajeFocoBloque {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'foco-bloque';
  foco: string;
}

/** W12d-bis: la respuesta al diálogo «¿Quitar … de este texto?»: true, «Quitar dato»; false, «Cancelar» (o Escape). */
export interface MensajeQuitarDato {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'quitar-dato';
  quitar: boolean;
}

/** Los dos menús de la capa que con el panel estrecho salen como hoja inferior del panel (d541). */
export const MENUS_HOJA = ['foto', 'anadir'] as const;
export type MenuHoja = (typeof MENUS_HOJA)[number];

/** Iconos que puede llevar una opción de esos menús (el panel los dibuja con los suyos). */
export const ICONOS_HOJA_MENU = ['foto', 'texto', 'deshacer', 'papelera', 'alumnos', 'antes', 'despues'] as const;
export type IconoHojaMenu = (typeof ICONOS_HOJA_MENU)[number];

/** Una opción del menú tal como la dibuja la hoja (boceto `.flot__op`): texto, pie opcional y estado. */
export interface OpcionHojaMenu {
  /** Lo que la capa recibirá de vuelta al elegirla (`subir`, `quitar`, un tipo de bloque…). */
  op: string;
  texto: string;
  /** Segunda línea pequeña; vacía si no la lleva. */
  pie: string;
  icono: IconoHojaMenu;
  desactivada: boolean;
  /** «Quitar»: color de peligro y, si no es la primera, un separador antes. */
  peligro: boolean;
}

/** d541: la capa de un panel estrecho pide al panel que muestre su menú (foto, añadir bloque) como hoja inferior. */
export interface MensajeHojaMenu {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'hoja-menu';
  menu: MenuHoja;
  /** Número de esta apertura (la capa lo incrementa en cada una): el panel lo devuelve y la capa descarta lo de otro número. */
  id: number;
  titulo: string;
  opciones: OpcionHojaMenu[];
}

/**
 * d541: la respuesta del panel a `hoja-menu`: `op` es la opción elegida o null si la hoja se cerró sin elegir.
 * `devolverFoco`: el foco vuelve al botón que abrió el menú (Escape, Tab) o no (velo, pulsar fuera, panel ancho).
 */
export interface MensajeRespuestaHojaMenu {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'respuesta-hoja-menu';
  menu: MenuHoja;
  /** El `id` del `hoja-menu` al que responde. */
  id: number;
  op: string | null;
  devolverFoco: boolean;
}

/**
 * d541: la capa cerró sola un menú que estaba en la hoja del panel (segundo toque, repintado, otro menú…): que el panel
 * la quite si sigue siendo esa (mismo `menu` e `id`), sin responder ni mover el foco.
 */
export interface MensajeCerrarHojaMenu {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'cerrar-hoja-menu';
  menu: MenuHoja;
  id: number;
}

/**
 * W12f-2b: los profesores del borrador, en orden, con su nombre y su casilla o su constancia (solo el texto). W12f-3: y
 * sus sedes marcadas (`EstadoProfesor.sedes`). Se reenvía cada vez que algo de esto cambia.
 */
export interface MensajeProfesores {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'profesores';
  lista: EstadoProfesor[];
  /**
   * W12f-3: las sedes que se pueden elegir, en el orden en que se pintan (el de la API), sin ids repetidos. Vacía
   * mientras se leen o si no se pudieron leer: entonces no se puede cambiar ninguna.
   */
  sedes: SedeElegible[];
}

/**
 * W12f-2b: tras una acción, el foco: al «Quitar» de un profesor (`quitar:3`), a su casilla (`permiso:3`), a editar su
 * nombre (`editar:3`) o al de añadir (`anadir`).
 */
export interface MensajeFocoProfesor {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'foco-profesor';
  foco: string;
}

/**
 * W12f-3: el panel cerró la hoja de sedes (`hoja-sedes` con ese `id`). `devolverFoco`: con «Listo» o Escape, el foco
 * vuelve al botón de sedes de la tarjeta; sin él (pulsar fuera, panel ancho, no se pudo abrir), no se mueve.
 */
export interface MensajeRespuestaHojaSedes {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'respuesta-hoja-sedes';
  id: number;
  devolverFoco: boolean;
}

export type MensajeDelPanel =
  | MensajeProfesores
  | MensajeRespuestaHojaSedes
  | MensajeFocoProfesor
  | MensajeRespuestaHojaMenu
  | MensajeQuitarDato
  | MensajeEstadoBloques
  | MensajeFocoBloque
  | MensajeBorrador
  | MensajeCaso
  | MensajePaso
  | MensajeErrores
  | MensajeIrTexto
  | MensajeFotos
  | MensajeDescribir;

// ------------------------------------------------------------------ del iframe al panel

/** La capa de edición está activa (la API confirmó la vista previa de gestión) y espera el borrador. */
export interface MensajeListo {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'listo';
}

/** Alto del documento en píxeles CSS, para que el marco del panel lo enseñe entero. */
export interface MensajeAlto {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'alto';
  alto: number;
}

/** Un candado pide ir a la pantalla del panel donde se cambia ese dato. */
export interface MensajeIr {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'ir';
  destino: DestinoPanel;
}

/** Tras pintar un caso, dónde empieza su parte (px CSS desde arriba del documento del iframe), para enseñarla. */
export interface MensajeMostrar {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'mostrar';
  arriba: number;
}

/** W12d: un texto cambiado en el sitio. */
export interface MensajeTexto {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'texto';
  clave: string;
  texto: string;
}

/** W12d: lo que pinta el tema en las claves sin guardar cuyo texto por defecto depende de los datos (titular, frase…). */
export interface MensajeDefectos {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'defectos';
  textos: Record<string, string>;
}

/** W12f-1: el fichero elegido para una foto (W12f-2a: también `galeria.nueva`, una foto más en la galería); lo sube el panel. */
export interface MensajeSubirFoto {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'subir-foto';
  clave: string;
  archivo: File;
}

/** W12f-1: acciones del menú y de la subida de una foto. W12f-2a: «Mover antes» y «Mover después» (galería). */
export const ACCIONES_FOTO = ['cancelar-subida', 'anterior', 'quitar', 'antes', 'despues'] as const;
export type AccionFoto = (typeof ACCIONES_FOTO)[number];

export interface MensajeAccionFoto {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'foto';
  clave: string;
  accion: AccionFoto;
}

/** W12f-1: la descripción de una foto (cada cambio del diálogo). */
export interface MensajeDescripcion {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'descripcion';
  clave: string;
  texto: string;
}

/** W12e: los bloques de Inicio que hay en la página, en su orden, y los vacíos. */
export interface MensajeBloques {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'bloques';
  orden: string[];
  vacios: string[];
}

/** W12e: una acción de la barra de un bloque. */
export interface MensajeAccionBloque {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'bloque';
  accion: AccionBloque;
  bloque: string;
}

/** W12e: añadir un bloque de ese tipo en el puesto `pos` (0: antes del primero). */
export interface MensajeAnadirBloque {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'anadir-bloque';
  pos: number;
  bloque: TipoBloque;
}

/** W12e: con el panel estrecho, abrir la hoja de acciones del bloque. */
export interface MensajeHoja {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'hoja';
  bloque: string;
}

/** W12d-bis: se intentó borrar la ficha de uno o varios huecos de un texto; el panel pregunta antes de quitarlos. */
export interface MensajePedirQuitarDato {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'pedir-quitar-dato';
  clave: string;
  /** Los huecos, sin llaves (`x`, `grupo`). */
  huecos: string[];
  /** La frase hoy, con los datos de la vista previa, y sin esos datos. */
  antes: string;
  despues: string;
}

/**
 * W12f-2b: añadir un profesor, quitarlo (el panel confirma) o marcar o desmarcar su casilla del permiso.
 *
 * W12f-3: `sedes`, el conjunto entero de sedes marcadas para el profesor `n` (ids de `MensajeProfesores.sedes`; el orden
 * da igual y vacío es «ninguna»). Aquí solo se comprueba la forma (enteros positivos seguros, sin repetidos, como mucho
 * `MAX_SEDES_PROFESOR`); el panel descarta la petición si `n` no está en el borrador o trae más ids que sedes elegibles,
 * se queda con los de la lista en el orden de la lista y no la atiende fuera de Inicio ni mientras guarda. A toda petición con forma válida
 * contesta con `profesores` (el estado que vale), también si no cambia nada: la capa pinta siempre lo que manda el panel.
 * `anunciar-sedes`: se cerró el diálogo de sedes de `n` con «Listo» o Escape; el panel dice sus sedes («Sedes: A, B.»).
 */
export type MensajeProfesor = { canal: typeof CANAL_EDITOR_WEB; tipo: 'profesor' } & (
  | { accion: 'anadir' }
  | { accion: 'quitar'; n: number }
  | { accion: 'permiso'; n: number; marcada: boolean }
  | { accion: 'sedes'; n: number; sedes: number[] }
  | { accion: 'anunciar-sedes'; n: number }
);

/**
 * W12f-3: con el panel estrecho, la capa pide la hoja de sedes del profesor `n` (el panel la pinta con sus datos).
 * `id`: número de esta apertura (la capa lo incrementa); el panel lo devuelve en `respuesta-hoja-sedes`.
 */
export interface MensajeHojaSedes {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'hoja-sedes';
  n: number;
  id: number;
}

/** W12f-3: la capa cerró sola la hoja de sedes `id` (repintado, el profesor ya no está…): que el panel la quite. */
export interface MensajeCerrarHojaSedes {
  canal: typeof CANAL_EDITOR_WEB;
  tipo: 'cerrar-hoja-sedes';
  id: number;
}

export type MensajeDelEditor =
  | MensajeProfesor
  | MensajeHojaSedes
  | MensajeCerrarHojaSedes
  | MensajeHojaMenu
  | MensajeCerrarHojaMenu
  | MensajePedirQuitarDato
  | MensajeBloques
  | MensajeAccionBloque
  | MensajeAnadirBloque
  | MensajeHoja
  | MensajeListo
  | MensajeAlto
  | MensajeIr
  | MensajeMostrar
  | MensajeTexto
  | MensajeDefectos
  | MensajeSubirFoto
  | MensajeAccionFoto
  | MensajeDescripcion;

/** Más largo que el mayor de los textos editables (1200): lo que pase de aquí no es de la capa. */
const LARGO_MAXIMO = 5000;

const esTextoCorto = (valor: unknown): valor is string => typeof valor === 'string' && valor.length <= LARGO_MAXIMO;

function esMapaDeTextos(valor: unknown, clave: (k: string) => boolean): boolean {
  return esObjeto(valor) && Object.entries(valor).every(([k, v]) => clave(k) && esTextoCorto(v));
}

// ------------------------------------------------------------------ comprobaciones

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor);
}

export function esDestinoPanel(valor: unknown): valor is DestinoPanel {
  return typeof valor === 'string' && (DESTINOS_PANEL as readonly string[]).includes(valor);
}

export function esCasoVista(valor: unknown): valor is CasoVista {
  return typeof valor === 'string' && (CASOS_VISTA as readonly string[]).includes(valor);
}

export function esPasoPreinscripcion(valor: unknown): valor is PasoPreinscripcion {
  return typeof valor === 'number' && (PASOS_PREINSCRIPCION as readonly number[]).includes(valor);
}

function textoONulo(valor: unknown): boolean {
  return valor === null || typeof valor === 'string';
}

function esBloqueBorrador(valor: unknown): valor is BloqueBorrador {
  return (
    esObjeto(valor) &&
    typeof valor['clave'] === 'string' &&
    textoONulo(valor['texto']) &&
    textoONulo(valor['imagenUrl']) &&
    typeof valor['orden'] === 'number' &&
    Number.isFinite(valor['orden']) &&
    (valor['oculto'] === undefined || typeof valor['oculto'] === 'boolean')
  );
}

const HEX = /^#[0-9a-f]{6}$/i;

function esAcento(valor: unknown): valor is AcentoEditor {
  return esObjeto(valor) && [valor['acento'], valor['borde'], valor['suave']].every((c) => typeof c === 'string' && HEX.test(c));
}

const esListaDeFotos = (valor: unknown) => Array.isArray(valor) && valor.length <= 100 && valor.every(esFotoEditable);
const esTextoDeSubida = (valor: unknown) => typeof valor === 'string' && valor.length <= 500;

function esEstadoSubida(valor: unknown): valor is EstadoSubidaFoto {
  if (!esObjeto(valor)) {
    return false;
  }
  if (valor['estado'] === 'subiendo') {
    const pct = valor['pct'];
    return typeof pct === 'number' && Number.isFinite(pct) && pct >= 0 && pct <= 100 && esTextoDeSubida(valor['nombre']);
  }
  return valor['estado'] === 'error' && esTextoDeSubida(valor['titulo']) && esTextoDeSubida(valor['msg']);
}

/**
 * Las claves a las que lleva un enlace del resumen: un texto, una foto (suelta, de la galería o de un profesor), su
 * descripción, (W12f-2b) la casilla del permiso de un profesor o (W12f-3) el botón de sus sedes.
 */
const esClaveIrA = (clave: unknown) =>
  typeof clave === 'string' &&
  (esTextoEditable(clave) ||
    esFotoEditable(clave) ||
    fotoDeDescripcion(clave) !== null ||
    numeroPermiso(clave) !== null ||
    numeroSedes(clave) !== null);

/** W12f-2b: el foco tras una acción de profesor. */
export function esFocoProfesor(valor: unknown): valor is string {
  if (valor === 'anadir') {
    return true;
  }
  const m = typeof valor === 'string' ? /^(quitar|permiso|editar):([1-9][0-9]?)$/.exec(valor) : null;
  return !!m;
}

function esPermisoProfesor(v: unknown): boolean {
  if (!esObjeto(v)) {
    return false;
  }
  if (v['tipo'] === 'casilla') {
    return typeof v['marcada'] === 'boolean';
  }
  return v['tipo'] === 'constancia' && typeof v['texto'] === 'string' && v['texto'].length <= 200;
}

/** W12f-3: ids de sede sin repetir, cada uno un entero positivo seguro, como mucho `max`. */
export function esListaIdsSedes(valor: unknown, max: number = MAX_SEDES_PROFESOR): valor is number[] {
  return Array.isArray(valor) && valor.length <= max && valor.every(esIdSede) && new Set(valor).size === valor.length;
}

/** W12f-3: las sedes elegibles: ids sin repetir y un nombre corto. */
function esSedesElegibles(valor: unknown): valor is SedeElegible[] {
  return (
    Array.isArray(valor) &&
    valor.length <= MAX_SEDES_PROFESOR &&
    valor.every((s) => esObjeto(s) && esIdSede(s['id']) && typeof s['nombre'] === 'string' && s['nombre'].length <= 200) &&
    new Set(valor.map((s) => (s as SedeElegible).id)).size === valor.length
  );
}

/** W12f-3: las sedes de cada profesor, solo de las elegibles (`ids`). */
function esListaProfesores(valor: unknown, ids: ReadonlySet<number>): valor is EstadoProfesor[] {
  return (
    Array.isArray(valor) &&
    valor.length <= 99 &&
    valor.every(
      (p) =>
        esObjeto(p) &&
        esNumeroProfesor(p['n']) &&
        esTextoCorto(p['nombre']) &&
        esPermisoProfesor(p['permiso']) &&
        esListaIdsSedes(p['sedes'], ids.size) &&
        (p['sedes'] as number[]).every((id) => ids.has(id)),
    ) &&
    new Set(valor.map((p) => (p as EstadoProfesor).n)).size === valor.length
  );
}

const esIdHoja = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const esMenuHoja = (v: unknown): v is MenuHoja => (MENUS_HOJA as readonly unknown[]).includes(v);

function esOpcionHojaMenu(v: unknown): v is OpcionHojaMenu {
  return (
    esObjeto(v) &&
    typeof v['op'] === 'string' &&
    v['op'].length > 0 &&
    v['op'].length <= 40 &&
    typeof v['texto'] === 'string' &&
    v['texto'].length <= 120 &&
    typeof v['pie'] === 'string' &&
    v['pie'].length <= 200 &&
    (ICONOS_HOJA_MENU as readonly unknown[]).includes(v['icono']) &&
    typeof v['desactivada'] === 'boolean' &&
    typeof v['peligro'] === 'boolean'
  );
}

/** Lista de ids sin repetir, cada uno válido, como mucho `MAX_BLOQUES` + las partes fijas. */
function esListaDeIds(valor: unknown, valido: (id: unknown) => boolean): valor is string[] {
  return Array.isArray(valor) && valor.length <= MAX_BLOQUES + 20 && valor.every(valido) && new Set(valor).size === valor.length;
}

/** Lo que el iframe acepta del panel. */
export function esMensajeDelPanel(valor: unknown): valor is MensajeDelPanel {
  if (!esObjeto(valor) || valor['canal'] !== CANAL_EDITOR_WEB) {
    return false;
  }
  switch (valor['tipo']) {
    case 'profesores': {
      const sedes = valor['sedes'];
      return esSedesElegibles(sedes) && esListaProfesores(valor['lista'], new Set(sedes.map((s) => s.id)));
    }
    case 'foco-profesor':
      return esFocoProfesor(valor['foco']);
    case 'respuesta-hoja-sedes':
      return esIdHoja(valor['id']) && typeof valor['devolverFoco'] === 'boolean';
    case 'borrador':
      return (
        Array.isArray(valor['bloques']) &&
        valor['bloques'].every(esBloqueBorrador) &&
        (valor['acento'] === undefined || esAcento(valor['acento']))
      );
    case 'caso':
      return valor['caso'] === null || esCasoVista(valor['caso']);
    case 'paso':
      return esPasoPreinscripcion(valor['paso']);
    case 'errores':
      return esMapaDeTextos(valor['errores'], (k) => k.length <= 120);
    case 'ir-texto':
      return esClaveIrA(valor['clave']);
    case 'fotos':
      return (
        esListaDeFotos(valor['cambiadas']) &&
        esListaDeFotos(valor['anteriores']) &&
        esObjeto(valor['subidas']) &&
        Object.entries(valor['subidas']).every(([k, v]) => esClaveSubida(k) && esEstadoSubida(v))
      );
    case 'describir':
      return esFotoEditable(valor['clave']);
    case 'estado-bloques':
      return esListaDeIds(valor['sinGuardar'], esParteInicio) && esListaDeIds(valor['quitables'], esIdBloque) && typeof valor['estrecho'] === 'boolean';
    case 'foco-bloque':
      return esFocoBloque(valor['foco']);
    case 'quitar-dato':
      return typeof valor['quitar'] === 'boolean';
    case 'respuesta-hoja-menu':
      return (
        esMenuHoja(valor['menu']) &&
        esIdHoja(valor['id']) &&
        (valor['op'] === null || (typeof valor['op'] === 'string' && valor['op'].length <= 40)) &&
        typeof valor['devolverFoco'] === 'boolean'
      );
    default:
      return false;
  }
}

/** Lo que el panel acepta del iframe. */
export function esMensajeDelEditor(valor: unknown): valor is MensajeDelEditor {
  if (!esObjeto(valor) || valor['canal'] !== CANAL_EDITOR_WEB) {
    return false;
  }
  switch (valor['tipo']) {
    case 'listo':
      return true;
    case 'profesor':
      switch (valor['accion']) {
        case 'anadir':
          return true;
        case 'quitar':
          return esNumeroProfesor(valor['n']);
        case 'permiso':
          return esNumeroProfesor(valor['n']) && typeof valor['marcada'] === 'boolean';
        case 'sedes':
          return esNumeroProfesor(valor['n']) && esListaIdsSedes(valor['sedes']);
        case 'anunciar-sedes':
          return esNumeroProfesor(valor['n']);
        default:
          return false;
      }
    case 'hoja-sedes':
      return esNumeroProfesor(valor['n']) && esIdHoja(valor['id']);
    case 'cerrar-hoja-sedes':
      return esIdHoja(valor['id']);
    case 'alto':
      return typeof valor['alto'] === 'number' && Number.isFinite(valor['alto']) && valor['alto'] >= 0;
    case 'ir':
      return esDestinoPanel(valor['destino']);
    case 'mostrar':
      return typeof valor['arriba'] === 'number' && Number.isFinite(valor['arriba']) && valor['arriba'] >= 0;
    case 'texto':
      return typeof valor['clave'] === 'string' && esTextoEditable(valor['clave']) && esTextoCorto(valor['texto']);
    case 'defectos':
      return esMapaDeTextos(valor['textos'], esTextoEditable);
    case 'subir-foto': {
      // El `File` llega clonado a esta ventana (`postMessage`): es un `Blob` de aquí con su nombre.
      const archivo = valor['archivo'];
      return esClaveSubida(valor['clave']) && archivo instanceof Blob && typeof (archivo as File).name === 'string';
    }
    case 'foto':
    {
      // La foto nueva de la galería solo tiene su subida (cancelarla o cerrar su fallo); solo las de la galería se mueven.
      const accion = valor['accion'];
      const clave = valor['clave'];
      if (!(ACCIONES_FOTO as readonly unknown[]).includes(accion)) {
        return false;
      }
      if (clave === GALERIA_NUEVA) {
        return accion === 'cancelar-subida';
      }
      return accion === 'antes' || accion === 'despues' ? esFotoGaleria(clave) : esFotoEditable(clave);
    }
    case 'descripcion': {
      const texto = valor['texto'];
      return esFotoEditable(valor['clave']) && typeof texto === 'string' && texto.length <= MAX_DESCRIPCION_FOTO;
    }
    case 'bloques':
      return esListaDeIds(valor['orden'], esIdBloque) && esListaDeIds(valor['vacios'], esIdBloque);
    case 'bloque':
      return esIdBloque(valor['bloque']) && (ACCIONES_BLOQUE as readonly unknown[]).includes(valor['accion']);
    case 'anadir-bloque': {
      const pos = valor['pos'];
      return Number.isInteger(pos) && (pos as number) >= 0 && (pos as number) <= MAX_BLOQUES && esTipoBloque(valor['bloque']);
    }
    case 'hoja':
      return esIdBloque(valor['bloque']);
    case 'cerrar-hoja-menu':
      return esMenuHoja(valor['menu']) && esIdHoja(valor['id']);
    case 'hoja-menu':
      return (
        esMenuHoja(valor['menu']) &&
        esIdHoja(valor['id']) &&
        typeof valor['titulo'] === 'string' &&
        valor['titulo'].length <= 120 &&
        Array.isArray(valor['opciones']) &&
        valor['opciones'].length > 0 &&
        valor['opciones'].length <= 12 &&
        valor['opciones'].every(esOpcionHojaMenu)
      );
    case 'pedir-quitar-dato': {
      const clave = valor['clave'];
      const huecos = valor['huecos'];
      return (
        typeof clave === 'string' &&
        esTextoEditable(clave) &&
        Array.isArray(huecos) &&
        huecos.length > 0 &&
        huecos.length <= 8 &&
        huecos.every((n) => typeof n === 'string' && huecoDe(clave, n) !== null) &&
        esTextoCorto(valor['antes']) &&
        esTextoCorto(valor['despues'])
      );
    }
    default:
      return false;
  }
}

/**
 * Escucha los mensajes de `ventana` y entrega solo los que pasan las tres comprobaciones: mismo origen que
 * `ventana`, de la ventana que da `emisor()` en ese momento y con forma válida. Devuelve la función que deja de
 * escuchar.
 */
export function escucharPuente<T>(
  ventana: Window,
  emisor: () => MessageEventSource | null,
  valido: (valor: unknown) => valor is T,
  alRecibir: (mensaje: T) => void,
): () => void {
  const oyente = (evento: MessageEvent) => {
    const esperado = emisor();
    if (esperado === null || evento.source !== esperado || evento.origin !== ventana.origin) {
      return;
    }
    if (valido(evento.data)) {
      alRecibir(evento.data);
    }
  };
  ventana.addEventListener('message', oyente);
  return () => ventana.removeEventListener('message', oyente);
}

/** Envía un mensaje a `destino` restringido al origen de `ventana` (`window.origin`; nunca `'*'`). False si no hay destino. */
export function enviarPorPuente(ventana: Window, destino: Window | null, mensaje: MensajeDelPanel | MensajeDelEditor): boolean {
  if (!destino) {
    return false;
  }
  destino.postMessage(mensaje, ventana.origin);
  return true;
}
