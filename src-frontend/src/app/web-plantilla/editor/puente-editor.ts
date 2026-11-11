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
 * Previsto para W12d (no implementado aquí): edición de textos (`texto` del editor al panel) y el guardado, que se
 * queda en el panel. Cada tipo nuevo se añade a su unión y a su comprobación.
 */

/** Marca de los mensajes de este puente: los de otras librerías o extensiones no la llevan. */
export const CANAL_EDITOR_WEB = 'taemoi-editor-web';

/** Pantallas del panel a las que lleva un candado (d517). El panel traduce cada una a su ruta. */
export const DESTINOS_PANEL = ['sedes', 'grupos', 'eventos', 'productos', 'datos-escuela'] as const;
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

export type MensajeDelPanel = MensajeBorrador;

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

export type MensajeDelEditor = MensajeListo | MensajeAlto | MensajeIr;

// ------------------------------------------------------------------ comprobaciones

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor);
}

export function esDestinoPanel(valor: unknown): valor is DestinoPanel {
  return typeof valor === 'string' && (DESTINOS_PANEL as readonly string[]).includes(valor);
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

/** Lo que el iframe acepta del panel. */
export function esMensajeDelPanel(valor: unknown): valor is MensajeDelPanel {
  if (!esObjeto(valor) || valor['canal'] !== CANAL_EDITOR_WEB) {
    return false;
  }
  return (
    valor['tipo'] === 'borrador' &&
    Array.isArray(valor['bloques']) &&
    valor['bloques'].every(esBloqueBorrador) &&
    (valor['acento'] === undefined || esAcento(valor['acento']))
  );
}

/** Lo que el panel acepta del iframe. */
export function esMensajeDelEditor(valor: unknown): valor is MensajeDelEditor {
  if (!esObjeto(valor) || valor['canal'] !== CANAL_EDITOR_WEB) {
    return false;
  }
  switch (valor['tipo']) {
    case 'listo':
      return true;
    case 'alto':
      return typeof valor['alto'] === 'number' && Number.isFinite(valor['alto']) && valor['alto'] >= 0;
    case 'ir':
      return esDestinoPanel(valor['destino']);
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
